package kr.kro.pay9um.service;

import kr.kro.pay9um.core.common.exception.InvalidRefreshTokenException;
import kr.kro.pay9um.core.common.util.CustomUID;
import kr.kro.pay9um.core.domain.dto.response.UserResponse;
import kr.kro.pay9um.core.domain.token.entity.RefreshToken;
import kr.kro.pay9um.core.domain.token.repository.RefreshTokenRepository;
import kr.kro.pay9um.core.domain.user.entity.User;
import kr.kro.pay9um.core.domain.user.repository.UserRepository;
import kr.kro.pay9um.core.security.CustomUserDetails;
import kr.kro.pay9um.dto.request.SigninRequest;
import kr.kro.pay9um.dto.request.SignupRequest;
import kr.kro.pay9um.dto.request.TokenRefreshRequest;
import kr.kro.pay9um.dto.response.SigninResponse;
import kr.kro.pay9um.jwt.TokenType;
import kr.kro.pay9um.jwt.config.JwtProperties;
import kr.kro.pay9um.jwt.dto.response.TokenResponse;
import kr.kro.pay9um.jwt.provider.JwtTokenProvider;
import kr.kro.pay9um.jwt.validator.JwtTokenValidator;
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
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenValidator jwtTokenValidator;
    private final JwtProperties jwtProperties;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        // 아이디 중복 확인
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 등록된 이메일입니다.");
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

    @Transactional
    public SigninResponse signin(SigninRequest request) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());

        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.user();

        TokenResponse tokenResponse = issueTokenPair(user.getUid());

        UserResponse userResponse = UserResponse.builder()
                .uid(user.getUid())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .build();

        return SigninResponse.builder()
                .token(tokenResponse)
                .user(userResponse)
                .build();
    }

    @Transactional
    public TokenResponse reissue(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenValidator.validateToken(refreshToken, TokenType.REFRESH)) {
            throw new InvalidRefreshTokenException();
        }

        String tokenId = jwtTokenProvider.extractTokenId(refreshToken);
        String uid = jwtTokenProvider.extractUid(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenIdForUpdate(tokenId)
                .filter(token -> token.getUid().equals(uid))
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .filter(token -> isTokenMatch(token, refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        refreshTokenRepository.delete(storedToken);
        return issueTokenPair(uid);
    }

    @Transactional
    public void logout(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenValidator.validateToken(refreshToken, TokenType.REFRESH)) {
            throw new InvalidRefreshTokenException();
        }

        String tokenId = jwtTokenProvider.extractTokenId(refreshToken);
        String uid = jwtTokenProvider.extractUid(refreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenIdForUpdate(tokenId)
                .filter(token -> token.getUid().equals(uid))
                .filter(token -> isTokenMatch(token, refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        refreshTokenRepository.delete(storedToken);
    }

    private TokenResponse issueTokenPair(String uid) {
        String accessToken = jwtTokenProvider.generateAccessToken(uid);
        String refreshToken = jwtTokenProvider.generateRefreshToken(uid);
        String tokenId = jwtTokenProvider.extractTokenId(refreshToken);

        refreshTokenRepository.save(new RefreshToken(
                tokenId,
                uid,
                hash(refreshToken),
                Instant.now().plusMillis(jwtProperties.refreshTokenExpiration())
        ));

        return TokenResponse.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(jwtProperties.accessTokenExpiration())
                .build();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }

    private boolean isTokenMatch(RefreshToken storedToken, String refreshToken) {
        return MessageDigest.isEqual(
                storedToken.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(refreshToken).getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String generateUniqueUid() {
        String uid;
        do {
            uid = CustomUID.randomUID();
        } while (userRepository.existsByUid(uid));
        return uid;
    }
}
