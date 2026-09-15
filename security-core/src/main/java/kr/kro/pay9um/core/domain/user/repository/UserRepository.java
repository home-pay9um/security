package kr.kro.pay9um.core.domain.user.repository;

import kr.kro.pay9um.core.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 로그인 및 인증 시 username으로 회원 조회
    Optional<User> findByUsername(String username);

    // UUID 식별자로 회원 조회 (외부 API 통신 시 사용)
    Optional<User> findByUid(String uid);

    // 회원가입 시 중복 검증용
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUid(String uid);
}
