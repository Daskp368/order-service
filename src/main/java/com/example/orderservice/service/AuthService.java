package com.example.orderservice.service;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.dto.auth.RegisterRequest;
import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.exception.UsernameAlreadyExistsException;
import com.example.orderservice.repository.UserRepository;

@Service
public class AuthService {

	private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_users_username";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		String normalizedUsername = request.getUsername().toLowerCase(Locale.ROOT);

		if (userRepository.existsByUsername(normalizedUsername)) {
			throw new UsernameAlreadyExistsException();
		}

		String passwordHash = passwordEncoder.encode(request.getPassword());
		User user = new User(normalizedUsername, passwordHash, Role.USER);

		try {
			User savedUser = userRepository.saveAndFlush(user);
			return new UserResponse(savedUser.getId(), savedUser.getUsername(), savedUser.getRole());
		}
		catch (DataIntegrityViolationException exception) {
			if (isUsernameUniqueConstraintViolation(exception)) {
				throw new UsernameAlreadyExistsException(exception);
			}

			throw exception;
		}
	}

	private boolean isUsernameUniqueConstraintViolation(Throwable exception) {
		Throwable currentCause = exception;

		while (currentCause != null) {
			if (currentCause instanceof ConstraintViolationException constraintViolationException) {
				return USERNAME_UNIQUE_CONSTRAINT.equals(constraintViolationException.getConstraintName());
			}

			currentCause = currentCause.getCause();
		}

		return false;
	}
}
