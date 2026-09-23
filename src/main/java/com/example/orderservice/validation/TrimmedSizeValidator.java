package com.example.orderservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TrimmedSizeValidator implements ConstraintValidator<TrimmedSize, String> {

	private int minimum;
	private int maximum;

	@Override
	public void initialize(TrimmedSize constraintAnnotation) {
		minimum = constraintAnnotation.min();
		maximum = constraintAnnotation.max();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		String trimmedValue = value.strip();
		int codePointCount = trimmedValue.codePointCount(0, trimmedValue.length());
		return codePointCount >= minimum && codePointCount <= maximum;
	}
}
