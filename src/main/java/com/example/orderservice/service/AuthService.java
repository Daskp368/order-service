package com.example.orderservice.service;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.dto.auth.LoginRequest;
import com.example.orderservice.dto.auth.LoginResponse;
import com.example.orderservice.dto.auth.RegisterRequest;
import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.exception.UsernameAlreadyExistsException;
import com.example.orderservice.repository.UserRepository;
import com.example.orderservice.security.JwtService;

@Service
public class AuthService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

	private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_users_username";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		String requestedUsername = request.getUsername();

		LOGGER.info("Начата регистрация пользователя: username={}", requestedUsername);

		try {
			String normalizedUsername = request.getUsername().toLowerCase(Locale.ROOT);

			if (userRepository.existsByUsername(normalizedUsername)) {
				throw new UsernameAlreadyExistsException();
			}

			String passwordHash = passwordEncoder.encode(request.getPassword());
			User user = new User(normalizedUsername, passwordHash, Role.USER);

			try {
				User savedUser = userRepository.saveAndFlush(user);

				UserResponse response = new UserResponse(
						savedUser.getId(),
						savedUser.getUsername(),
						savedUser.getRole());

				LOGGER.info("Регистрация пользователя завершена: userId={}, username={}, role={}",
						response.getId(), response.getUsername(), response.getRole());

				return response;
			}
			catch (DataIntegrityViolationException exception) {
				if (isUsernameUniqueConstraintViolation(exception)) {
					throw new UsernameAlreadyExistsException(exception);
				}

				throw exception;
			}
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка регистрации пользователя: username={}", requestedUsername, exception);

			throw exception;
		}
	}

	public LoginResponse login(LoginRequest request) {
		String requestedUsername = request.getUsername();
		LOGGER.info("Начата аутентификация пользователя: username={}", requestedUsername);

		try {
			String normalizedUsername = requestedUsername.toLowerCase(Locale.ROOT);
			Authentication authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(normalizedUsername, request.getPassword()));
			String token = jwtService.generateToken(authentication.getName());
			LoginResponse response = new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());

			LOGGER.info("Аутентификация пользователя завершена: username={}, tokenType={}, expiresIn={}",
					authentication.getName(), response.getTokenType(), response.getExpiresIn());
			return response;
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка аутентификации пользователя: username={}", requestedUsername, exception);
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public UserResponse getCurrentUser(String username) {
		LOGGER.info("Начато получение текущего пользователя: username={}", username);

		try {
			User user = userRepository.findByUsername(username)
					.orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
			UserResponse response = new UserResponse(user.getId(), user.getUsername(), user.getRole());

			LOGGER.info("Получение текущего пользователя завершено: userId={}, username={}, role={}",
					response.getId(), response.getUsername(), response.getRole());
			return response;
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка получения текущего пользователя: username={}", username, exception);
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
