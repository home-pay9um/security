package kr.kro.pay9um.jwt;

import kr.kro.pay9um.jwt.config.JwtProperties;
import kr.kro.pay9um.jwt.provider.JwtTokenProvider;
import kr.kro.pay9um.jwt.validator.JwtTokenValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenTypeTest {
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "test-secret-key-must-be-at-least-32-characters-long",
            3_600_000,
            86_400_000
    );

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(JWT_PROPERTIES);
    private final JwtTokenValidator tokenValidator = new JwtTokenValidator(JWT_PROPERTIES);

    @Test
    void accessTokenIsNotAcceptedAsRefreshToken() {
        String accessToken = tokenProvider.generateAccessToken("user-uid");

        assertThat(tokenValidator.validateToken(accessToken, TokenType.ACCESS)).isTrue();
        assertThat(tokenValidator.validateToken(accessToken, TokenType.REFRESH)).isFalse();
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String refreshToken = tokenProvider.generateRefreshToken("user-uid");

        assertThat(tokenValidator.validateToken(refreshToken, TokenType.REFRESH)).isTrue();
        assertThat(tokenValidator.validateToken(refreshToken, TokenType.ACCESS)).isFalse();
    }
}
