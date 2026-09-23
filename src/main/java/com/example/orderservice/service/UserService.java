package com.example.orderservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.entity.User;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.exception.SelfDeletionNotAllowedException;
import com.example.orderservice.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> getAllUsers() {
		return userRepository.findAllByOrderByUsernameAsc()
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public void deleteUser(UUID userId, String currentUsername) {
		User currentAdmin = userRepository.findByUsername(currentUsername)
				.orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
		User userToDelete = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

		if (currentAdmin.getId().equals(userToDelete.getId())) {
			throw new SelfDeletionNotAllowedException(
					"Администратор не может удалить собственную учётную запись");
		}

		userRepository.delete(userToDelete);
	}

	private UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getRole());
	}
}
