package com.example.orderservice.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;

	public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			String token = extractBearerToken(request);
			if (token != null) {
				authenticateFromToken(request, token);
			}
		}

		filterChain.doFilter(request, response);
	}

	private String extractBearerToken(HttpServletRequest request) {
		String authorization = request.getHeader(AUTHORIZATION_HEADER);
		if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0,
				BEARER_PREFIX.length())) {
			return null;
		}

		String token = authorization.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}

	private void authenticateFromToken(HttpServletRequest request, String token) {
		try {
			String username = jwtService.extractUsername(token);
			if (username == null || username.isBlank()) {
				return;
			}

			UserDetails userDetails = userDetailsService.loadUserByUsername(username);
			UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
					userDetails,
					null,
					userDetails.getAuthorities());
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

			SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
			securityContext.setAuthentication(authentication);
			SecurityContextHolder.setContext(securityContext);
		}
		catch (JwtException | AuthenticationException | IllegalArgumentException exception) {
			SecurityContextHolder.clearContext();
		}
	}
}
