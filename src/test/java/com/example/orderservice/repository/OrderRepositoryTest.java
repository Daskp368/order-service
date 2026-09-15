package com.example.orderservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

	private static final String TEST_PASSWORD_HASH = "$2a$10$" + "a".repeat(53);

	private final UserRepository userRepository;
	private final OrderRepository orderRepository;

	@Autowired
	OrderRepositoryTest(UserRepository userRepository, OrderRepository orderRepository) {
		this.userRepository = userRepository;
		this.orderRepository = orderRepository;
	}

	@Test
	void findsOnlyOwnersOrdersFromNewestToOldest() {
		User firstUser = saveUser("first_user");
		User secondUser = saveUser("second_user");

		Order olderOrder = new Order(
				firstUser,
				"Первый заказ пользователя",
				OrderStatus.CREATED,
				LocalDateTime.of(2026, 9, 4, 10, 0));
		Order newerOrder = new Order(
				firstUser,
				"Второй заказ пользователя",
				OrderStatus.IN_PROGRESS,
				LocalDateTime.of(2026, 9, 4, 11, 0));
		Order anotherUsersOrder = new Order(
				secondUser,
				"Чужой заказ",
				OrderStatus.CREATED,
				LocalDateTime.of(2026, 9, 4, 12, 0));

		orderRepository.saveAllAndFlush(List.of(olderOrder, newerOrder, anotherUsersOrder));

		Page<Order> result = orderRepository.findAllByUser_Id(
				firstUser.getId(),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

		assertThat(result.getContent())
				.extracting(Order::getDescription)
				.containsExactly("Второй заказ пользователя", "Первый заказ пользователя");
		assertThat(result.getContent())
				.allSatisfy(order -> assertThat(order.getUser().getId()).isEqualTo(firstUser.getId()));
		assertThat(result.getTotalElements()).isEqualTo(2);
		assertThat(result.getTotalPages()).isEqualTo(1);
	}

	private User saveUser(String prefix) {
		String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
		return userRepository.saveAndFlush(new User(username, TEST_PASSWORD_HASH, Role.USER));
	}
}
