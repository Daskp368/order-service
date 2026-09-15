package com.example.orderservice.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.UserRepository;
import com.example.orderservice.security.JwtService;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@ActiveProfiles("test")
class DeletionOperationsIntegrationTest {

	private static final String PASSWORD = "strong-password";

	private final MockMvc mockMvc;
	private final UserRepository userRepository;
	private final OrderRepository orderRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final EntityManager entityManager;

	@Autowired
	DeletionOperationsIntegrationTest(MockMvc mockMvc, UserRepository userRepository,
			OrderRepository orderRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			EntityManager entityManager) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
		this.orderRepository = orderRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.entityManager = entityManager;
	}

	@Test
	void ownerDeletesOwnOrder() throws Exception {
		User owner = createUser("stage12_owner", Role.USER);
		Order order = saveOrder(owner, "Собственный заказ");

		mockMvc.perform(delete("/api/orders/{id}", order.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		flushAndClear();
		assertThat(orderRepository.existsById(order.getId())).isFalse();
	}

	@Test
	void regularUserGetsSameNotFoundForForeignAndMissingOrder() throws Exception {
		User currentUser = createUser("stage12_current", Role.USER);
		User anotherUser = createUser("stage12_another", Role.USER);
		Order foreignOrder = saveOrder(anotherUser, "Чужой заказ");
		String authorization = bearerToken(currentUser);

		mockMvc.perform(delete("/api/orders/{id}", foreignOrder.getId())
				.header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Заказ не найден"));

		mockMvc.perform(delete("/api/orders/{id}", UUID.randomUUID())
				.header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Заказ не найден"));

		flushAndClear();
		assertThat(orderRepository.existsById(foreignOrder.getId())).isTrue();
	}

	@Test
	void adminDeletesAnyOrder() throws Exception {
		User owner = createUser("stage12_order_owner", Role.USER);
		User admin = createUser("stage12_order_admin", Role.ADMIN);
		Order order = saveOrder(owner, "Заказ пользователя");

		mockMvc.perform(delete("/api/orders/{id}", order.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isNoContent());

		flushAndClear();
		assertThat(orderRepository.existsById(order.getId())).isFalse();
	}

	@Test
	void authenticatedUserGetsBadRequestForMalformedOrderId() throws Exception {
		User user = createUser("stage12_bad_order_id", Role.USER);

		mockMvc.perform(delete("/api/orders/not-a-uuid")
				.header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Некорректный параметр запроса"))
				.andExpect(jsonPath("$.path").value("/api/orders/not-a-uuid"));
	}

	@Test
	void adminDeletesUserWithOrdersAndOldJwtStopsWorking() throws Exception {
		User admin = createUser("stage12_delete_admin", Role.ADMIN);
		User userToDelete = createUser("stage12_deleted_user", Role.USER);
		Order firstOrder = saveOrder(userToDelete, "Первый заказ");
		Order secondOrder = saveOrder(userToDelete, "Второй заказ");
		String deletedUsersAuthorization = bearerToken(userToDelete);
		entityManager.clear();

		mockMvc.perform(delete("/api/users/{id}", userToDelete.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		flushAndClear();
		assertThat(userRepository.existsById(userToDelete.getId())).isFalse();
		assertThat(orderRepository.existsById(firstOrder.getId())).isFalse();
		assertThat(orderRepository.existsById(secondOrder.getId())).isFalse();

		mockMvc.perform(get("/api/auth/me")
				.header(HttpHeaders.AUTHORIZATION, deletedUsersAuthorization))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void adminCannotDeleteOwnAccount() throws Exception {
		User admin = createUser("stage12_self_admin", Role.ADMIN);

		mockMvc.perform(delete("/api/users/{id}", admin.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("Администратор не может удалить собственную учётную запись"));

		flushAndClear();
		assertThat(userRepository.existsById(admin.getId())).isTrue();
	}

	@Test
	void regularUserGetsForbiddenWhenDeletingUser() throws Exception {
		User regularUser = createUser("stage12_forbidden", Role.USER);
		User userToDelete = createUser("stage12_protected", Role.USER);

		mockMvc.perform(delete("/api/users/{id}", userToDelete.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(regularUser)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.path").value("/api/users/" + userToDelete.getId()));

		flushAndClear();
		assertThat(userRepository.existsById(userToDelete.getId())).isTrue();
	}

	@Test
	void adminGetsNotFoundAndBadRequestForInvalidUserIds() throws Exception {
		User admin = createUser("stage12_invalid_user", Role.ADMIN);
		String authorization = bearerToken(admin);

		mockMvc.perform(delete("/api/users/{id}", UUID.randomUUID())
				.header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Пользователь не найден"));

		mockMvc.perform(delete("/api/users/not-a-uuid")
				.header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Некорректный параметр запроса"));
	}

	@Test
	void missingTokenReturnsUnauthorizedForDeletionEndpoints() throws Exception {
		mockMvc.perform(delete("/api/orders/{id}", UUID.randomUUID()))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/api/users/{id}", UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	private User createUser(String prefix, Role role) {
		String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		String username = prefix + "_" + randomSuffix;
		return userRepository.saveAndFlush(new User(username, passwordEncoder.encode(PASSWORD), role));
	}

	private Order saveOrder(User owner, String description) {
		return orderRepository.saveAndFlush(
				new Order(owner, description, OrderStatus.CREATED, LocalDateTime.now()));
	}

	private String bearerToken(User user) {
		return "Bearer " + jwtService.generateToken(user.getUsername());
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
