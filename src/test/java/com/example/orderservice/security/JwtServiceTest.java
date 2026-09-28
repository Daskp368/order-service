package com.example.orderservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

	private static final String VALID_SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

	@Test
	void generatesTokenWithUsernameAsSubject() {
		JwtService jwtService = new JwtService(VALID_SECRET, 1800);

		String token = jwtService.generateToken("test_user");

		assertThat(jwtService.extractUsername(token)).isEqualTo("test_user");
		assertThat(jwtService.getExpirationSeconds()).isEqualTo(1800);
	}

	@Test
	void rejectsSecretShorterThanThirtyTwoBytes() {
		assertThatThrownBy(() -> new JwtService("c2hvcnQ=", 1800))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("не менее 32 байт");
	}

	@Test
	void rejectsNonPositiveExpiration() {
		assertThatThrownBy(() -> new JwtService(VALID_SECRET, 0))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("положительным числом");
	}

	@Test
	void rejectsExpiredToken() {
		JwtService jwtService = new JwtService(VALID_SECRET, 1800);
		Instant now = Instant.now();
		String token = Jwts.builder()
				.subject("expired_user")
				.issuedAt(Date.from(now.minusSeconds(1_801)))
				.expiration(Date.from(now.minusSeconds(1)))
				.signWith(testSigningKey(), Jwts.SIG.HS256)
				.compact();

		assertThatThrownBy(() -> jwtService.extractUsername(token))
				.isInstanceOf(ExpiredJwtException.class);
	}

	private SecretKey testSigningKey() {
		return Keys.hmacShaKeyFor(Decoders.BASE64.decode(VALID_SECRET));
	}
}
