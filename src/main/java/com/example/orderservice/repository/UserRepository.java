package com.example.orderservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByRole(Role role);

	List<User> findAllByOrderByUsernameAsc();
}
