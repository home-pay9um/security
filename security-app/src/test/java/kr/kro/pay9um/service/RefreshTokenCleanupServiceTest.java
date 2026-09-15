package kr.kro.pay9um.service;

import kr.kro.pay9um.core.domain.token.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenCleanupServiceTest {
    @Test
    void deletesTokensWhoseExpirationHasPassed() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        when(refreshTokenRepository.deleteExpiredTokens(any(Instant.class))).thenReturn(2);
        RefreshTokenCleanupService cleanupService = new RefreshTokenCleanupService(refreshTokenRepository);

        cleanupService.deleteExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredTokens(any(Instant.class));
    }
}
