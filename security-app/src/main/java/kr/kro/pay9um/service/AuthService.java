package kr.kro.pay9um.service;

import kr.kro.pay9um.core.common.exception.DuplicateUserException;
import kr.kro.pay9um.core.common.exception.InvalidRefreshTokenException;
import kr.kro.pay9um.core.common.exception.RefreshTokenReplayException;
import kr.kro.pay9um.core.common.util.CustomUID;
import kr.kro.pay9um.core.domain.dto.response.UserResponse;
import kr.kro.pay9um.core.domain.user.entity.User;
import kr.kro.pay9um.core.domain.user.repository.UserRepository;
import kr.kro.pay9um.core.security.CustomUserDetails;
import kr.kro.pay9um.dto.request.SigninRequest;
import kr.kro.pay9um.dto.request.SignupRequest;
import kr.kro.pay9um.dto.request.TokenRefreshRequest;
import kr.kro.pay9um.dto.response.SigninResponse;
import kr.kro.pay9um.jwt.JwtPayload;
import kr.kro.pay9um.jwt.TokenType;
import kr.kro.pay9um.jwt.config.JwtProperties;
import kr.kro.pay9um.jwt.dto.response.TokenResponse;
import kr.kro.pay9um.jwt.provider.JwtTokenProvider;
import kr.kro.pay9um.jwt.validator.JwtTokenValidator;
import kr.kro.pay9um.service.token.RefreshTokenRotationResult;
import kr.kro.pay9um.service.token.RefreshTokenSession;
import kr.kro.pay9um.service.token.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenValidator jwtTokenValidator;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        // 아이디 중복 확인
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUserException("이미 사용 중인 아이디입니다.");
        }

        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateUserException("이미 등록된 이메일입니다.");
        }

        // 고유 UID 생성
        String uid = generateUniqueUid();

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .uid(uid)
                .username(request.getUsername())
                .password(encodedPassword)
                .email(request.getEmail())
                .nickname(request.getNickname())
                .phoneNumber(request.getPhoneNumber())
                .build();

        User savedUser = userRepository.save(user);

        return UserResponse.builder()
                .uid(savedUser.getUid())
                .username(savedUser.getUsername())
                .nickname(savedUser.getNickname())
                .email(savedUser.getEmail())
                .phoneNumber(savedUser.getPhoneNumber())
                .role(savedUser.getRole())
                .build();
    }

    public SigninResponse signin(SigninRequest request) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());

        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.user();

        TokenResponse tokenResponse = issueNewSession(user.getUid());
        UserResponse userResponse = toUserResponse(user);

        return SigninResponse.builder()
                .token(tokenResponse)
                .user(userResponse)
                .build();
    }

    public TokenResponse reissue(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();
        JwtPayload payload = jwtTokenValidator.validate(refreshToken, TokenType.REFRESH)
                .orElseThrow(InvalidRefreshTokenException::new);

        RefreshTokenSession current = toRefreshTokenSession(payload, refreshToken);
        String replacementToken = jwtTokenProvider.generateRefreshToken(
                payload.uid(), payload.sessionId(), payload.expiresAt()
        );
        JwtPayload replacementPayload = jwtTokenValidator.validate(replacementToken, TokenType.REFRESH)
                .orElseThrow(IllegalStateException::new);
        RefreshTokenSession replacement = toRefreshTokenSession(replacementPayload, replacementToken);
        String accessToken = jwtTokenProvider.generateAccessToken(payload.uid(), payload.sessionId());

        RefreshTokenRotationResult rotationResult = refreshTokenStore.rotate(current, replacement);
        if (rotationResult == RefreshTokenRotationResult.REUSED) {
            throw new RefreshTokenReplayException();
        }
        if (rotationResult != RefreshTokenRotationResult.ROTATED) {
            throw new InvalidRefreshTokenException();
        }

        return createTokenResponse(accessToken, replacementToken, payload.expiresAt());
    }

    public void logout(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();
        JwtPayload payload = jwtTokenValidator.validate(refreshToken, TokenType.REFRESH)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!refreshTokenStore.revoke(toRefreshTokenSession(payload, refreshToken))) {
            throw new InvalidRefreshTokenException();
        }
    }

    private TokenResponse issueNewSession(String uid) {
        String sessionId = UUID.randomUUID().toString();
        Instant refreshTokenExpiresAt = clock.instant().plusMillis(jwtProperties.refreshTokenExpiration());
        String refreshToken = jwtTokenProvider.generateRefreshToken(uid, sessionId, refreshTokenExpiresAt);
        JwtPayload refreshPayload = jwtTokenValidator.validate(refreshToken, TokenType.REFRESH)
                .orElseThrow(IllegalStateException::new);
        String accessToken = jwtTokenProvider.generateAccessToken(uid, sessionId);

        refreshTokenStore.save(toRefreshTokenSession(refreshPayload, refreshToken));
        return createTokenResponse(accessToken, refreshToken, refreshTokenExpiresAt);
    }

    private TokenResponse createTokenResponse(
            String accessToken,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
        return TokenResponse.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(jwtProperties.accessTokenExpiration())
                .refreshTokenExpiresIn(Duration.between(clock.instant(), refreshTokenExpiresAt).toMillis())
                .build();
    }

    private RefreshTokenSession toRefreshTokenSession(JwtPayload payload, String refreshToken) {
        return new RefreshTokenSession(
                payload.tokenId(),
                payload.sessionId(),
                hash(refreshToken),
                payload.expiresAt()
        );
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .uid(user.getUid())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .build();
    }

    private String generateUniqueUid() {
        String uid;
        do {
            uid = CustomUID.randomUID();
        } while (userRepository.existsByUid(uid));
        return uid;
    }
}
