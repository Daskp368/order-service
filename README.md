# Order Service

Учебный микросервис управления пользователями и заказами на Java и Spring Boot.

## Текущий прогресс

- [x] Этап 0: требования и API-контракт.
- [x] Этап 1: минимальная основа Spring Boot.
- [x] Этап 2: подключение PostgreSQL.
- [x] Этап 3: миграции Flyway.
- [x] Этап 4: сущности JPA.
- [x] Этап 5: репозитории Spring Data JPA.
- [x] Этап 6: регистрация пользователя.
- [x] Этап 7: валидация и единый формат ошибок.
- [x] Этап 8: вход и JWT-аутентификация.

API-контракт находится в [`docs/stage-0-api-contract.md`](docs/stage-0-api-contract.md).
Конспект основы Spring Boot находится в [`docs/stage-1-spring-boot-foundation.md`](docs/stage-1-spring-boot-foundation.md).
Конспект подключения базы находится в [`docs/stage-2-database.md`](docs/stage-2-database.md).
Конспект миграций находится в [`docs/stage-3-migrations.md`](docs/stage-3-migrations.md).
Конспект сущностей JPA находится в [`docs/stage-4-jpa-entities.md`](docs/stage-4-jpa-entities.md).
Конспект репозиториев находится в [`docs/stage-5-repositories.md`](docs/stage-5-repositories.md).
Конспект регистрации находится в [`docs/stage-6-registration.md`](docs/stage-6-registration.md).
Конспект валидации и обработки ошибок находится в [`docs/stage-7-validation-errors.md`](docs/stage-7-validation-errors.md).
Конспект входа и JWT-аутентификации находится в [`docs/stage-8-jwt-authentication.md`](docs/stage-8-jwt-authentication.md).

## Технологии этапа 1

- Java 17 — целевая версия исходного кода и байткода;
- Spring Boot 3.5.16;
- Maven Wrapper;
- Spring Web;
- Spring Validation;
- Spring Security;
- JUnit и Spring Boot Test.

JPA и PostgreSQL Driver добавлены на этапе 2. Flyway и первая версия схемы базы добавлены на этапе 3.
JJWT 0.13.0 и stateless Bearer-аутентификация добавлены на этапе 8.

## Команды

Проверить проект:

```bash
./mvnw test
```

Запустить приложение:

```bash
./mvnw spring-boot:run
```

Остановить запущенное приложение: `Ctrl+C`.

Глобальная установка Maven не требуется: скрипт `mvnw` скачивает и использует подходящую версию Maven.

Для запуска этапа 8 добавьте в локальный `.env` случайный Base64-ключ длиной 32 байта:

```bash
openssl rand -base64 32
```

```properties
JWT_SECRET=<результат команды>
```

## Главные файлы

| Файл | Назначение |
|---|---|
| `pom.xml` | Описание проекта, версия Java, зависимости и плагины Maven |
| `mvnw` | Maven Wrapper для macOS и Linux |
| `mvnw.cmd` | Maven Wrapper для Windows |
| `OrderServiceApplication.java` | Точка входа в приложение |
| `application.properties` | Настройки Spring Boot |
| `OrderServiceApplicationTests.java` | Проверка загрузки Spring Context |
| `.env.example` | Шаблон локальных переменных подключения к БД |
| `V1__create_users_and_orders.sql` | Первая версия структуры базы данных |
| `User.java`, `Order.java` | JPA-сущности пользователей и заказов |
| `Role.java`, `OrderStatus.java` | Допустимые роли и статусы заказов |
| `UserRepository.java` | Доступ к данным пользователей |
| `OrderRepository.java` | Доступ к данным заказов |
| `RegisterRequest.java`, `UserResponse.java` | Входной и выходной DTO регистрации |
| `LoginRequest.java`, `LoginResponse.java` | Входной и выходной DTO аутентификации |
| `AuthController.java` | HTTP endpoints регистрации и входа |
| `AuthService.java` | Регистрация, проверка учётных данных и выпуск JWT |
| `SecurityConfig.java` | BCrypt, stateless-режим и правила доступа к endpoints |
| `DatabaseUserDetailsService.java` | Загрузка пользователя и роли из PostgreSQL для Spring Security |
| `JwtService.java` | Выпуск и проверка JWT с подписью `HS256` |
| `JwtAuthenticationFilter.java` | Bearer-аутентификация защищённых запросов |
| `RestAuthenticationEntryPoint.java`, `RestAccessDeniedHandler.java` | Единые JSON-ответы `401` и `403` |
| `ApiErrorResponse.java`, `FieldValidationError.java` | Единый JSON обычных ошибок и ошибок полей |
| `ApiExceptionHandler.java` | Преобразование исключений в безопасные HTTP-ответы |
| `CreateOrderRequest.java` | Входной DTO с правилом проверки будущего описания заказа |
| `TrimmedSize.java`, `TrimmedSizeValidator.java` | Проверка длины строки после удаления крайних пробелов |

Файл `.env` содержит локальные настройки и не отслеживается Git.

## Ожидаемое поведение безопасности

`POST /api/auth/register` и `POST /api/auth/login` доступны без аутентификации. Остальные запросы требуют JWT в заголовке:

```http
Authorization: Bearer <token>
```

Приложение работает без HTTP-сессии, form login и HTTP Basic. Пользователь и его актуальная роль загружаются из PostgreSQL при каждом защищённом запросе. Ролевые ограничения конкретных административных endpoints будут добавлены на этапе 11.

## Документация

- [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/)
- [Apache Maven](https://maven.apache.org/guides/)
