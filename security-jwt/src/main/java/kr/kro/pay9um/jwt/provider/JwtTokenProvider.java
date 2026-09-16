package kr.kro.pay9um.jwt.provider;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kr.kro.pay9um.jwt.TokenType;
import kr.kro.pay9um.jwt.config.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String SESSION_ID_CLAIM = "sid";
    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtTokenProvider(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String uid, String sessionId) {
        Instant expiresAt = clock.instant().plusMillis(jwtProperties.accessTokenExpiration());
        return buildToken(uid, sessionId, TokenType.ACCESS, expiresAt);
    }

    public String generateRefreshToken(String uid, String sessionId, Instant expiresAt) {
        return buildToken(uid, sessionId, TokenType.REFRESH, expiresAt);
    }

    private String buildToken(String uid, String sessionId, TokenType tokenType, Instant expiresAt) {
        Instant issuedAt = clock.instant();
        if (isBlank(uid) || isBlank(sessionId) || !Objects.requireNonNull(expiresAt, "expiresAt must not be null.").isAfter(issuedAt)) {
            throw new IllegalArgumentException("JWT claims must be valid and expiry must be in the future.");
        }
        return Jwts.builder()
                .subject(uid)
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .claim(SESSION_ID_CLAIM, sessionId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
