package com.example.orderservice.security;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private static final int HS256_MINIMUM_KEY_BYTES = 32;

	private final SecretKey signingKey;
	private final long expirationSeconds;

	public JwtService(
			@Value("${jwt.secret}") String encodedSecret,
			@Value("${jwt.expiration-seconds}") long expirationSeconds) {
		this.signingKey = createSigningKey(encodedSecret);
		if (expirationSeconds <= 0) {
			throw new IllegalStateException("jwt.expiration-seconds должен быть положительным числом");
		}
		this.expirationSeconds = expirationSeconds;
	}

	public String generateToken(String username) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

		return Jwts.builder()
				.subject(username)
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey, Jwts.SIG.HS256)
				.compact();
	}

	public String extractUsername(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.sig()
				.clear()
				.add(Jwts.SIG.HS256)
				.and()
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();
	}

	public long getExpirationSeconds() {
		return expirationSeconds;
	}

	private SecretKey createSigningKey(String encodedSecret) {
		if (encodedSecret == null || encodedSecret.isBlank()) {
			throw new IllegalStateException("JWT_SECRET обязателен");
		}

		byte[] keyBytes;
		try {
			keyBytes = Decoders.BASE64.decode(encodedSecret);
		}
		catch (DecodingException exception) {
			throw new IllegalStateException("JWT_SECRET должен быть строкой Base64", exception);
		}

		if (keyBytes.length < HS256_MINIMUM_KEY_BYTES) {
			throw new IllegalStateException("JWT_SECRET после декодирования должен содержать не менее 32 байт");
		}

		return Keys.hmacShaKeyFor(keyBytes);
	}
}
