package com.example.orderservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.example.orderservice.entity.Role;
import com.example.orderservice.entity.User;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

	private static final String TEST_PASSWORD_HASH = "$2a$10$" + "a".repeat(53);

	private final UserRepository userRepository;

	@Autowired
	UserRepositoryTest(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Test
	void savesUserAndFindsItByUsername() {
		String username = uniqueUsername("repository_user");
		User user = new User(username, TEST_PASSWORD_HASH, Role.USER);

		User savedUser = userRepository.saveAndFlush(user);

		assertThat(savedUser.getId()).isNotNull();
		assertThat(userRepository.existsByUsername(username)).isTrue();
		assertThat(userRepository.findByUsername(username))
				.isPresent()
				.get()
				.extracting(User::getId, User::getUsername, User::getRole)
				.containsExactly(savedUser.getId(), username, Role.USER);
	}

	@Test
	void returnsEmptyResultForUnknownUsername() {
		String username = uniqueUsername("missing_user");

		assertThat(userRepository.existsByUsername(username)).isFalse();
		assertThat(userRepository.findByUsername(username)).isEmpty();
	}

	private String uniqueUsername(String prefix) {
		return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
	}
}
