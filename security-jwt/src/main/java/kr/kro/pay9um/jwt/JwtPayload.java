package kr.kro.pay9um.jwt;

import java.time.Instant;

/**
 * JWT signature and standard claims have been verified.
 */
public record JwtPayload(
        String uid,
        String tokenId,
        String sessionId,
        TokenType tokenType,
        Instant issuedAt,
        Instant expiresAt
) {
}
