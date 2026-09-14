package com.example.orderservice.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.repository.UserRepository;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

	private static final String PASSWORD = "strong-password";

	@Mock
	private UserRepository userRepository;

	private ValidatorFactory validatorFactory;
	private Validator validator;
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
		passwordEncoder = new BCryptPasswordEncoder();
	}

	@AfterEach
	void closeValidatorFactory() {
		validatorFactory.close();
	}

	@Test
	void createsAdminWithNormalizedUsernameAndBcryptHash() {
		when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
		when(userRepository.findByUsername("stage11_admin")).thenReturn(Optional.empty());
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		AdminInitializer initializer = new AdminInitializer(
				userRepository, passwordEncoder, validator, "STAGE11_ADMIN", PASSWORD);

		initializer.run(null);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());
		User savedAdmin = userCaptor.getValue();
		assertThat(savedAdmin.getUsername()).isEqualTo("stage11_admin");
		assertThat(savedAdmin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(savedAdmin.getPasswordHash()).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, savedAdmin.getPasswordHash())).isTrue();
	}

	@Test
	void doesNothingWhenAdministratorAlreadyExists() {
		when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);
		AdminInitializer initializer = new AdminInitializer(userRepository, passwordEncoder, validator, "", "");

		initializer.run(null);

		verify(userRepository, never()).findByUsername(any());
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void refusesToReplaceExistingRegularUser() {
		User existingUser = new User("occupied_name", passwordEncoder.encode(PASSWORD), Role.USER);
		when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
		when(userRepository.findByUsername("occupied_name")).thenReturn(Optional.of(existingUser));
		AdminInitializer initializer = new AdminInitializer(
				userRepository, passwordEncoder, validator, "OCCUPIED_NAME", PASSWORD);

		assertThatThrownBy(() -> initializer.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("username уже принадлежит другому пользователю");
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void rejectsInvalidCredentialsWithoutExposingPassword() {
		when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
		String invalidPassword = "short";
		AdminInitializer initializer = new AdminInitializer(
				userRepository, passwordEncoder, validator, "invalid name", invalidPassword);

		assertThatThrownBy(() -> initializer.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Некорректные настройки первого администратора")
				.hasMessageContaining("username")
				.hasMessageContaining("password")
				.hasMessageNotContaining(invalidPassword);
		verify(userRepository, never()).findByUsername(any());
		verify(userRepository, never()).saveAndFlush(any());
	}
}
