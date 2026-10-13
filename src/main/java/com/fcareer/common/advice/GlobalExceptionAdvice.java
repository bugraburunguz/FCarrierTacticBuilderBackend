package com.fcareer.common.advice;

import com.fcareer.common.configuration.RequestBodyLimitFilter;
import com.fcareer.common.exception.FcareerRuntimeException;
import com.fcareer.common.model.ApiError;
import com.fcareer.common.model.ApiResponse;
import com.fcareer.common.service.MessageService;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

import static com.fcareer.common.constants.ErrorCodes.DATA_CONFLICT;
import static com.fcareer.common.constants.ErrorCodes.FORBIDDEN;
import static com.fcareer.common.constants.ErrorCodes.METHOD_NOT_ALLOWED;
import static com.fcareer.common.constants.ErrorCodes.PAYLOAD_TOO_LARGE;
import static com.fcareer.common.constants.ErrorCodes.ROUTE_NOT_FOUND;
import static com.fcareer.common.constants.ErrorCodes.UNSUPPORTED_MEDIA_TYPE;
import static com.fcareer.common.constants.ErrorCodes.UNKNOWN_ERROR;
import static com.fcareer.common.constants.ErrorCodes.VALIDATION_ERROR;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionAdvice {

    private final MessageService messageService;

    @ExceptionHandler(FcareerRuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(FcareerRuntimeException ex) {
        if (ex.isTechnical()) {
            log.error("Technical failure code : {}", ex.getErrorCode(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(buildError(ex.getErrorCode(), ex.getArgs()));
        }
        log.warn("Business error code : {} message : {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.httpStatus()).body(buildError(ex.getErrorCode(), ex.getArgs()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError(VALIDATION_ERROR, fe.getField() + ": " + fe.getDefaultMessage()))
                .toList();
        log.warn("Validation failed field count : {}", errors.size());
        return ResponseEntity.badRequest().body(ApiResponse.fail(messageService.getMessage(VALIDATION_ERROR), errors));
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        if (causedByTooLarge(ex)) {
            return tooLarge();
        }
        log.warn("Bad request : {}", ex.getMessage());
        return ResponseEntity.badRequest().body(buildError(VALIDATION_ERROR));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        if (ex instanceof MaxUploadSizeExceededException || causedByTooLarge(ex)) {
            return tooLarge();
        }
        log.warn("Multipart failure : {}", ex.getMessage());
        return ResponseEntity.badRequest().body(buildError(VALIDATION_ERROR));
    }

    private static boolean causedByTooLarge(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof RequestBodyLimitFilter.RequestTooLargeException) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<ApiResponse<Void>> tooLarge() {
        log.warn("Request body too large");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(buildError(PAYLOAD_TOO_LARGE));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload too large : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(buildError(VALIDATION_ERROR));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildError(FORBIDDEN));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoRoute(NoResourceFoundException ex) {
        log.warn("No route : {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildError(ROUTE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed : {}", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(buildError(METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type : {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(buildError(UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        log.warn("Method validation failed : {}", ex.getMessage());
        return ResponseEntity.badRequest().body(buildError(VALIDATION_ERROR));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation : {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(buildError(DATA_CONFLICT));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument : {}", ex.getMessage());
        return ResponseEntity.badRequest().body(buildError(VALIDATION_ERROR));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildError(UNKNOWN_ERROR));
    }

    private ApiResponse<Void> buildError(String code, Object... args) {
        return ApiResponse.fail(messageService.getMessage(code, args), code);
    }
}
