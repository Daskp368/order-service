package com.example.orderservice.bootstrap;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.dto.auth.RegisterRequest;
import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.repository.UserRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@Component
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminInitializer implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdminInitializer.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final Validator validator;
	private final String adminUsername;
	private final String adminPassword;

	public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, Validator validator,
			@Value("${app.admin.username:}") String adminUsername,
			@Value("${app.admin.password:}") String adminPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.validator = validator;
		this.adminUsername = adminUsername;
		this.adminPassword = adminPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		if (userRepository.existsByRole(Role.ADMIN)) {
			return;
		}

		RegisterRequest credentials = createCredentials();
		validateCredentials(credentials);

		String normalizedUsername = credentials.getUsername().toLowerCase(Locale.ROOT);
		if (userRepository.findByUsername(normalizedUsername).isPresent()) {
			throw new IllegalStateException(
					"Невозможно создать первого администратора: username уже принадлежит другому пользователю");
		}

		String passwordHash = passwordEncoder.encode(credentials.getPassword());
		User admin = userRepository.saveAndFlush(new User(normalizedUsername, passwordHash, Role.ADMIN));
		LOGGER.info("Создан первый администратор с username '{}'", admin.getUsername());
	}

	private RegisterRequest createCredentials() {
		RegisterRequest credentials = new RegisterRequest();
		credentials.setUsername(adminUsername);
		credentials.setPassword(adminPassword);
		return credentials;
	}

	private void validateCredentials(RegisterRequest credentials) {
		Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(credentials);
		if (violations.isEmpty()) {
			return;
		}

		String validationMessage = violations.stream()
				.sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
				.map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
				.collect(Collectors.joining("; "));
		throw new IllegalStateException("Некорректные настройки первого администратора: " + validationMessage);
	}
}
