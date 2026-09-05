package com.example.orderservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UsernameAlreadyExistsException extends RuntimeException {

	public UsernameAlreadyExistsException() {
		super("Username уже занят");
	}

	public UsernameAlreadyExistsException(Throwable cause) {
		super("Username уже занят", cause);
	}
}
