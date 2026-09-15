package com.example.orderservice.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
class OrderApiIntegrationTest {

	private static final String PASSWORD = "strong-password";

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;
	private final OrderRepository orderRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final EntityManager entityManager;

	@Autowired
	OrderApiIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository userRepository,
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
	void createsOrderWithServerOwnedFieldsAndTrimmedDescription() throws Exception {
		User currentUser = createUser("stage10_owner", Role.USER);
		User anotherUser = createUser("stage10_other", Role.USER);
		String token = jwtService.generateToken(currentUser.getUsername());
		LocalDateTime beforeRequest = LocalDateTime.now().minusSeconds(1);

		Map<String, Object> requestBody = new LinkedHashMap<>();
		requestBody.put("description", "  Новый заказ  ");
		requestBody.put("userId", anotherUser.getId());
		requestBody.put("status", OrderStatus.COMPLETED);
		requestBody.put("createdAt", "2000-01-01T00:00:00");

		MvcResult result = mockMvc.perform(post("/api/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestBody)))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").isString())
				.andExpect(jsonPath("$.userId").value(currentUser.getId().toString()))
				.andExpect(jsonPath("$.description").value("Новый заказ"))
				.andExpect(jsonPath("$.status").value("CREATED"))
				.andExpect(jsonPath("$.createdAt").isString())
				.andExpect(jsonPath("$.user").doesNotExist())
				.andReturn();

		JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
		UUID orderId = UUID.fromString(responseBody.get("id").asText());
		assertThat(responseBody.size()).isEqualTo(5);

		entityManager.flush();
		entityManager.clear();

		Order storedOrder = orderRepository.findById(orderId).orElseThrow();
		assertThat(storedOrder.getUser().getId()).isEqualTo(currentUser.getId());
		assertThat(storedOrder.getDescription()).isEqualTo("Новый заказ");
		assertThat(storedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
		assertThat(storedOrder.getCreatedAt())
				.isBetween(beforeRequest, LocalDateTime.now().plusSeconds(1));
	}

	@Test
	void listsOnlyCurrentUsersOrdersFromNewestToOldest() throws Exception {
		User currentUser = createUser("s10_list_owner", Role.USER);
		User anotherUser = createUser("s10_list_other", Role.USER);
		Order olderOrder = saveOrder(currentUser, "Старый заказ", LocalDateTime.of(2026, 9, 10, 10, 0));
		Order newerOrder = saveOrder(currentUser, "Новый заказ", LocalDateTime.of(2026, 9, 10, 11, 0));
		saveOrder(anotherUser, "Чужой заказ", LocalDateTime.of(2026, 9, 10, 12, 0));

		String token = jwtService.generateToken(currentUser.getUsername());

		MvcResult result = mockMvc.perform(get("/api/orders")
				.queryParam("userId", anotherUser.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].id").value(newerOrder.getId().toString()))
				.andExpect(jsonPath("$.content[0].description").value("Новый заказ"))
				.andExpect(jsonPath("$.content[1].id").value(olderOrder.getId().toString()))
				.andExpect(jsonPath("$.content[1].description").value("Старый заказ"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andReturn();

		JsonNode contentNode = objectMapper.readTree(result.getResponse().getContentAsString()).path("content");
		assertThat(contentNode)
				.allSatisfy(order -> {
					assertThat(order.get("userId").asText()).isEqualTo(currentUser.getId().toString());
					assertThat(order.has("user")).isFalse();
				});
	}

	@Test
	void adminGetsOwnOrdersInsteadOfEveryUsersOrders() throws Exception {
		User regularUser = createUser("stage10_regular", Role.USER);
		User admin = createUser("stage10_admin", Role.ADMIN);
		saveOrder(regularUser, "Заказ пользователя", LocalDateTime.of(2026, 9, 10, 10, 0));
		Order adminOrder = saveOrder(admin, "Заказ администратора", LocalDateTime.of(2026, 9, 10, 11, 0));

		mockMvc.perform(get("/api/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(admin.getUsername())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(adminOrder.getId().toString()))
				.andExpect(jsonPath("$.content[0].userId").value(admin.getId().toString()));
	}

	@Test
	void rejectsBlankDescriptionWithoutCreatingOrder() throws Exception {
		User currentUser = createUser("stage10_invalid", Role.USER);
		long ordersBeforeRequest = orderRepository.count();

		mockMvc.perform(post("/api/orders")
				.header(HttpHeaders.AUTHORIZATION,
						"Bearer " + jwtService.generateToken(currentUser.getUsername()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("description", "   "))))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("Ошибка валидации"))
				.andExpect(jsonPath("$.path").value("/api/orders"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("description"));

		assertThat(orderRepository.count()).isEqualTo(ordersBeforeRequest);
	}

	@Test
	void missingTokenReturnsUnauthorizedForBothEndpoints() throws Exception {
		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("description", "Новый заказ"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.path").value("/api/orders"));

		mockMvc.perform(get("/api/orders"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.path").value("/api/orders"));
	}

	private User createUser(String prefix, Role role) {
		String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
		return userRepository.saveAndFlush(new User(username, passwordEncoder.encode(PASSWORD), role));
	}

	private Order saveOrder(User user, String description, LocalDateTime createdAt) {
		Order order = new Order(user, description, OrderStatus.CREATED, createdAt);
		return orderRepository.saveAndFlush(order);
	}
}
