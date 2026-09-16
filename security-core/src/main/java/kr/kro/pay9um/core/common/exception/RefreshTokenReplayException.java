package kr.kro.pay9um.core.common.exception;

public class RefreshTokenReplayException extends RuntimeException {
    public RefreshTokenReplayException() {
        super("유효하지 않거나 만료된 Refresh Token입니다.");
    }
}
