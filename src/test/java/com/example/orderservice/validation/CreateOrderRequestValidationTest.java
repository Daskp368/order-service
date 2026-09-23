package com.example.orderservice.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.orderservice.dto.order.CreateOrderRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateOrderRequestValidationTest {

	@Test
	void acceptsOneThousandUnicodeCharactersAfterTrimming() {
		Set<ConstraintViolation<CreateOrderRequest>> violations = validate("  " + "😀".repeat(1000) + "  ");

		assertThat(violations).isEmpty();
	}

	@Test
	void rejectsBlankDescription() {
		Set<ConstraintViolation<CreateOrderRequest>> violations = validate("    ");

		assertThat(violations)
				.extracting(ConstraintViolation::getMessage)
				.contains("Описание заказа обязательно");
	}

	@Test
	void rejectsDescriptionLongerThanOneThousandUnicodeCharactersAfterTrimming() {
		Set<ConstraintViolation<CreateOrderRequest>> violations = validate("  " + "я".repeat(1001) + "  ");

		assertThat(violations)
				.extracting(ConstraintViolation::getMessage)
				.contains("Описание заказа после удаления пробелов по краям должно содержать от 1 до 1000 символов");
	}

	private Set<ConstraintViolation<CreateOrderRequest>> validate(String description) {
		CreateOrderRequest request = new CreateOrderRequest();
		request.setDescription(description);

		try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = validatorFactory.getValidator();
			return validator.validate(request);
		}
	}
}
