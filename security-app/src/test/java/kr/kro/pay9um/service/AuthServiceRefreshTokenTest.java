package kr.kro.pay9um.service;

import kr.kro.pay9um.core.common.exception.RefreshTokenReplayException;
import kr.kro.pay9um.core.domain.user.repository.UserRepository;
import kr.kro.pay9um.dto.request.TokenRefreshRequest;
import kr.kro.pay9um.jwt.JwtPayload;
import kr.kro.pay9um.jwt.TokenType;
import kr.kro.pay9um.jwt.config.JwtProperties;
import kr.kro.pay9um.jwt.dto.response.TokenResponse;
import kr.kro.pay9um.jwt.provider.JwtTokenProvider;
import kr.kro.pay9um.jwt.validator.JwtTokenValidator;
import kr.kro.pay9um.service.token.RefreshTokenRotationResult;
import kr.kro.pay9um.service.token.RefreshTokenSession;
import kr.kro.pay9um.service.token.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTokenTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "test-secret-key-must-be-at-least-32-characters-long",
            21_600_000,
            1_209_600_000
    );

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private RefreshTokenStore refreshTokenStore;

    private JwtTokenProvider jwtTokenProvider;
    private JwtTokenValidator jwtTokenValidator;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(JWT_PROPERTIES, CLOCK);
        jwtTokenValidator = new JwtTokenValidator(JWT_PROPERTIES, CLOCK);
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                authenticationManager,
                jwtTokenProvider,
                jwtTokenValidator,
                JWT_PROPERTIES,
                refreshTokenStore,
                CLOCK
        );
    }

    @Test
    void reissueRotatesRefreshTokenWithinTheOriginalSessionExpiry() {
        Instant refreshExpiresAt = CLOCK.instant().plusMillis(JWT_PROPERTIES.refreshTokenExpiration());
        String refreshToken = jwtTokenProvider.generateRefreshToken("user-uid", "session-id", refreshExpiresAt);
        when(refreshTokenStore.rotate(any(), any())).thenReturn(RefreshTokenRotationResult.ROTATED);

        TokenResponse response = authService.reissue(new TokenRefreshRequest(refreshToken));

        ArgumentCaptor<RefreshTokenSession> currentCaptor = ArgumentCaptor.forClass(RefreshTokenSession.class);
        ArgumentCaptor<RefreshTokenSession> replacementCaptor = ArgumentCaptor.forClass(RefreshTokenSession.class);
        verify(refreshTokenStore).rotate(currentCaptor.capture(), replacementCaptor.capture());

        JwtPayload replacementPayload = jwtTokenValidator.validate(response.refreshToken(), TokenType.REFRESH)
                .orElseThrow();
        assertThat(replacementCaptor.getValue().sessionId()).isEqualTo("session-id");
        assertThat(replacementCaptor.getValue().expiresAt()).isEqualTo(refreshExpiresAt);
        assertThat(replacementPayload.sessionId()).isEqualTo("session-id");
        assertThat(replacementPayload.expiresAt()).isEqualTo(refreshExpiresAt);
        assertThat(currentCaptor.getValue().tokenId()).isNotEqualTo(replacementCaptor.getValue().tokenId());
        assertThat(response.accessTokenExpiresIn()).isEqualTo(JWT_PROPERTIES.accessTokenExpiration());
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(JWT_PROPERTIES.refreshTokenExpiration());
    }

    @Test
    void reissueRejectsReplayAfterTheStoreRevokesTheSession() {
        Instant refreshExpiresAt = CLOCK.instant().plusMillis(JWT_PROPERTIES.refreshTokenExpiration());
        String refreshToken = jwtTokenProvider.generateRefreshToken("user-uid", "session-id", refreshExpiresAt);
        when(refreshTokenStore.rotate(any(), any())).thenReturn(RefreshTokenRotationResult.REUSED);

        assertThatThrownBy(() -> authService.reissue(new TokenRefreshRequest(refreshToken)))
                .isInstanceOf(RefreshTokenReplayException.class);
    }

    @Test
    void logoutRevokesTheActiveRefreshToken() {
        Instant refreshExpiresAt = CLOCK.instant().plusMillis(JWT_PROPERTIES.refreshTokenExpiration());
        String refreshToken = jwtTokenProvider.generateRefreshToken("user-uid", "session-id", refreshExpiresAt);
        when(refreshTokenStore.revoke(any())).thenReturn(true);

        authService.logout(new TokenRefreshRequest(refreshToken));

        verify(refreshTokenStore).revoke(any(RefreshTokenSession.class));
    }
}
