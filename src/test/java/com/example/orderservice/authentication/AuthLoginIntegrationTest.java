package com.example.orderservice.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.repository.UserRepository;
import com.example.orderservice.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@ActiveProfiles("test")
@Import(AuthLoginIntegrationTest.ProtectedTestController.class)
class AuthLoginIntegrationTest {

	private static final String TEST_SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";
	private static final String PASSWORD = "strong-password";

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Autowired
	AuthLoginIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository userRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Test
	void loginReturnsHs256TokenWithExpectedClaims() throws Exception {
		String username = createUser("login_success");

		MvcResult result = login(username.toUpperCase(Locale.ROOT), PASSWORD)
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(1800))
				.andReturn();

		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(response.size()).isEqualTo(3);
		String token = response.get("token").asText();
		assertThat(jwtService.extractUsername(token)).isEqualTo(username);

		Jws<Claims> signedClaims = Jwts.parser()
				.verifyWith(testSigningKey())
				.build()
				.parseSignedClaims(token);
		assertThat(signedClaims.getHeader().getAlgorithm()).isEqualTo("HS256");
		assertThat(signedClaims.getPayload().getSubject()).isEqualTo(username);
		assertThat(signedClaims.getPayload().getExpiration().getTime()
				- signedClaims.getPayload().getIssuedAt().getTime()).isEqualTo(1_800_000L);
	}

	@Test
	void wrongPasswordAndUnknownUsernameReturnSameSafeError() throws Exception {
		String username = createUser("wrong_credentials");

		assertInvalidCredentials(login(username, "wrong-password"));
		assertInvalidCredentials(login(uniqueUsername("unknown_user"), PASSWORD));
	}

	@Test
	void validBearerTokenCreatesAuthenticationForProtectedRequest() throws Exception {
		String username = createUser("protected_request");
		String token = loginAndReadToken(username, PASSWORD);

		mockMvc.perform(get("/test/protected")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(username))
				.andExpect(jsonPath("$.authority").value("ROLE_USER"));
	}

	@Test
	void missingTokenReturnsUnauthorizedInUnifiedFormat() throws Exception {
		assertProtectedRequestUnauthorized(null);
	}

	@Test
	void malformedTokenReturnsUnauthorized() throws Exception {
		assertProtectedRequestUnauthorized("not-a-jwt");
	}

	@Test
	void tokenWithChangedSignatureReturnsUnauthorized() throws Exception {
		String username = createUser("changed_signature");
		String token = loginAndReadToken(username, PASSWORD);

		assertProtectedRequestUnauthorized(changeSignature(token));
	}

	@Test
	void expiredTokenReturnsUnauthorized() throws Exception {
		String username = createUser("expired_token");
		Instant now = Instant.now();
		String token = Jwts.builder()
				.subject(username)
				.issuedAt(Date.from(now.minusSeconds(1_801)))
				.expiration(Date.from(now.minusSeconds(1)))
				.signWith(testSigningKey(), Jwts.SIG.HS256)
				.compact();

		assertProtectedRequestUnauthorized(token);
	}

	@Test
	void tokenStopsWorkingAfterUserIsDeleted() throws Exception {
		String username = createUser("deleted_user");
		String token = loginAndReadToken(username, PASSWORD);
		User user = userRepository.findByUsername(username).orElseThrow();
		userRepository.delete(user);
		userRepository.flush();

		assertProtectedRequestUnauthorized(token);
	}

	private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", username,
						"password", password))));
	}

	private void assertInvalidCredentials(org.springframework.test.web.servlet.ResultActions resultActions)
			throws Exception {
		resultActions
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Неверные учётные данные"))
				.andExpect(jsonPath("$.path").value("/api/auth/login"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	private String loginAndReadToken(String username, String password) throws Exception {
		MvcResult result = login(username, password)
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
	}

	private void assertProtectedRequestUnauthorized(String token) throws Exception {
		var request = get("/test/protected");
		if (token != null) {
			request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}

		mockMvc.perform(request)
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Требуется аутентификация"))
				.andExpect(jsonPath("$.path").value("/test/protected"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	private String createUser(String prefix) {
		String username = uniqueUsername(prefix);
		User user = new User(username, passwordEncoder.encode(PASSWORD), Role.USER);
		userRepository.saveAndFlush(user);
		return username;
	}

	private String uniqueUsername(String prefix) {
		return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
	}

	private SecretKey testSigningKey() {
		return Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
	}

	private String changeSignature(String token) {
		String[] parts = token.split("\\.");
		char replacement = parts[2].charAt(0) == 'a' ? 'b' : 'a';
		parts[2] = replacement + parts[2].substring(1);
		return String.join(".", parts);
	}

	@RestController
	static class ProtectedTestController {

		@GetMapping("/test/protected")
		Map<String, String> protectedEndpoint(Authentication authentication) {
			String authority = authentication.getAuthorities().iterator().next().getAuthority();
			return Map.of("username", authentication.getName(), "authority", authority);
		}
	}
}
