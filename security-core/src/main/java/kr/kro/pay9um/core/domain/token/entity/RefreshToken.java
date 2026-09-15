package kr.kro.pay9um.core.domain.token.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_token_expires_at", columnList = "expires_at"),
                @Index(name = "idx_refresh_token_uid", columnList = "uid")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
    @Id
    @Column(name = "token_id", nullable = false, updatable = false, length = 36)
    private String tokenId;

    @Column(nullable = false, length = 16)
    private String uid;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    public RefreshToken(String tokenId, String uid, String tokenHash, Instant expiresAt) {
        this.tokenId = tokenId;
        this.uid = uid;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
}
