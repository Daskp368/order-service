package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.exception.SelfDeletionNotAllowedException;
import com.example.orderservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserService userService;

	@Test
	void getAllUsersMapsRepositoryResultToSafeResponses() {
		User admin = user(UUID.randomUUID(), "admin", Role.ADMIN);
		User regularUser = user(UUID.randomUUID(), "user", Role.USER);
		when(userRepository.findAllByOrderByUsernameAsc()).thenReturn(List.of(admin, regularUser));

		List<UserResponse> response = userService.getAllUsers();

		assertThat(response).extracting(UserResponse::getUsername).containsExactly("admin", "user");
		assertThat(response).extracting(UserResponse::getRole).containsExactly(Role.ADMIN, Role.USER);
		assertThat(response).extracting(UserResponse::getId)
				.containsExactly(admin.getId(), regularUser.getId());
	}

	@Test
	void deleteUserDeletesAnotherUser() {
		User currentAdmin = user(UUID.randomUUID(), "current_admin", Role.ADMIN);
		User userToDelete = user(UUID.randomUUID(), "deleted_user", Role.USER);
		when(userRepository.findByUsername("current_admin")).thenReturn(Optional.of(currentAdmin));
		when(userRepository.findById(userToDelete.getId())).thenReturn(Optional.of(userToDelete));

		userService.deleteUser(userToDelete.getId(), "current_admin");

		verify(userRepository).delete(userToDelete);
	}

	@Test
	void deleteUserRejectsUnknownCurrentAdministrator() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findByUsername("missing_admin")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.deleteUser(userId, "missing_admin"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Пользователь не найден");

		verify(userRepository, never()).findById(any(UUID.class));
		verify(userRepository, never()).delete(any(User.class));
	}

	@Test
	void deleteUserRejectsUnknownTargetUser() {
		User currentAdmin = user(UUID.randomUUID(), "current_admin", Role.ADMIN);
		UUID missingUserId = UUID.randomUUID();
		when(userRepository.findByUsername("current_admin")).thenReturn(Optional.of(currentAdmin));
		when(userRepository.findById(missingUserId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.deleteUser(missingUserId, "current_admin"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Пользователь не найден");

		verify(userRepository, never()).delete(any(User.class));
	}

	@Test
	void deleteUserForbidsAdministratorSelfDeletion() {
		UUID adminId = UUID.randomUUID();
		User currentAdmin = user(adminId, "current_admin", Role.ADMIN);
		when(userRepository.findByUsername("current_admin")).thenReturn(Optional.of(currentAdmin));
		when(userRepository.findById(adminId)).thenReturn(Optional.of(currentAdmin));

		assertThatThrownBy(() -> userService.deleteUser(adminId, "current_admin"))
				.isInstanceOf(SelfDeletionNotAllowedException.class)
				.hasMessage("Администратор не может удалить собственную учётную запись");

		verify(userRepository, never()).delete(any(User.class));
	}

	private User user(UUID id, String username, Role role) {
		User user = new User(username, "password-hash", role);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
