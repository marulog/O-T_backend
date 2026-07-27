package com.ott.common.web.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = ErrorHttpStatusMapper.toHttpStatus(errorCode);
        if (status.is4xxClientError()) {
            log.warn("BusinessException: {}", ex.getMessage());
        } else {
            log.error("BusinessException: {}", ex.getMessage(), ex);
        }
        ErrorResponse response = ErrorResponse.of(errorCode, errorCode.getMessage());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.warn("MethodArgumentNotValidException: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT, ex.getBindingResult());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("MissingServletRequestParameterException: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(ErrorCode.MISSING_PARAMETER, ErrorCode.MISSING_PARAMETER.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("MethodArgumentTypeMismatchException: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_TYPE, ErrorCode.INVALID_TYPE.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());
        Throwable cause = ex.getCause();
        ErrorCode errorCode = (cause instanceof JsonProcessingException)
                ? ErrorCode.JSON_PARSE_ERROR
                : ErrorCode.MISSING_BODY;
        ErrorResponse response = ErrorResponse.of(errorCode, errorCode.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNoHandlerFoundException(NoHandlerFoundException ex) {
        log.warn("NoHandlerFoundException: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        return ResponseEntity.status(ErrorHttpStatusMapper.toHttpStatus(ErrorCode.RESOURCE_NOT_FOUND)).body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.warn("HttpRequestMethodNotSupportedException: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
        return ResponseEntity.status(ErrorHttpStatusMapper.toHttpStatus(ErrorCode.METHOD_NOT_ALLOWED)).body(response);
    }

    // [Exception] 메소드에 전달된 인수가 유효하지 않은 경우
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("handleIllegalArgumentException", ex);
        final ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unhandled Exception: {}", ex.getMessage(), ex);
        ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
        return ResponseEntity.status(ErrorHttpStatusMapper.toHttpStatus(ErrorCode.INTERNAL_ERROR)).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("ConstraintViolationException: {}", ex.getMessage());
        // C001 에러 코드를 사용하여 400 Bad Request 응답 생성
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
}
