package com.ats.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class LlmException extends RuntimeException {

	public LlmException(String message) {
		super(message);
	}

}
