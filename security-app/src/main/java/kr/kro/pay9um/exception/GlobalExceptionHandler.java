package kr.kro.pay9um.exception;

import kr.kro.pay9um.core.common.exception.DuplicateUserException;
import kr.kro.pay9um.core.common.exception.InvalidRefreshTokenException;
import kr.kro.pay9um.core.common.exception.RefreshTokenReplayException;
import kr.kro.pay9um.core.common.exception.RefreshTokenStoreUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), fieldError.getDefaultMessage());
            } else if (error instanceof ObjectError objectError) {
                errors.put(objectError.getObjectName(), objectError.getDefaultMessage());
            }
        });

        return response(HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다.", errors);
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateUserException(DuplicateUserException ex) {
        return response(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex
    ) {
        return response(HttpStatus.CONFLICT, "이미 존재하거나 데이터베이스 제약 조건을 위반한 요청입니다.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        return response(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다.");
    }

    @ExceptionHandler({InvalidRefreshTokenException.class, RefreshTokenReplayException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidRefreshTokenException(RuntimeException ex) {
        if (ex instanceof RefreshTokenReplayException) {
            log.warn("Refresh token replay detected; the token family was revoked.");
        }
        return response(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(RefreshTokenStoreUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleRefreshTokenStoreUnavailableException(
            RefreshTokenStoreUnavailableException ex
    ) {
        log.error("Refresh token store is unavailable.", ex);
        return response(HttpStatus.SERVICE_UNAVAILABLE, "인증 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return response(status, message, null);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message, Map<String, String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        if (errors != null && !errors.isEmpty()) {
            body.put("errors", errors);
        }
        return ResponseEntity.status(status).body(body);
    }
}
