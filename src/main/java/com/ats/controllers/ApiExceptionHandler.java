package com.ats.controllers;

import com.ats.service.ConflictException;
import com.ats.service.LlmException;
import com.ats.service.NotFoundException;
import com.ats.service.UnauthorizedException;
import com.ats.service.ValidationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

}
