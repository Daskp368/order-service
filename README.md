# Order Service

Учебный микросервис управления пользователями и заказами на Java и Spring Boot.

## Текущий прогресс

- [x] Этап 0: требования и API-контракт.
- [x] Этап 1: минимальная основа Spring Boot.
- [ ] Этап 2: подключение PostgreSQL.

API-контракт находится в [`docs/stage-0-api-contract.md`](docs/stage-0-api-contract.md).

## Технологии этапа 1

- Java 17 — целевая версия исходного кода и байткода;
- Spring Boot 3.5.16;
- Maven Wrapper;
- Spring Web;
- Spring Validation;
- Spring Security;
- JUnit и Spring Boot Test.

JPA, PostgreSQL Driver и Flyway будут добавлены одновременно с настройкой базы данных на следующих этапах.

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

## Ожидаемое поведение безопасности

На этапе 1 Spring Security использует временную автоматическую конфигурацию. При запуске в логах появляется сгенерированный пароль, а запрос без аутентификации получает `401 Unauthorized`.

Это не окончательная безопасность проекта. Собственная конфигурация JWT будет реализована на соответствующем этапе.

## Документация

- [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/)
- [Apache Maven](https://maven.apache.org/guides/)
