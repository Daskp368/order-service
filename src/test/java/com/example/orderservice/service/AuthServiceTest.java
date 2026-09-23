package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private JwtService jwtService;

	@InjectMocks
	private AuthService authService;

	@Test
	void registerNormalizesUsernameHashesPasswordAndAssignsUserRole() {
		RegisterRequest request = registerRequest("New_User", "strong-password");
		UUID userId = UUID.randomUUID();
		when(userRepository.existsByUsername("new_user")).thenReturn(false);
		when(passwordEncoder.encode("strong-password")).thenReturn("bcrypt-hash");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			ReflectionTestUtils.setField(user, "id", userId);
			return user;
		});

		UserResponse response = authService.register(request);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());
		User savedUser = userCaptor.getValue();
		assertThat(savedUser.getUsername()).isEqualTo("new_user");
		assertThat(savedUser.getPasswordHash()).isEqualTo("bcrypt-hash");
		assertThat(savedUser.getRole()).isEqualTo(Role.USER);
		assertThat(response.getId()).isEqualTo(userId);
		assertThat(response.getUsername()).isEqualTo("new_user");
		assertThat(response.getRole()).isEqualTo(Role.USER);
	}

	@Test
	void registerRejectsUsernameThatAlreadyExists() {
		RegisterRequest request = registerRequest("Existing_User", "strong-password");
		when(userRepository.existsByUsername("existing_user")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(request))
				.isInstanceOf(UsernameAlreadyExistsException.class)
				.hasMessage("Username уже занят");

		verify(userRepository, never()).saveAndFlush(any(User.class));
		verifyNoInteractions(passwordEncoder, authenticationManager, jwtService);
	}

	@Test
	void registerTranslatesDatabaseUsernameConstraintViolation() {
		RegisterRequest request = registerRequest("Concurrent_User", "strong-password");
		DataIntegrityViolationException databaseException = dataIntegrityViolation("uk_users_username");
		when(userRepository.existsByUsername("concurrent_user")).thenReturn(false);
		when(passwordEncoder.encode("strong-password")).thenReturn("bcrypt-hash");
		when(userRepository.saveAndFlush(any(User.class))).thenThrow(databaseException);

		assertThatThrownBy(() -> authService.register(request))
				.isInstanceOf(UsernameAlreadyExistsException.class)
				.hasMessage("Username уже занят")
				.hasCause(databaseException);
	}

	@Test
	void registerDoesNotHideUnrelatedDatabaseViolation() {
		RegisterRequest request = registerRequest("Valid_User", "strong-password");
		DataIntegrityViolationException databaseException = dataIntegrityViolation("another_constraint");
		when(userRepository.existsByUsername("valid_user")).thenReturn(false);
		when(passwordEncoder.encode("strong-password")).thenReturn("bcrypt-hash");
		when(userRepository.saveAndFlush(any(User.class))).thenThrow(databaseException);

		assertThatThrownBy(() -> authService.register(request)).isSameAs(databaseException);
	}

	@Test
	void loginAuthenticatesNormalizedUsernameAndReturnsJwtMetadata() {
		LoginRequest request = loginRequest("Student_User", "strong-password");
		Authentication authenticated = org.mockito.Mockito.mock(Authentication.class);
		when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
		when(authenticated.getName()).thenReturn("student_user");
		when(jwtService.generateToken("student_user")).thenReturn("signed-jwt");
		when(jwtService.getExpirationSeconds()).thenReturn(1800L);

		LoginResponse response = authService.login(request);

		ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
		verify(authenticationManager).authenticate(authenticationCaptor.capture());
		Authentication submittedAuthentication = authenticationCaptor.getValue();
		assertThat(submittedAuthentication.getPrincipal()).isEqualTo("student_user");
		assertThat(submittedAuthentication.getCredentials()).isEqualTo("strong-password");
		assertThat(response.getToken()).isEqualTo("signed-jwt");
		assertThat(response.getTokenType()).isEqualTo("Bearer");
		assertThat(response.getExpiresIn()).isEqualTo(1800L);
	}

	@Test
	void getCurrentUserReturnsSafeResponse() {
		UUID userId = UUID.randomUUID();
		User user = user(userId, "current_user", Role.ADMIN);
		when(userRepository.findByUsername("current_user")).thenReturn(Optional.of(user));

		UserResponse response = authService.getCurrentUser("current_user");

		assertThat(response.getId()).isEqualTo(userId);
		assertThat(response.getUsername()).isEqualTo("current_user");
		assertThat(response.getRole()).isEqualTo(Role.ADMIN);
	}

	@Test
	void getCurrentUserRejectsUnknownUsername() {
		when(userRepository.findByUsername("missing_user")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.getCurrentUser("missing_user"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Пользователь не найден");
	}

	private RegisterRequest registerRequest(String username, String password) {
		RegisterRequest request = new RegisterRequest();
		request.setUsername(username);
		request.setPassword(password);
		return request;
	}

	private LoginRequest loginRequest(String username, String password) {
		LoginRequest request = new LoginRequest();
		request.setUsername(username);
		request.setPassword(password);
		return request;
	}

	private User user(UUID id, String username, Role role) {
		User user = new User(username, "password-hash", role);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private DataIntegrityViolationException dataIntegrityViolation(String constraintName) {
		ConstraintViolationException cause = new ConstraintViolationException(
				"constraint violation", new SQLException("duplicate"), constraintName);
		return new DataIntegrityViolationException("database constraint violation", cause);
	}
}
