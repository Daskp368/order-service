package com.example.orderservice.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.entity.User;
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

	private UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getRole());
	}
}
