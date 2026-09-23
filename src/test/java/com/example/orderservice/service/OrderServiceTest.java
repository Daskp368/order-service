package com.example.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.orderservice.dto.common.PagedResponse;
import com.example.orderservice.dto.order.CreateOrderRequest;
import com.example.orderservice.dto.order.OrderResponse;
import com.example.orderservice.dto.order.UpdateOrderStatusRequest;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.exception.InvalidPaginationException;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private OrderService orderService;

	@Test
	void createOrderUsesCurrentUserAndServerOwnedFields() {
		UUID userId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		User currentUser = user(userId, "order_owner", Role.USER);
		CreateOrderRequest request = createOrderRequest("  Новый заказ  ");
		when(userRepository.findByUsername("order_owner")).thenReturn(Optional.of(currentUser));
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
			Order order = invocation.getArgument(0);
			ReflectionTestUtils.setField(order, "id", orderId);
			return order;
		});
		LocalDateTime beforeCall = LocalDateTime.now();

		OrderResponse response = orderService.createOrder("order_owner", request);

		LocalDateTime afterCall = LocalDateTime.now();
		ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
		verify(orderRepository).save(orderCaptor.capture());
		Order savedOrder = orderCaptor.getValue();
		assertThat(savedOrder.getUser()).isSameAs(currentUser);
		assertThat(savedOrder.getDescription()).isEqualTo("Новый заказ");
		assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
		assertThat(savedOrder.getCreatedAt()).isBetween(beforeCall, afterCall);
		assertThat(response.getId()).isEqualTo(orderId);
		assertThat(response.getUserId()).isEqualTo(userId);
		assertThat(response.getDescription()).isEqualTo("Новый заказ");
		assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED);
		assertThat(response.getCreatedAt()).isEqualTo(savedOrder.getCreatedAt());
	}

	@Test
	void createOrderRejectsUnknownCurrentUser() {
		CreateOrderRequest request = createOrderRequest("Новый заказ");
		when(userRepository.findByUsername("missing_user")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orderService.createOrder("missing_user", request))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Пользователь не найден");

		verifyNoInteractions(orderRepository);
	}

	@Test
	void getCurrentUserOrdersQueriesOnlyOwnerAndMapsPage() {
		UUID userId = UUID.randomUUID();
		User currentUser = user(userId, "page_owner", Role.USER);
		Order firstOrder = order(UUID.randomUUID(), currentUser, "Первый", OrderStatus.IN_PROGRESS,
				LocalDateTime.of(2026, 9, 17, 12, 0));
		Order secondOrder = order(UUID.randomUUID(), currentUser, "Второй", OrderStatus.CREATED,
				LocalDateTime.of(2026, 9, 17, 11, 0));
		Page<Order> repositoryPage = new PageImpl<>(
				List.of(firstOrder, secondOrder), PageRequest.of(1, 2), 5);
		when(userRepository.findByUsername("page_owner")).thenReturn(Optional.of(currentUser));
		when(orderRepository.findAllByUser_Id(eq(userId), any(Pageable.class))).thenReturn(repositoryPage);

		PagedResponse<OrderResponse> response = orderService.getCurrentUserOrders("page_owner", 1, 2);

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(orderRepository).findAllByUser_Id(eq(userId), pageableCaptor.capture());
		assertDefaultPageable(pageableCaptor.getValue(), 1, 2);
		assertThat(response.getContent()).extracting(OrderResponse::getId)
				.containsExactly(firstOrder.getId(), secondOrder.getId());
		assertThat(response.getPage()).isEqualTo(1);
		assertThat(response.getSize()).isEqualTo(2);
		assertThat(response.getTotalElements()).isEqualTo(5);
		assertThat(response.getTotalPages()).isEqualTo(3);
	}

	@Test
	void getCurrentUserOrdersRejectsUnknownUser() {
		String username = "missing_user";
		when(userRepository.findByUsername(username))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() ->
				orderService.getCurrentUserOrders(username, 0, 20))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Пользователь не найден");

		verify(userRepository).findByUsername(username);
		verifyNoInteractions(orderRepository);
	}

	@Test
	void getAllOrdersUsesStableSortAndMapsPage() {
		User owner = user(UUID.randomUUID(), "all_owner", Role.USER);
		Order order = order(UUID.randomUUID(), owner, "Общий список", OrderStatus.COMPLETED,
				LocalDateTime.of(2026, 9, 17, 13, 0));
		Page<Order> repositoryPage = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
		when(orderRepository.findAll(any(Pageable.class))).thenReturn(repositoryPage);

		PagedResponse<OrderResponse> response = orderService.getAllOrders(0, 20);

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(orderRepository).findAll(pageableCaptor.capture());
		assertDefaultPageable(pageableCaptor.getValue(), 0, 20);
		assertThat(response.getContent()).singleElement().satisfies(item -> {
			assertThat(item.getId()).isEqualTo(order.getId());
			assertThat(item.getUserId()).isEqualTo(owner.getId());
			assertThat(item.getStatus()).isEqualTo(OrderStatus.COMPLETED);
		});
	}

	@ParameterizedTest
	@CsvSource({
			"-1, 20, 'Параметр page не может быть меньше 0'",
			"0, 0, 'Параметр size должен быть от 1 до 100'",
			"0, 101, 'Параметр size должен быть от 1 до 100'"
	})
	void rejectsPaginationOutsideAllowedRange(int page, int size, String expectedMessage) {
		assertThatThrownBy(() -> orderService.getAllOrders(page, size))
				.isInstanceOf(InvalidPaginationException.class)
				.hasMessage(expectedMessage);

		verifyNoInteractions(orderRepository, userRepository);
	}

	@Test
	void updateOrderStatusChangesManagedEntityWithoutExplicitSave() {
		UUID orderId = UUID.randomUUID();
		User owner = user(UUID.randomUUID(), "status_owner", Role.USER);
		Order order = order(orderId, owner, "Статус", OrderStatus.CREATED,
				LocalDateTime.of(2026, 9, 17, 14, 0));
		UpdateOrderStatusRequest request = updateStatusRequest(OrderStatus.IN_PROGRESS);
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

		OrderResponse response = orderService.updateOrderStatus(orderId, request);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
		assertThat(response.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
		verify(orderRepository, never()).save(any(Order.class));
	}

	@Test
	void updateOrderStatusRejectsMissingOrder() {
		UUID orderId = UUID.randomUUID();
		UpdateOrderStatusRequest request = updateStatusRequest(OrderStatus.COMPLETED);
		when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, request))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Заказ не найден");
	}

	@Test
	void deleteOrderUsesOwnerScopedQueryForRegularUser() {
		UUID userId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		User currentUser = user(userId, "delete_owner", Role.USER);
		Order order = order(orderId, currentUser, "Удалить", OrderStatus.CREATED, LocalDateTime.now());
		when(userRepository.findByUsername("delete_owner")).thenReturn(Optional.of(currentUser));
		when(orderRepository.findByIdAndUser_Id(orderId, userId)).thenReturn(Optional.of(order));

		orderService.deleteOrder(orderId, "delete_owner");

		verify(orderRepository).findByIdAndUser_Id(orderId, userId);
		verify(orderRepository, never()).findById(any(UUID.class));
		verify(orderRepository).delete(order);
	}

	@Test
	void deleteOrderHidesForeignOrMissingOrderFromRegularUser() {
		UUID userId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		User currentUser = user(userId, "regular_user", Role.USER);
		when(userRepository.findByUsername("regular_user")).thenReturn(Optional.of(currentUser));
		when(orderRepository.findByIdAndUser_Id(orderId, userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orderService.deleteOrder(orderId, "regular_user"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Заказ не найден");

		verify(orderRepository, never()).findById(any(UUID.class));
		verify(orderRepository, never()).delete(any(Order.class));
	}

	@Test
	void deleteOrderUsesUnrestrictedLookupForAdmin() {
		UUID orderId = UUID.randomUUID();
		User admin = user(UUID.randomUUID(), "order_admin", Role.ADMIN);
		User owner = user(UUID.randomUUID(), "another_owner", Role.USER);
		Order order = order(orderId, owner, "Чужой заказ", OrderStatus.CREATED, LocalDateTime.now());
		when(userRepository.findByUsername("order_admin")).thenReturn(Optional.of(admin));
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

		orderService.deleteOrder(orderId, "order_admin");

		verify(orderRepository).findById(orderId);
		verify(orderRepository, never()).findByIdAndUser_Id(any(UUID.class), any(UUID.class));
		verify(orderRepository).delete(order);
	}

	@Test
	void deleteOrderRejectsMissingOrderForAdmin() {
		UUID orderId = UUID.randomUUID();
		User admin = user(UUID.randomUUID(), "missing_admin", Role.ADMIN);
		when(userRepository.findByUsername("missing_admin")).thenReturn(Optional.of(admin));
		when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> orderService.deleteOrder(orderId, "missing_admin"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Заказ не найден");

		verify(orderRepository, never()).delete(any(Order.class));
	}

	private void assertDefaultPageable(Pageable pageable, int expectedPage, int expectedSize) {
		assertThat(pageable.getPageNumber()).isEqualTo(expectedPage);
		assertThat(pageable.getPageSize()).isEqualTo(expectedSize);
		assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull()
				.extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
		assertThat(pageable.getSort().getOrderFor("id")).isNotNull()
				.extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
	}

	private User user(UUID id, String username, Role role) {
		User user = new User(username, "password-hash", role);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Order order(UUID id, User user, String description, OrderStatus status, LocalDateTime createdAt) {
		Order order = new Order(user, description, status, createdAt);
		ReflectionTestUtils.setField(order, "id", id);
		return order;
	}

	private CreateOrderRequest createOrderRequest(String description) {
		CreateOrderRequest request = new CreateOrderRequest();
		request.setDescription(description);
		return request;
	}

	private UpdateOrderStatusRequest updateStatusRequest(OrderStatus status) {
		UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
		request.setStatus(status);
		return request;
	}
}
