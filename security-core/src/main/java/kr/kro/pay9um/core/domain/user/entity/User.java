package kr.kro.pay9um.core.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_user_uid", columnList = "uid")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 16)
    private String uid; // 유저 고유 UID

    @Column(nullable = false, unique = true, length = 16)
    private String username; // 로그인 아이디

    @Column(nullable = false)
    private String password; // 암호화 비밀번호

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 16)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role; // USER, ADMIN

    @Builder
    public User(String uid, String username, String password, String email, String nickname, String phoneNumber, Role role) {
        this.uid = uid;
        this.username = username;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.role = role != null ? role : Role.USER;
    }
}
