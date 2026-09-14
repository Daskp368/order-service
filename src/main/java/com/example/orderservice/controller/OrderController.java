package com.example.orderservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderservice.dto.order.CreateOrderRequest;
import com.example.orderservice.dto.order.OrderResponse;
import com.example.orderservice.dto.order.UpdateOrderStatusRequest;
import com.example.orderservice.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request,
			Authentication authentication) {
		OrderResponse response = orderService.createOrder(authentication.getName(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping
	public ResponseEntity<List<OrderResponse>> getCurrentUserOrders(Authentication authentication) {
		return ResponseEntity.ok(orderService.getCurrentUserOrders(authentication.getName()));
	}

	@GetMapping("/all")
	public ResponseEntity<List<OrderResponse>> getAllOrders() {
		return ResponseEntity.ok(orderService.getAllOrders());
	}

	@PutMapping("/{id}")
	public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable UUID id,
			@Valid @RequestBody UpdateOrderStatusRequest request) {
		return ResponseEntity.ok(orderService.updateOrderStatus(id, request));
	}
}
