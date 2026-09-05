package com.example.orderservice.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AuthRegistrationIntegrationTest {

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final EntityManager entityManager;

	@Autowired
	AuthRegistrationIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository userRepository,
			PasswordEncoder passwordEncoder, EntityManager entityManager) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.entityManager = entityManager;
	}

	@Test
	void registersUserWithNormalizedUsernameAndHashedPassword() throws Exception {
		String normalizedUsername = uniqueUsername("stage6_user");
		String requestedUsername = normalizedUsername.toUpperCase(Locale.ROOT);
		String rawPassword = "я".repeat(36);

		MvcResult result = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationJson(requestedUsername, rawPassword)))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").isString())
				.andExpect(jsonPath("$.username").value(normalizedUsername))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn();

		JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
		UUID responseId = UUID.fromString(responseBody.get("id").asText());
		assertThat(responseBody.size()).isEqualTo(3);

		entityManager.flush();
		entityManager.clear();

		User storedUser = userRepository.findByUsername(normalizedUsername).orElseThrow();
		assertThat(storedUser.getId()).isEqualTo(responseId);
		assertThat(storedUser.getRole()).isEqualTo(Role.USER);
		assertThat(storedUser.getPasswordHash()).hasSize(60);
		assertThat(storedUser.getPasswordHash()).isNotEqualTo(rawPassword);
		assertThat(passwordEncoder.matches(rawPassword, storedUser.getPasswordHash())).isTrue();
	}

	@Test
	void alwaysAssignsUserRoleWhenClientSendsAdminRole() throws Exception {
		String username = uniqueUsername("role_attack");

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationJsonWithRole(username, "12345678", "ADMIN")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("USER"));

		entityManager.flush();
		entityManager.clear();

		User storedUser = userRepository.findByUsername(username).orElseThrow();
		assertThat(storedUser.getRole()).isEqualTo(Role.USER);
	}

	@Test
	void returnsConflictAndDoesNotOverwriteExistingUser() throws Exception {
		String username = uniqueUsername("duplicate");
		String firstPassword = "first-password";
		String secondPassword = "second-password";

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationJson(username, firstPassword)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationJson(username.toUpperCase(Locale.ROOT), secondPassword)))
				.andExpect(status().isConflict());

		entityManager.clear();
		User storedUser = userRepository.findByUsername(username).orElseThrow();
		assertThat(passwordEncoder.matches(firstPassword, storedUser.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches(secondPassword, storedUser.getPasswordHash())).isFalse();
	}

	@ParameterizedTest
	@MethodSource("invalidRegistrations")
	void rejectsInvalidRegistrationData(String username, String password) throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationJson(username, password)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsPasswordLongerThanSeventyTwoUtf8Bytes() throws Exception {
		String username = uniqueUsername("utf8_limit");
		String seventyFourBytePassword = "я".repeat(37);

		MvcResult result = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationJson(username, seventyFourBytePassword)))
				.andExpect(status().isBadRequest())
				.andReturn();

		assertThat(userRepository.findByUsername(username)).isEmpty();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(seventyFourBytePassword);
	}

	@Test
	void permitsInternalErrorDispatchWithoutReplacingItWithForbidden() throws Exception {
		mockMvc.perform(get("/error")
				.with(request -> {
					request.setDispatcherType(DispatcherType.ERROR);
					return request;
				})
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
				.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/auth/register"))
				.andExpect(status().isInternalServerError());
	}

	private String registrationJson(String username, String password) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"username", username,
				"password", password));
	}

	private String registrationJsonWithRole(String username, String password, String role) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"username", username,
				"password", password,
				"role", role));
	}

	private String uniqueUsername(String prefix) {
		return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
	}

	private static Stream<Arguments> invalidRegistrations() {
		return Stream.of(
				Arguments.of("ab", "valid-password"),
				Arguments.of("invalid user", "valid-password"),
				Arguments.of("valid_user", "1234567"),
				Arguments.of("valid_user", "        "),
				Arguments.of("valid_user", "😀".repeat(4)));
	}
}
