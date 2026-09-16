package kr.kro.pay9um.service.token;

import kr.kro.pay9um.core.common.exception.RefreshTokenStoreUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Redis-backed refresh-token rotation (RTR) store.
 *
 * <p>Every active refresh token is stored by {@code jti}; a session key points
 * to the only current token for that session. Consumed token markers remain
 * until the absolute refresh-token expiry so replay can revoke that session.</p>
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final String ACTIVE_TOKEN_KEY_PREFIX = "auth:refresh:token:";
    private static final String USED_TOKEN_KEY_PREFIX = "auth:refresh:used:";
    private static final String SESSION_KEY_PREFIX = "auth:refresh:session:";

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3]); "
                    + "redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3]); "
                    + "return 1;",
            Long.class
    );

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>(
            "local storedHash = redis.call('GET', KEYS[1]); "
                    + "if storedHash then "
                    + "  if storedHash ~= ARGV[1] then return 0; end; "
                    + "  if redis.call('GET', KEYS[3]) ~= ARGV[2] then return 0; end; "
                    + "  redis.call('DEL', KEYS[1]); "
                    + "  redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[6]); "
                    + "  redis.call('SET', KEYS[4], ARGV[5], 'PX', ARGV[6]); "
                    + "  redis.call('SET', KEYS[3], ARGV[4], 'PX', ARGV[6]); "
                    + "  return 1; "
                    + "end; "
                    + "if redis.call('GET', KEYS[2]) == ARGV[3] then "
                    + "  local activeTokenId = redis.call('GET', KEYS[3]); "
                    + "  if activeTokenId then redis.call('DEL', ARGV[7] .. '{' .. ARGV[3] .. '}:' .. activeTokenId); end; "
                    + "  redis.call('DEL', KEYS[3]); "
                    + "  return 2; "
                    + "end; "
                    + "return 0;",
            Long.class
    );

    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0; end; "
                    + "if redis.call('GET', KEYS[2]) ~= ARGV[2] then return 0; end; "
                    + "redis.call('DEL', KEYS[1]); "
                    + "redis.call('DEL', KEYS[2]); "
                    + "return 1;",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Override
    public void save(RefreshTokenSession refreshTokenSession) {
        long ttlMillis = remainingTtlMillis(refreshTokenSession);
        try {
            redisTemplate.execute(
                    SAVE_SCRIPT,
                    List.of(
                            activeTokenKey(refreshTokenSession.sessionId(), refreshTokenSession.tokenId()),
                            sessionKey(refreshTokenSession.sessionId())
                    ),
                    refreshTokenSession.tokenHash(),
                    refreshTokenSession.tokenId(),
                    Long.toString(ttlMillis)
            );
        } catch (DataAccessException e) {
            throw new RefreshTokenStoreUnavailableException(e);
        }
    }

    @Override
    public RefreshTokenRotationResult rotate(RefreshTokenSession current, RefreshTokenSession replacement) {
        if (!current.sessionId().equals(replacement.sessionId())
                || !current.expiresAt().equals(replacement.expiresAt())) {
            throw new IllegalArgumentException("Refresh token rotation must preserve its session and absolute expiry.");
        }
        long ttlMillis = remainingTtlMillis(replacement);
        try {
            Long result = redisTemplate.execute(
                    ROTATE_SCRIPT,
                    List.of(
                            activeTokenKey(current.sessionId(), current.tokenId()),
                            usedTokenKey(current.sessionId(), current.tokenId()),
                            sessionKey(current.sessionId()),
                            activeTokenKey(replacement.sessionId(), replacement.tokenId())
                    ),
                    current.tokenHash(),
                    current.tokenId(),
                    current.sessionId(),
                    replacement.tokenId(),
                    replacement.tokenHash(),
                    Long.toString(ttlMillis),
                    ACTIVE_TOKEN_KEY_PREFIX
            );
            return mapRotationResult(result);
        } catch (DataAccessException e) {
            throw new RefreshTokenStoreUnavailableException(e);
        }
    }

    @Override
    public boolean revoke(RefreshTokenSession refreshTokenSession) {
        try {
            Long result = redisTemplate.execute(
                    REVOKE_SCRIPT,
                    List.of(
                            activeTokenKey(refreshTokenSession.sessionId(), refreshTokenSession.tokenId()),
                            sessionKey(refreshTokenSession.sessionId())
                    ),
                    refreshTokenSession.tokenHash(),
                    refreshTokenSession.tokenId()
            );
            return Long.valueOf(1L).equals(result);
        } catch (DataAccessException e) {
            throw new RefreshTokenStoreUnavailableException(e);
        }
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(sessionId)));
        } catch (DataAccessException e) {
            throw new RefreshTokenStoreUnavailableException(e);
        }
    }

    private long remainingTtlMillis(RefreshTokenSession refreshTokenSession) {
        long ttlMillis = Duration.between(clock.instant(), refreshTokenSession.expiresAt()).toMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("Refresh token expiry must be in the future.");
        }
        return ttlMillis;
    }

    private RefreshTokenRotationResult mapRotationResult(Long result) {
        if (Long.valueOf(1L).equals(result)) {
            return RefreshTokenRotationResult.ROTATED;
        }
        if (Long.valueOf(2L).equals(result)) {
            return RefreshTokenRotationResult.REUSED;
        }
        return RefreshTokenRotationResult.INVALID;
    }

    private String activeTokenKey(String sessionId, String tokenId) {
        return ACTIVE_TOKEN_KEY_PREFIX + hashTag(sessionId) + ':' + tokenId;
    }

    private String usedTokenKey(String sessionId, String tokenId) {
        return USED_TOKEN_KEY_PREFIX + hashTag(sessionId) + ':' + tokenId;
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + hashTag(sessionId);
    }

    private String hashTag(String sessionId) {
        return '{' + sessionId + '}';
    }
}
