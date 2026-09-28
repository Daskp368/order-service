package com.example.orderservice.controller.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.example.orderservice.config.OpenApiConfig;
import com.example.orderservice.dto.common.PagedResponse;
import com.example.orderservice.dto.error.ApiErrorResponse;
import com.example.orderservice.dto.order.CreateOrderRequest;
import com.example.orderservice.dto.order.OrderResponse;
import com.example.orderservice.dto.order.UpdateOrderStatusRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Заказы", description = "Создание, получение, изменение статуса и удаление заказов")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public interface OrderApi {

    @Operation(summary = "Создать заказ",
            description = "Создаёт заказ текущего пользователя со статусом CREATED и серверным временем создания")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Заказ создан", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации или некорректный JSON",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<OrderResponse> createOrder(
            CreateOrderRequest request,
            @Parameter(hidden = true) Authentication authentication);

    @Operation(summary = "Получить свои заказы",
            description = "Возвращает страницу заказов владельца JWT от новых к старым")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Страница заказов получена", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Некорректные page или size",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<PagedResponse<OrderResponse>> getCurrentUserOrders(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "Номер страницы, начиная с нуля", example = "0",
                    schema = @Schema(implementation = Integer.class, minimum = "0", defaultValue = "0"))
            int page,
            @Parameter(description = "Количество заказов на странице", example = "20",
                    schema = @Schema(implementation = Integer.class, minimum = "1", maximum = "100",
                            defaultValue = "20"))
            int size);

    @Operation(summary = "Получить все заказы",
            description = "Возвращает ADMIN страницу заказов всех пользователей от новых к старым")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Страница заказов получена", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Некорректные page или size",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Требуется роль ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<PagedResponse<OrderResponse>> getAllOrders(
            @Parameter(description = "Номер страницы, начиная с нуля", example = "0",
                    schema = @Schema(implementation = Integer.class, minimum = "0", defaultValue = "0"))
            int page,
            @Parameter(description = "Количество заказов на странице", example = "20",
                    schema = @Schema(implementation = Integer.class, minimum = "1", maximum = "100",
                            defaultValue = "20"))
            int size);

    @Operation(summary = "Изменить статус заказа",
            description = "Позволяет ADMIN установить CREATED, IN_PROGRESS или COMPLETED")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус заказа изменён", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID, JSON или статус",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Требуется роль ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Заказ не найден",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<OrderResponse> updateOrderStatus(
            @Parameter(description = "UUID заказа", example = "2715fc32-0bc4-435d-ad2d-d09a7f19c607")
            UUID id,
            UpdateOrderStatusRequest request);

    @Operation(summary = "Удалить заказ",
            description = "Владелец удаляет свой заказ, ADMIN — любой. Для USER чужой заказ скрывается ответом 404")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Заказ удалён"),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "JWT отсутствует или недействителен",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Заказ не найден или не принадлежит текущему USER",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> deleteOrder(
            @Parameter(description = "UUID заказа", example = "2715fc32-0bc4-435d-ad2d-d09a7f19c607")
            UUID id,
            @Parameter(hidden = true) Authentication authentication);
}
