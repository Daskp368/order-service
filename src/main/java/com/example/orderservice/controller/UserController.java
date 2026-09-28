package com.example.orderservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderservice.controller.api.UserApi;
import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.service.UserService;

@RestController
@RequestMapping(value = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController implements UserApi {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Override
	@GetMapping
	public ResponseEntity<List<UserResponse>> getAllUsers() {
		return ResponseEntity.ok(userService.getAllUsers());
	}

	@Override
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteUser(
			@PathVariable UUID id,
			Authentication authentication) {
		userService.deleteUser(id, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}
