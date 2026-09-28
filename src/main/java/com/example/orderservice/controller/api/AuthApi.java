package com.example.orderservice.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.example.orderservice.config.OpenApiConfig;
import com.example.orderservice.dto.auth.LoginRequest;
import com.example.orderservice.dto.auth.LoginResponse;
import com.example.orderservice.dto.auth.RegisterRequest;
import com.example.orderservice.dto.auth.UserResponse;
import com.example.orderservice.dto.error.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Аутентификация", description = "Регистрация, вход и информация о текущем пользователе")
public interface AuthApi {

    @Operation(summary = "Зарегистрировать пользователя",
            description = "Создаёт пользователя с ролью USER и сохраняет BCrypt-хеш пароля")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пользователь создан", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации или некорректный JSON",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Username уже занят",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<UserResponse> register(RegisterRequest request);

    @Operation(summary = "Войти в систему",
            description = "Проверяет учётные данные и возвращает Bearer JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Учётные данные верны", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации или некорректный JSON",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Неверные учётные данные",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<LoginResponse> login(LoginRequest request);

    @Operation(summary = "Получить текущего пользователя",
            description = "Возвращает безопасный профиль владельца предъявленного JWT")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Текущий пользователь найден", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<UserResponse> currentUser(@Parameter(hidden = true) Authentication authentication);
}
