package com.example.orderservice.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class OrderService {

	private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);

	private static final int MAX_PAGE_SIZE = 100;
	private static final Sort DEFAULT_ORDER_SORT = Sort.by(
			Sort.Order.desc("createdAt"),
			Sort.Order.desc("id"));

	private final OrderRepository orderRepository;
	private final UserRepository userRepository;

	public OrderService(OrderRepository orderRepository, UserRepository userRepository) {
		this.orderRepository = orderRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public OrderResponse createOrder(String username, CreateOrderRequest request) {
		int descriptionLength = request.getDescription().length();
		LOGGER.info("Начато создание заказа: username={}, descriptionLength={}", username, descriptionLength);

		try {
			User currentUser = findCurrentUser(username);
			Order order = new Order(
					currentUser,
					request.getDescription().strip(),
					OrderStatus.CREATED,
					LocalDateTime.now());
			Order savedOrder = orderRepository.save(order);
			OrderResponse response = toResponse(savedOrder);

			LOGGER.info("Создание заказа завершено: orderId={}, userId={}, status={}",
					response.getId(), response.getUserId(), response.getStatus());
			return response;
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка создания заказа: username={}, descriptionLength={}",
					username, descriptionLength, exception);
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public PagedResponse<OrderResponse> getCurrentUserOrders(String username, int page, int size) {
		LOGGER.info("Начато получение заказов пользователя: username={}, page={}, size={}", username, page, size);

		try {
			Pageable pageable = createPageable(page, size);
			User currentUser = findCurrentUser(username);
			Page<OrderResponse> orderPage = orderRepository.findAllByUser_Id(currentUser.getId(), pageable)
					.map(this::toResponse);
			PagedResponse<OrderResponse> response = toPagedResponse(orderPage);

			LOGGER.info("Получение заказов пользователя завершено: username={}, page={}, "
					+ "returnedElements={}, totalElements={}, totalPages={}",
					username, response.getPage(), response.getContent().size(),
					response.getTotalElements(), response.getTotalPages());
			return response;
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка получения заказов пользователя: username={}, page={}, size={}",
					username, page, size, exception);
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public PagedResponse<OrderResponse> getAllOrders(int page, int size) {
		LOGGER.info("Начато получение всех заказов: page={}, size={}", page, size);

		try {
			Pageable pageable = createPageable(page, size);
			Page<OrderResponse> orderPage = orderRepository.findAll(pageable)
					.map(this::toResponse);
			PagedResponse<OrderResponse> response = toPagedResponse(orderPage);

			LOGGER.info("Получение всех заказов завершено: page={}, returnedElements={}, "
					+ "totalElements={}, totalPages={}",
					response.getPage(), response.getContent().size(),
					response.getTotalElements(), response.getTotalPages());
			return response;
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка получения всех заказов: page={}, size={}", page, size, exception);
			throw exception;
		}
	}

	@Transactional
	public OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
		OrderStatus requestedStatus = request.getStatus();
		LOGGER.info("Начато изменение статуса заказа: orderId={}, requestedStatus={}", orderId, requestedStatus);

		try {
			Order order = orderRepository.findById(orderId)
					.orElseThrow(() -> new ResourceNotFoundException("Заказ не найден"));
			order.changeStatus(requestedStatus);
			OrderResponse response = toResponse(order);

			LOGGER.info("Изменение статуса заказа завершено: orderId={}, status={}",
					response.getId(), response.getStatus());
			return response;
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка изменения статуса заказа: orderId={}, requestedStatus={}",
					orderId, requestedStatus, exception);
			throw exception;
		}
	}

	@Transactional
	public void deleteOrder(UUID orderId, String username) {
		LOGGER.info("Начато удаление заказа: orderId={}, username={}", orderId, username);

		try {
			User currentUser = findCurrentUser(username);
			Order order = findDeletableOrder(orderId, currentUser);
			orderRepository.delete(order);

			LOGGER.info("Удаление заказа завершено: orderId={}, username={}", orderId, username);
		}
		catch (RuntimeException exception) {
			LOGGER.error("Ошибка удаления заказа: orderId={}, username={}", orderId, username, exception);
			throw exception;
		}
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

	private Pageable createPageable(int page, int size) {
		if (page < 0) {
			throw new InvalidPaginationException("Параметр page не может быть меньше 0");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("Параметр size должен быть от 1 до " + MAX_PAGE_SIZE);
		}

		return PageRequest.of(page, size, DEFAULT_ORDER_SORT);
	}

	private PagedResponse<OrderResponse> toPagedResponse(Page<OrderResponse> page) {
		return new PagedResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
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
