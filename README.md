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

API-контракт находится в [`docs/stage-0-api-contract.md`](docs/stage-0-api-contract.md).
Конспект основы Spring Boot находится в [`docs/stage-1-spring-boot-foundation.md`](docs/stage-1-spring-boot-foundation.md).
Конспект подключения базы находится в [`docs/stage-2-database.md`](docs/stage-2-database.md).
Конспект миграций находится в [`docs/stage-3-migrations.md`](docs/stage-3-migrations.md).
Конспект сущностей JPA находится в [`docs/stage-4-jpa-entities.md`](docs/stage-4-jpa-entities.md).
Конспект репозиториев находится в [`docs/stage-5-repositories.md`](docs/stage-5-repositories.md).
Конспект регистрации находится в [`docs/stage-6-registration.md`](docs/stage-6-registration.md).

## Технологии этапа 1

- Java 17 — целевая версия исходного кода и байткода;
- Spring Boot 3.5.16;
- Maven Wrapper;
- Spring Web;
- Spring Validation;
- Spring Security;
- JUnit и Spring Boot Test.

JPA и PostgreSQL Driver добавлены на этапе 2. Flyway и первая версия схемы базы добавлены на этапе 3.

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
| `AuthController.java` | HTTP endpoint регистрации |
| `AuthService.java` | Правила регистрации и транзакция |
| `SecurityConfig.java` | BCrypt и правила доступа к регистрации |

Файл `.env` содержит локальные настройки и не отслеживается Git.

## Ожидаемое поведение безопасности

Начиная с этапа 6, `POST /api/auth/register` доступен без аутентификации и без CSRF-токена. Остальные запросы требуют аутентифицированного пользователя. При запуске пока ещё появляется сгенерированный временный пароль Spring Security.

Это не окончательная безопасность проекта. Временный пользователь Spring Security будет заменён собственной конфигурацией JWT на этапе аутентификации.

## Документация

- [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/)
- [Apache Maven](https://maven.apache.org/guides/)
