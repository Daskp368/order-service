package com.example.orderservice.exception;

public class UsernameAlreadyExistsException extends RuntimeException {

	public UsernameAlreadyExistsException() {
		super("Username уже занят");
	}

	public UsernameAlreadyExistsException(Throwable cause) {
		super("Username уже занят", cause);
	}
}
