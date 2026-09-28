package com.example.orderservice.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.repository.UserRepository;
import com.example.orderservice.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@ActiveProfiles("test")
class AuthMeIntegrationTest {

	private static final String PASSWORD = "strong-password";

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Autowired
	AuthMeIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository userRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Test
	void tokensOfDifferentUsersReturnTheirOwnSafeProfiles() throws Exception {
		User firstUser = createUser("stage9_user", Role.USER);
		User secondUser = createUser("stage9_admin", Role.ADMIN);

		assertCurrentUser(firstUser, secondUser.getId());
		assertCurrentUser(secondUser, firstUser.getId());
	}

	@Test
	void missingTokenReturnsUnauthorizedInUnifiedFormat() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Требуется аутентификация"))
				.andExpect(jsonPath("$.path").value("/api/auth/me"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	private void assertCurrentUser(User expectedUser, UUID requestedUserId) throws Exception {
		String token = jwtService.generateToken(expectedUser.getUsername());

		MvcResult result = mockMvc.perform(get("/api/auth/me")
				.queryParam("userId", requestedUserId.toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(expectedUser.getId().toString()))
				.andExpect(jsonPath("$.username").value(expectedUser.getUsername()))
				.andExpect(jsonPath("$.role").value(expectedUser.getRole().name()))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn();

		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).size()).isEqualTo(3);
	}

	private User createUser(String prefix, Role role) {
		String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
		User user = new User(username, passwordEncoder.encode(PASSWORD), role);
		return userRepository.saveAndFlush(user);
	}
}
