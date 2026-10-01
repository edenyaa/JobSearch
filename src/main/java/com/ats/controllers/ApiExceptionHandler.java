package com.ats.controllers;

import com.ats.service.ConflictException;
import com.ats.service.LlmException;
import com.ats.service.NotFoundException;
import com.ats.service.UnauthorizedException;
import com.ats.service.ValidationException;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler({ NotFoundException.class, ConflictException.class, ValidationException.class,
			UnauthorizedException.class, LlmException.class })
	public ResponseEntity<Map<String, Object>> handle(RuntimeException e) {
		HttpStatus status = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class).code();
		if (e instanceof LlmException) {
			log.warn("AI call failed: {}", e.getMessage());
		}
		return ResponseEntity.status(status).body(Map.of(
				"status", status.value(),
				"error", status.getReasonPhrase(),
				"message", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
		String details = e.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + " " + f.getDefaultMessage()).collect(Collectors.joining("; "));
		return body(HttpStatus.BAD_REQUEST, details.isBlank() ? "Some fields are not valid." : "Invalid data: " + details + ".");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
		return body(HttpStatus.BAD_REQUEST, "The request body is missing or is not valid JSON.");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
		return body(HttpStatus.CONTENT_TOO_LARGE, "The file is too large. The limit is 10 MB.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
		log.warn("Data integrity violation", e);
		return body(HttpStatus.CONFLICT, "The data conflicts with existing records or is missing a required value.");
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
		if (e instanceof ErrorResponse response) {
			HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
			String detail = response.getBody().getDetail();
			return body(status, detail == null || detail.isBlank() ? status.getReasonPhrase() : detail);
		}
		log.error("Unexpected error", e);
		Throwable root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String reason = root.getMessage() == null ? "no message" : root.getMessage();
		if (reason.length() > 300) {
			reason = reason.substring(0, 300) + "...";
		}
		return body(HttpStatus.INTERNAL_SERVER_ERROR,
				"Unexpected server error (" + root.getClass().getSimpleName() + "): " + reason);
	}

	private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of(
				"status", status.value(),
				"error", status.getReasonPhrase(),
				"message", message));
	}

}
