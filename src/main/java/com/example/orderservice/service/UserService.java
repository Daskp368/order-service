package com.example.orderservice.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.entity.User;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.exception.SelfDeletionNotAllowedException;
import com.example.orderservice.repository.UserRepository;

@Service
public class UserService {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> getAllUsers() {
		LOGGER.info("Начато получение списка пользователей");

		try {
			List<UserResponse> response = userRepository.findAllByOrderByUsernameAsc()
					.stream()
					.map(this::toResponse)
					.toList();

			LOGGER.info("Получение списка пользователей завершено: usersCount={}", response.size());
			return response;
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка получения списка пользователей", exception);
			throw exception;
		}
	}

	@Transactional
	public void deleteUser(UUID userId, String currentUsername) {
		LOGGER.info("Начато удаление пользователя: userId={}, currentUsername={}", userId, currentUsername);

		try {
			User currentAdmin = userRepository.findByUsername(currentUsername)
					.orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
			User userToDelete = userRepository.findById(userId)
					.orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

			if (currentAdmin.getId().equals(userToDelete.getId())) {
				throw new SelfDeletionNotAllowedException(
						"Администратор не может удалить собственную учётную запись");
			}

			userRepository.delete(userToDelete);
			LOGGER.info("Удаление пользователя завершено: userId={}, deletedUsername={}",
					userId, userToDelete.getUsername());
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка удаления пользователя: userId={}, currentUsername={}",
					userId, currentUsername, exception);
			throw exception;
		}
	}

	private UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getRole());
	}
}
