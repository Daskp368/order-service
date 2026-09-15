package com.example.orderservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.dto.order.CreateOrderRequest;
import com.example.orderservice.dto.order.OrderResponse;
import com.example.orderservice.dto.order.UpdateOrderStatusRequest;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.repository.UserRepository;

@Service
public class OrderService {

	private final OrderRepository orderRepository;
	private final UserRepository userRepository;

	public OrderService(OrderRepository orderRepository, UserRepository userRepository) {
		this.orderRepository = orderRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public OrderResponse createOrder(String username, CreateOrderRequest request) {
		User currentUser = findCurrentUser(username);
		Order order = new Order(
				currentUser,
				request.getDescription().strip(),
				OrderStatus.CREATED,
				LocalDateTime.now());
		Order savedOrder = orderRepository.save(order);

		return toResponse(savedOrder);
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> getCurrentUserOrders(String username) {
		User currentUser = findCurrentUser(username);

		return orderRepository.findAllByUser_IdOrderByCreatedAtDesc(currentUser.getId())
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> getAllOrders() {
		return orderRepository.findAllByOrderByCreatedAtDesc()
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Заказ не найден"));
		order.changeStatus(request.getStatus());

		return toResponse(order);
	}

	@Transactional
	public void deleteOrder(UUID orderId, String username) {
		User currentUser = findCurrentUser(username);
		Order order = findDeletableOrder(orderId, currentUser);
		orderRepository.delete(order);
	}

	private Order findDeletableOrder(UUID orderId, User currentUser) {
		if (currentUser.getRole() == Role.ADMIN) {
			return orderRepository.findById(orderId)
					.orElseThrow(() -> new ResourceNotFoundException("Заказ не найден"));
		}

		return orderRepository.findByIdAndUser_Id(orderId, currentUser.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Заказ не найден"));
	}

	private User findCurrentUser(String username) {
		return userRepository.findByUsername(username)
				.orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
	}

	private OrderResponse toResponse(Order order) {
		return new OrderResponse(
				order.getId(),
				order.getUser().getId(),
				order.getDescription(),
				order.getStatus(),
				order.getCreatedAt());
	}
}
