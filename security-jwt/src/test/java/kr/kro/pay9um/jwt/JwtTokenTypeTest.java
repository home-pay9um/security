package kr.kro.pay9um.jwt;

import kr.kro.pay9um.jwt.config.JwtProperties;
import kr.kro.pay9um.jwt.provider.JwtTokenProvider;
import kr.kro.pay9um.jwt.validator.JwtTokenValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenTypeTest {
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "test-secret-key-must-be-at-least-32-characters-long",
            3_600_000,
            86_400_000
    );

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(JWT_PROPERTIES, CLOCK);
    private final JwtTokenValidator tokenValidator = new JwtTokenValidator(JWT_PROPERTIES, CLOCK);

    @Test
    void accessTokenIsNotAcceptedAsRefreshToken() {
        String accessToken = tokenProvider.generateAccessToken("user-uid", "session-id");

        assertThat(tokenValidator.validate(accessToken, TokenType.ACCESS)).isPresent();
        assertThat(tokenValidator.validate(accessToken, TokenType.REFRESH)).isEmpty();
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String refreshToken = tokenProvider.generateRefreshToken(
                "user-uid",
                "session-id",
                CLOCK.instant().plusMillis(JWT_PROPERTIES.refreshTokenExpiration())
        );

        assertThat(tokenValidator.validate(refreshToken, TokenType.REFRESH)).isPresent();
        assertThat(tokenValidator.validate(refreshToken, TokenType.ACCESS)).isEmpty();
    }
}
