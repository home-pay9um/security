package kr.kro.pay9um.core.common.exception;

public class RefreshTokenStoreUnavailableException extends RuntimeException {
    public RefreshTokenStoreUnavailableException(Throwable cause) {
        super("Refresh Token 저장소에 연결할 수 없습니다.", cause);
    }
}
