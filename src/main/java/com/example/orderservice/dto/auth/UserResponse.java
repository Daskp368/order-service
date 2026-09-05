package com.example.orderservice.dto.auth;

import java.util.UUID;

import com.example.orderservice.entity.Role;

public class UserResponse {

	private final UUID id;
	private final String username;
	private final Role role;

	public UserResponse(UUID id, String username, Role role) {
		this.id = id;
		this.username = username;
		this.role = role;
	}

	public UUID getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public Role getRole() {
		return role;
	}
}
