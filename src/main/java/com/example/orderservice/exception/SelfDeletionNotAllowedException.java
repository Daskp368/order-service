package com.example.orderservice.exception;

public class SelfDeletionNotAllowedException extends RuntimeException {

	public SelfDeletionNotAllowedException(String message) {
		super(message);
	}
}
