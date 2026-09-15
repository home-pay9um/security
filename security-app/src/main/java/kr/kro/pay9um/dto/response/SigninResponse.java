package kr.kro.pay9um.dto.response;

import kr.kro.pay9um.core.domain.dto.response.UserResponse;
import kr.kro.pay9um.jwt.dto.response.TokenResponse;
import lombok.Builder;

@Builder
public record SigninResponse(
        TokenResponse token,
        UserResponse user
) {
}
