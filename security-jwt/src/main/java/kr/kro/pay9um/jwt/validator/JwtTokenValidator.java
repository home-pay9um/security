package kr.kro.pay9um.jwt.validator;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kr.kro.pay9um.jwt.TokenType;
import kr.kro.pay9um.jwt.JwtPayload;
import kr.kro.pay9um.jwt.config.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenValidator {
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String SESSION_ID_CLAIM = "sid";
    private final SecretKey secretKey;
    private final Clock clock;

    public JwtTokenValidator(JwtProperties jwtProperties, Clock clock) {
        this.clock = clock;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
    }

    public Optional<JwtPayload> validate(String token, TokenType expectedTokenType) {
        try {
            Claims claims = Jwts.parser()
                    .clock(() -> Date.from(clock.instant()))
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tokenTypeClaim = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (isBlank(tokenTypeClaim)) {
                return Optional.empty();
            }
            TokenType tokenType = TokenType.valueOf(tokenTypeClaim);
            if (tokenType != expectedTokenType) {
                return Optional.empty();
            }

            String uid = claims.getSubject();
            String tokenId = claims.getId();
            String sessionId = claims.get(SESSION_ID_CLAIM, String.class);
            if (isBlank(uid) || isBlank(tokenId) || isBlank(sessionId)
                    || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }

            return Optional.of(new JwtPayload(
                    uid,
                    tokenId,
                    sessionId,
                    tokenType,
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant()
            ));
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT token: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
