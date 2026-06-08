package com.eat.common;

import com.eat.common.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBadRequest(IllegalArgumentException e) {
        return R.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return R.fail(ResultCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler(JwtUtil.JwtExpiredException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleJwtExpired(JwtUtil.JwtExpiredException e) {
        return R.fail(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
    }

    @ExceptionHandler(JwtUtil.JwtInvalidException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleJwtInvalid(JwtUtil.JwtInvalidException e) {
        return R.fail(ResultCode.UNAUTHORIZED, "Token无效");
    }

    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public R<Void> handleRateLimit(RateLimitException e) {
        return R.fail(ResultCode.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(SensitiveWordException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public R<Void> handleSensitive(SensitiveWordException e) {
        return R.fail(ResultCode.UNPROCESSABLE_ENTITY, "请提出健康的饮食问题");
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleRuntime(RuntimeException e) {
        log.error("Unhandled runtime exception", e);
        return R.fail(ResultCode.INTERNAL_ERROR, "系统异常，请稍后再试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return R.fail(ResultCode.INTERNAL_ERROR, "系统异常，请稍后再试");
    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String msg) { super(msg); }
    }

    public static class SensitiveWordException extends RuntimeException {
        public SensitiveWordException() { super("敏感词拦截"); }
    }
}
