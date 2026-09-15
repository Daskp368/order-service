package com.example.orderservice.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@ActiveProfiles("test")
class OrderPaginationIntegrationTest {

	private static final String PASSWORD = "strong-password";

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;
	private final OrderRepository orderRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Autowired
	OrderPaginationIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository userRepository,
			OrderRepository orderRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.userRepository = userRepository;
		this.orderRepository = orderRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Test
	void paginatesCurrentUsersOrdersWithoutOverlap() throws Exception {
		User currentUser = createUser("s13_page_owner", Role.USER);
		User anotherUser = createUser("s13_page_other", Role.USER);
		LocalDateTime baseTime = LocalDateTime.of(2026, 9, 15, 10, 0);
		Order oldestOrder = saveOrder(currentUser, "Заказ 1", baseTime);
		Order secondOrder = saveOrder(currentUser, "Заказ 2", baseTime.plusMinutes(1));
		Order thirdOrder = saveOrder(currentUser, "Заказ 3", baseTime.plusMinutes(2));
		Order fourthOrder = saveOrder(currentUser, "Заказ 4", baseTime.plusMinutes(3));
		Order newestOrder = saveOrder(currentUser, "Заказ 5", baseTime.plusMinutes(4));
		saveOrder(anotherUser, "Чужой заказ", baseTime.plusMinutes(5));

		String authorization = bearerToken(currentUser);
		JsonNode firstPage = getPage("/api/orders", authorization, 0, 2);
		JsonNode secondPage = getPage("/api/orders", authorization, 1, 2);

		assertThat(orderIds(firstPage.path("content")))
				.containsExactly(newestOrder.getId().toString(), fourthOrder.getId().toString());
		assertThat(orderIds(secondPage.path("content")))
				.containsExactly(thirdOrder.getId().toString(), secondOrder.getId().toString());
		assertThat(orderIds(firstPage.path("content")))
				.doesNotContainAnyElementsOf(orderIds(secondPage.path("content")));
		assertThat(firstPage.path("page").asInt()).isZero();
		assertThat(secondPage.path("page").asInt()).isEqualTo(1);
		assertThat(firstPage.path("size").asInt()).isEqualTo(2);
		assertThat(firstPage.path("totalElements").asLong()).isEqualTo(5);
		assertThat(firstPage.path("totalPages").asInt()).isEqualTo(3);
		assertThat(firstPage.path("content"))
				.allSatisfy(order -> assertThat(order.path("userId").asText())
						.isEqualTo(currentUser.getId().toString()));
		assertThat(orderIds(firstPage.path("content"))).doesNotContain(oldestOrder.getId().toString());
	}

	@Test
	void usesDefaultPageAndSize() throws Exception {
		User currentUser = createUser("s13_defaults", Role.USER);
		Order order = saveOrder(currentUser, "Заказ", LocalDateTime.of(2026, 9, 15, 11, 0));

		MvcResult result = mockMvc.perform(get("/api/orders")
				.header(HttpHeaders.AUTHORIZATION, bearerToken(currentUser)))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(order.getId().toString()))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andReturn();

		JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(responseBody.size()).isEqualTo(5);
	}

	@Test
	void adminPaginatesOrdersOfEveryOwner() throws Exception {
		User firstOwner = createUser("s13_admin_first", Role.USER);
		User secondOwner = createUser("s13_admin_second", Role.USER);
		User admin = createUser("s13_admin", Role.ADMIN);
		LocalDateTime baseTime = LocalDateTime.of(2026, 9, 15, 12, 0);
		Order oldestOrder = saveOrder(firstOwner, "Старый заказ", baseTime);
		saveOrder(secondOwner, "Средний заказ", baseTime.plusMinutes(1));
		saveOrder(firstOwner, "Новый заказ", baseTime.plusMinutes(2));

		JsonNode secondPage = getPage("/api/orders/all", bearerToken(admin), 1, 2);

		assertThat(orderIds(secondPage.path("content"))).containsExactly(oldestOrder.getId().toString());
		assertThat(secondPage.path("page").asInt()).isEqualTo(1);
		assertThat(secondPage.path("size").asInt()).isEqualTo(2);
		assertThat(secondPage.path("totalElements").asLong()).isEqualTo(3);
		assertThat(secondPage.path("totalPages").asInt()).isEqualTo(2);
	}

	@Test
	void keepsPagesStableWhenCreationTimesMatch() throws Exception {
		User currentUser = createUser("s13_stable_sort", Role.USER);
		LocalDateTime sharedCreatedAt = LocalDateTime.of(2026, 9, 15, 13, 0);
		List<Order> orders = List.of(
				saveOrder(currentUser, "Одинаковое время 1", sharedCreatedAt),
				saveOrder(currentUser, "Одинаковое время 2", sharedCreatedAt),
				saveOrder(currentUser, "Одинаковое время 3", sharedCreatedAt),
				saveOrder(currentUser, "Одинаковое время 4", sharedCreatedAt));
		String authorization = bearerToken(currentUser);

		List<String> firstPageIds = orderIds(getPage("/api/orders", authorization, 0, 2).path("content"));
		List<String> repeatedFirstPageIds = orderIds(getPage("/api/orders", authorization, 0, 2).path("content"));
		List<String> secondPageIds = orderIds(getPage("/api/orders", authorization, 1, 2).path("content"));
		Set<String> combinedIds = new HashSet<>(firstPageIds);
		combinedIds.addAll(secondPageIds);

		assertThat(repeatedFirstPageIds).containsExactlyElementsOf(firstPageIds);
		assertThat(firstPageIds).doesNotContainAnyElementsOf(secondPageIds);
		assertThat(combinedIds)
				.containsExactlyInAnyOrderElementsOf(orders.stream().map(order -> order.getId().toString()).toList());
	}

	@Test
	void returnsEmptyContentForPagePastEnd() throws Exception {
		User currentUser = createUser("s13_empty_page", Role.USER);
		saveOrder(currentUser, "Единственный заказ", LocalDateTime.of(2026, 9, 15, 14, 0));

		JsonNode responseBody = getPage("/api/orders", bearerToken(currentUser), 4, 2);

		assertThat(responseBody.path("content").isArray()).isTrue();
		assertThat(responseBody.path("content")).isEmpty();
		assertThat(responseBody.path("page").asInt()).isEqualTo(4);
		assertThat(responseBody.path("size").asInt()).isEqualTo(2);
		assertThat(responseBody.path("totalElements").asLong()).isEqualTo(1);
		assertThat(responseBody.path("totalPages").asInt()).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({
			"page, -1, Параметр page не может быть меньше 0",
			"size, 0, Параметр size должен быть от 1 до 100",
			"size, 101, Параметр size должен быть от 1 до 100"
	})
	void rejectsPaginationValuesOutsideAllowedRange(String parameter, String value, String expectedMessage)
			throws Exception {
		User currentUser = createUser("s13_invalid_range", Role.USER);

		mockMvc.perform(get("/api/orders")
				.queryParam(parameter, value)
				.header(HttpHeaders.AUTHORIZATION, bearerToken(currentUser)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value(expectedMessage))
				.andExpect(jsonPath("$.path").value("/api/orders"));
	}

	@Test
	void rejectsNonNumericPaginationParameter() throws Exception {
		User currentUser = createUser("s13_invalid_type", Role.USER);

		mockMvc.perform(get("/api/orders")
				.queryParam("size", "много")
				.header(HttpHeaders.AUTHORIZATION, bearerToken(currentUser)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("Некорректный параметр запроса"))
				.andExpect(jsonPath("$.path").value("/api/orders"));
	}

	private JsonNode getPage(String path, String authorization, int page, int size) throws Exception {
		MvcResult result = mockMvc.perform(get(path)
				.queryParam("page", Integer.toString(page))
				.queryParam("size", Integer.toString(size))
				.header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private List<String> orderIds(JsonNode contentNode) {
		List<String> ids = new ArrayList<>();
		contentNode.forEach(order -> ids.add(order.path("id").asText()));
		return ids;
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
}
