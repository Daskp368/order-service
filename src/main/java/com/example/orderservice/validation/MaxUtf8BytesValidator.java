package com.example.orderservice.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

	private int maximumBytes;

	@Override
	public void initialize(MaxUtf8Bytes constraintAnnotation) {
		maximumBytes = constraintAnnotation.value();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		return value.getBytes(StandardCharsets.UTF_8).length <= maximumBytes;
	}
}
