package com.example.orderservice.exception;

public class InvalidPaginationException extends RuntimeException {

	public InvalidPaginationException(String message) {
		super(message);
	}
}
