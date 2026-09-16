package kr.kro.pay9um.service.token;

import java.time.Instant;
import java.util.Objects;

/**
 * A verified refresh token and the server-side state needed to rotate it.
 */
public record RefreshTokenSession(
        String tokenId,
        String sessionId,
        String tokenHash,
        Instant expiresAt
) {
    public RefreshTokenSession {
        if (isBlank(tokenId) || isBlank(sessionId) || isBlank(tokenHash)) {
            throw new IllegalArgumentException("Refresh token session fields must not be blank.");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
