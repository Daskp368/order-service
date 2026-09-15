package com.example.orderservice.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
		title = "Order Service API",
		version = "1.0",
		description = "Учебный REST API для управления пользователями и заказами"))
@SecurityScheme(
		name = OpenApiConfig.SECURITY_SCHEME_NAME,
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT",
		description = "JWT из ответа POST /api/auth/login. В Swagger UI нужно вставить только значение token")
public class OpenApiConfig {

	public static final String SECURITY_SCHEME_NAME = "bearerAuth";
}
