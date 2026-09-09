package com.example.orderservice.dto.auth;

public class LoginResponse {

	private final String token;
	private final String tokenType;
	private final long expiresIn;

	public LoginResponse(String token, String tokenType, long expiresIn) {
		this.token = token;
		this.tokenType = tokenType;
		this.expiresIn = expiresIn;
	}

	public String getToken() {
		return token;
	}

	public String getTokenType() {
		return tokenType;
	}

	public long getExpiresIn() {
		return expiresIn;
	}
}
