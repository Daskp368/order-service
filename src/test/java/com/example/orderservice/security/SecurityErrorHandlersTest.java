package com.example.orderservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SecurityErrorHandlersTest {

	private ObjectMapper objectMapper;
	private RestAuthenticationEntryPoint authenticationEntryPoint;
	private RestAccessDeniedHandler accessDeniedHandler;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper().findAndRegisterModules();
		SecurityErrorResponseWriter responseWriter = new SecurityErrorResponseWriter(objectMapper);
		authenticationEntryPoint = new RestAuthenticationEntryPoint(responseWriter);
		accessDeniedHandler = new RestAccessDeniedHandler(responseWriter);
	}

	@Test
	void authenticationEntryPointWritesUnifiedUnauthorizedResponse() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
		MockHttpServletResponse response = new MockHttpServletResponse();

		authenticationEntryPoint.commence(request, response, new BadCredentialsException("sensitive"));

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(body.get("status").asInt()).isEqualTo(401);
		assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
		assertThat(body.get("message").asText()).isEqualTo("Требуется аутентификация");
		assertThat(body.get("path").asText()).isEqualTo("/api/orders");
		assertThat(response.getContentAsString()).doesNotContain("sensitive");
	}

	@Test
	void accessDeniedHandlerWritesUnifiedForbiddenResponse() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/all");
		MockHttpServletResponse response = new MockHttpServletResponse();

		accessDeniedHandler.handle(request, response, new AccessDeniedException("sensitive"));

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(body.get("status").asInt()).isEqualTo(403);
		assertThat(body.get("error").asText()).isEqualTo("Forbidden");
		assertThat(body.get("message").asText()).isEqualTo("Доступ запрещён");
		assertThat(body.get("path").asText()).isEqualTo("/api/orders/all");
		assertThat(response.getContentAsString()).doesNotContain("sensitive");
	}
}
