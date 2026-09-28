package com.example.orderservice.controller.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.example.orderservice.config.OpenApiConfig;
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

@Tag(name = "Пользователи", description = "Административные операции с пользователями")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public interface UserApi {

    @Operation(summary = "Получить всех пользователей",
            description = "Возвращает ADMIN список пользователей, отсортированный по username")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список пользователей получен", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Требуется роль ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<List<UserResponse>> getAllUsers();

    @Operation(summary = "Удалить пользователя",
            description = "Удаляет пользователя и его заказы. ADMIN не может удалить собственную учётную запись")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Пользователь и его заказы удалены"),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Требуется роль ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ADMIN пытается удалить себя",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> deleteUser(
            @Parameter(description = "UUID удаляемого пользователя",
                    example = "0dbdfd3c-439c-46ab-ab78-a7cbe86d540f")
            UUID id,
            @Parameter(hidden = true)
            Authentication authentication);
}
