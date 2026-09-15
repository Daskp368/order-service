package com.example.orderservice.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.UserRepository;
import com.example.orderservice.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@ActiveProfiles("test")
class AdminOperationsIntegrationTest {

	private static final String PASSWORD = "strong-password";

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;
	private final OrderRepository orderRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final EntityManager entityManager;

	@Autowired
	AdminOperationsIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository userRepository,
			OrderRepository orderRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			EntityManager entityManager) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.userRepository = userRepository;
		this.orderRepository = orderRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.entityManager = entityManager;
	}

	@Test
	void adminListsAllUsersWithoutSensitiveFields() throws Exception {
		User regularUser = createUser("stage11_regular", Role.USER);
		User admin = createUser("stage11_admin", Role.ADMIN);

		MvcResult result = mockMvc.perform(get("/api/users")
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn();

		JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode regularUserJson = findUser(responseBody, regularUser.getId());
		JsonNode adminJson = findUser(responseBody, admin.getId());

		assertThat(regularUserJson.get("username").asText()).isEqualTo(regularUser.getUsername());
		assertThat(regularUserJson.get("role").asText()).isEqualTo("USER");
		assertThat(adminJson.get("role").asText()).isEqualTo("ADMIN");
		assertThat(responseBody)
				.allSatisfy(user -> {
					assertThat(user.size()).isEqualTo(3);
					assertThat(user.has("password")).isFalse();
					assertThat(user.has("passwordHash")).isFalse();
				});
	}

	@Test
	void adminListsOrdersOfEveryUserFromNewestToOldest() throws Exception {
		User firstOwner = createUser("stage11_first", Role.USER);
		User secondOwner = createUser("stage11_second", Role.USER);
		User admin = createUser("stage11_list_admin", Role.ADMIN);
		Order olderOrder = saveOrder(firstOwner, "Старый заказ", LocalDateTime.of(2026, 9, 11, 10, 0));
		Order newerOrder = saveOrder(secondOwner, "Новый заказ", LocalDateTime.of(2026, 9, 11, 11, 0));

		MvcResult result = mockMvc.perform(get("/api/orders/all")
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn();

		JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode contentNode = responseBody.path("content");
		List<String> orderIds = contentNode.findValuesAsText("id");
		assertThat(orderIds).contains(olderOrder.getId().toString(), newerOrder.getId().toString());
		assertThat(orderIds.indexOf(newerOrder.getId().toString()))
				.isLessThan(orderIds.indexOf(olderOrder.getId().toString()));
		assertThat(findOrder(contentNode, olderOrder.getId()).get("userId").asText())
				.isEqualTo(firstOwner.getId().toString());
		assertThat(findOrder(contentNode, newerOrder.getId()).get("userId").asText())
				.isEqualTo(secondOwner.getId().toString());
		assertThat(responseBody.path("page").asInt()).isZero();
		assertThat(responseBody.path("size").asInt()).isEqualTo(20);
	}

	@Test
	void adminChangesOnlyStatusAndCanUseEveryDefinedStatus() throws Exception {
		User owner = createUser("stage11_status_owner", Role.USER);
		User admin = createUser("stage11_status_admin", Role.ADMIN);
		LocalDateTime createdAt = LocalDateTime.of(2026, 9, 11, 12, 0);
		Order order = saveOrder(owner, "Исходное описание", createdAt);

		for (OrderStatus statusValue : OrderStatus.values()) {
			Map<String, Object> requestBody = Map.of(
					"status", statusValue,
					"description", "Описание нельзя изменить",
					"userId", admin.getId(),
					"createdAt", "2000-01-01T00:00:00");

			mockMvc.perform(put("/api/orders/{id}", order.getId())
					.header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(requestBody)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(order.getId().toString()))
					.andExpect(jsonPath("$.userId").value(owner.getId().toString()))
					.andExpect(jsonPath("$.description").value("Исходное описание"))
					.andExpect(jsonPath("$.status").value(statusValue.name()))
					.andExpect(jsonPath("$.createdAt")
							.value(createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
		}

		entityManager.flush();
		entityManager.clear();

		Order storedOrder = orderRepository.findById(order.getId()).orElseThrow();
		assertThat(storedOrder.getUser().getId()).isEqualTo(owner.getId());
		assertThat(storedOrder.getDescription()).isEqualTo("Исходное описание");
		assertThat(storedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
		assertThat(storedOrder.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void rejectsMissingAndUnknownStatusWithoutChangingOrder() throws Exception {
		User owner = createUser("stage11_invalid_owner", Role.USER);
		User admin = createUser("stage11_invalid_admin", Role.ADMIN);
		Order order = saveOrder(owner, "Заказ", LocalDateTime.of(2026, 9, 11, 13, 0));

		mockMvc.perform(put("/api/orders/{id}", order.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Ошибка валидации"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("status"));

		mockMvc.perform(put("/api/orders/{id}", order.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CANCELLED\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Некорректный JSON"));

		entityManager.flush();
		entityManager.clear();
		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.CREATED);
	}

	@Test
	void adminGetsNotFoundWhenUpdatingMissingOrder() throws Exception {
		User admin = createUser("stage11_missing_admin", Role.ADMIN);

		mockMvc.perform(put("/api/orders/{id}", UUID.randomUUID())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"IN_PROGRESS\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Заказ не найден"));
	}

	@Test
	void adminGetsBadRequestForMalformedOrderId() throws Exception {
		User admin = createUser("stage11_bad_id_admin", Role.ADMIN);

		mockMvc.perform(put("/api/orders/not-a-uuid")
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"IN_PROGRESS\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Некорректный параметр запроса"))
				.andExpect(jsonPath("$.path").value("/api/orders/not-a-uuid"));
	}

	@Test
	void regularUserGetsForbiddenForEveryAdminOperation() throws Exception {
		User regularUser = createUser("stage11_forbidden", Role.USER);
		Order order = saveOrder(regularUser, "Неизменяемый заказ", LocalDateTime.of(2026, 9, 11, 14, 0));
		String authorization = bearerToken(regularUser);

		mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.path").value("/api/users"));

		mockMvc.perform(get("/api/orders/all").header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.path").value("/api/orders/all"));

		mockMvc.perform(put("/api/orders/{id}", order.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"COMPLETED\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.path").value("/api/orders/" + order.getId()));

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.CREATED);
	}

	@Test
	void missingTokenReturnsUnauthorizedForEveryAdminOperation() throws Exception {
		UUID orderId = UUID.randomUUID();

		mockMvc.perform(get("/api/users"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/orders/all"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(put("/api/orders/{id}", orderId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"COMPLETED\"}"))
				.andExpect(status().isUnauthorized());
	}

	private User createUser(String prefix, Role role) {
		String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		String username = prefix + "_" + randomSuffix;
		return userRepository.saveAndFlush(new User(username, passwordEncoder.encode(PASSWORD), role));
	}

	private Order saveOrder(User user, String description, LocalDateTime createdAt) {
		return orderRepository.saveAndFlush(new Order(user, description, OrderStatus.CREATED, createdAt));
	}

	private String bearerToken(User user) {
		return "Bearer " + jwtService.generateToken(user.getUsername());
	}

	private JsonNode findUser(JsonNode users, UUID userId) {
		return findById(users, userId, "Пользователь отсутствует в ответе");
	}

	private JsonNode findOrder(JsonNode orders, UUID orderId) {
		return findById(orders, orderId, "Заказ отсутствует в ответе");
	}

	private JsonNode findById(JsonNode values, UUID id, String errorMessage) {
		for (JsonNode value : values) {
			if (id.toString().equals(value.path("id").asText())) {
				return value;
			}
		}

		throw new AssertionError(errorMessage + ": " + id);
	}
}
