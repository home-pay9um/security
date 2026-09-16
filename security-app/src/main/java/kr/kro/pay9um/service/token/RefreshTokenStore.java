package kr.kro.pay9um.service.token;

/**
 * Server-side refresh token state. Implementations must make rotation atomic.
 */
public interface RefreshTokenStore {
    void save(RefreshTokenSession refreshTokenSession);

    RefreshTokenRotationResult rotate(RefreshTokenSession current, RefreshTokenSession replacement);

    boolean revoke(RefreshTokenSession refreshTokenSession);

    /**
     * Indicates that the session has not been logged out or revoked after a refresh-token replay.
     */
    boolean isSessionActive(String sessionId);
}
