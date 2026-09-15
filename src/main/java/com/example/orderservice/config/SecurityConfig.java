package com.example.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.example.orderservice.security.JwtAuthenticationFilter;
import com.example.orderservice.security.RestAccessDeniedHandler;
import com.example.orderservice.security.RestAuthenticationEntryPoint;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

	private static final RequestMatcher REGISTRATION_REQUEST = PathPatternRequestMatcher
			.withDefaults()
			.matcher(HttpMethod.POST, "/api/auth/register");
	private static final RequestMatcher LOGIN_REQUEST = PathPatternRequestMatcher
			.withDefaults()
			.matcher(HttpMethod.POST, "/api/auth/login");
	private static final RequestMatcher ALL_ORDERS_REQUEST = PathPatternRequestMatcher
			.withDefaults()
			.matcher(HttpMethod.GET, "/api/orders/all");
	private static final RequestMatcher UPDATE_ORDER_REQUEST = PathPatternRequestMatcher
			.withDefaults()
			.matcher(HttpMethod.PUT, "/api/orders/{id}");
	private static final RequestMatcher ALL_USERS_REQUEST = PathPatternRequestMatcher
			.withDefaults()
			.matcher(HttpMethod.GET, "/api/users");
	private static final RequestMatcher DELETE_USER_REQUEST = PathPatternRequestMatcher
			.withDefaults()
			.matcher(HttpMethod.DELETE, "/api/users/{id}");

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(authenticationProvider);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationEntryPoint authenticationEntryPoint,
			RestAccessDeniedHandler accessDeniedHandler) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(REGISTRATION_REQUEST, LOGIN_REQUEST).permitAll()
						.requestMatchers(ALL_ORDERS_REQUEST, UPDATE_ORDER_REQUEST, ALL_USERS_REQUEST,
								DELETE_USER_REQUEST).hasRole("ADMIN")
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
