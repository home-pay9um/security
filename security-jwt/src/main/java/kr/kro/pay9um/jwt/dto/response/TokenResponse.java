package kr.kro.pay9um.jwt.dto.response;

import lombok.Builder;

@Builder
public record TokenResponse(
        String grantType,
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn
) {
}
