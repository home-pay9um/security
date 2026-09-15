package kr.kro.pay9um.core.domain.dto.response;

import kr.kro.pay9um.core.domain.user.entity.Role;
import lombok.Builder;

@Builder
public record UserResponse(
        String uid,
        String username,
        String email,
        String nickname,
        String phoneNumber,
        Role role
) {
}
