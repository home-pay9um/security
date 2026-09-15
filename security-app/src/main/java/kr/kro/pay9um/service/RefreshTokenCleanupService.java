package kr.kro.pay9um.service;

import kr.kro.pay9um.core.domain.token.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Scheduled(cron = "${app.security.refresh-token-cleanup-cron:0 0 3 * * *}")
    public void deleteExpiredTokens() {
        int deletedCount = refreshTokenRepository.deleteExpiredTokens(Instant.now());
        if (deletedCount > 0) {
            log.info("Deleted {} expired refresh token(s).", deletedCount);
        }
    }
}
