package com.example.orderservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MinCodePointsValidator implements ConstraintValidator<MinCodePoints, String> {

	private int minimumCodePoints;

	@Override
	public void initialize(MinCodePoints constraintAnnotation) {
		minimumCodePoints = constraintAnnotation.value();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		return value.codePointCount(0, value.length()) >= minimumCodePoints;
	}
}
