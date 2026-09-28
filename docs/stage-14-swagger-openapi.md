# Этап 14. Swagger и OpenAPI

## Результат

Приложение формирует машиночитаемое описание существующего REST API и показывает его в Swagger UI:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
http://localhost:8080/v3/api-docs.yaml
```

`/swagger-ui.html` перенаправляет браузер на `/swagger-ui/index.html`. JSON-документ использует
OpenAPI 3.1 и содержит:

- общую информацию об Order Service API;
- три группы операций: аутентификация, пользователи и заказы;
- восемь путей и десять HTTP-операций из согласованного контракта;
- схемы DTO, enum-значения, примеры и ограничения;
- успешные и ошибочные HTTP-ответы;
- Bearer-схему `bearerAuth` для JWT;
- параметры пагинации `page` и `size`.

Swagger UI позволяет получить JWT через `POST /api/auth/login`, сохранить его через кнопку
`Authorize` и вызывать защищённые операции. Swagger добавляет префикс `Bearer` автоматически: в поле
авторизации нужно вставлять только значение `token` из ответа входа.

## Добавленные и изменённые классы и файлы

| Статус | Класс или файл | Что сделано |
|---|---|---|
| Изменён | `pom.xml` | Добавлен `springdoc-openapi-starter-webmvc-ui` версии `2.9.0` |
| Новый | `OpenApiConfig` | Заданы сведения об API и схема Bearer JWT |
| Изменён | `SecurityConfig` | Разрешён публичный доступ только к OpenAPI и Swagger UI |
| Изменён | `AuthController` | Описаны регистрация, вход, текущий пользователь и ответы |
| Изменён | `OrderController` | Описаны операции заказов, JWT, UUID и пагинация |
| Изменён | `UserController` | Описаны административные операции пользователей |
| Изменены | DTO запросов и ответов | Добавлены описания, примеры, форматы и ограничения схем |
| Изменены | DTO ошибок | Описаны единый ответ ошибки и ошибки отдельных полей |
| Новый | `OpenApiIntegrationTest` | Проверяет документ, схемы, операции, безопасность и Swagger UI |
| Новый | `stage-14-swagger-openapi.md` | Содержит разбор этапа, проверку, практику и вопросы |
| Изменён | `README.md` | Зафиксированы завершение этапа и адреса документации |

Сервисы, репозитории, сущности, миграции и таблицы не изменялись. Документация описывает уже
существующий контракт, а не создаёт новую бизнес-логику.

## Зависимость springdoc

В `pom.xml` добавлена версия:

```xml
<springdoc.version>2.9.0</springdoc.version>
```

И starter для Spring MVC со Swagger UI:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

Приложение использует Spring Boot 3.5.16, поэтому выбрана ветка springdoc `2.x`. Ветка springdoc
`3.x` предназначена для Spring Boot 4.

Starter добавляет две части:

1. Генерацию OpenAPI-документа из Spring MVC-контроллеров и аннотаций.
2. Готовый Swagger UI, который загружает этот документ и строит интерактивную страницу.

Контроллер для `/v3/api-docs` вручную не создаётся. Его регистрирует автоконфигурация springdoc.

## Как формируется OpenAPI-документ

При запуске springdoc изучает Spring Context и метаданные приложения:

```text
@RequestMapping, @GetMapping, @PostMapping, ...
                    ↓
сигнатуры методов Controller и типы ResponseEntity
                    ↓
DTO, Bean Validation и @Schema
                    ↓
@Operation, @ApiResponse, @SecurityRequirement
                    ↓
OpenAPI document → /v3/api-docs
                    ↓
Swagger UI загружает document и строит страницу
```

OpenAPI-документ создаётся из реального кода. Например, возвращаемый тип
`ResponseEntity<PagedResponse<OrderResponse>>` становится схемой `PagedResponseOrderResponse`, а
вложенный `content` содержит элементы `OrderResponse`.

Springdoc не обращается к таблице `orders`, чтобы построить схему. Он анализирует Java-типы и
аннотации. Данные из PostgreSQL появляются только при выполнении конкретного запроса через кнопку
`Execute`.

## Общая конфигурация API

Класс `OpenApiConfig` находится в пакете `config`, как предусмотрено ТЗ:

```java
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "Order Service API",
        version = "1.0",
        description = "Учебный REST API для управления пользователями и заказами"))
@SecurityScheme(
        name = OpenApiConfig.SECURITY_SCHEME_NAME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";
}
```

Имя схемы вынесено в константу. Контроллеры ссылаются на ту же константу, поэтому опечатка в строке
`bearerAuth` не приведёт к ссылке на несуществующую схему.

## Bearer JWT в OpenAPI

`@SecurityScheme` описывает способ передачи токена:

```text
type         = http
scheme       = bearer
bearerFormat = JWT
name         = bearerAuth
```

Это метаданные документации. Аннотация не проверяет подпись токена, не загружает пользователя и не
назначает роли. Реальную аутентификацию по-прежнему выполняют:

```text
JwtAuthenticationFilter
        ↓
JwtService
        ↓
DatabaseUserDetailsService
        ↓
SecurityContext
        ↓
правила SecurityConfig
```

`@SecurityRequirement(name = "bearerAuth")` связывает операцию со схемой. После `Authorize` Swagger
UI добавляет к такому запросу заголовок:

```http
Authorization: Bearer <token>
```

Без `@SecurityRequirement` реальная защита продолжила бы работать, но Swagger UI не показал бы замок
и не отправил бы сохранённый токен для этой операции.

## Почему Swagger UI доступен без JWT

`SecurityConfig` разрешает только технические пути:

```text
/v3/api-docs/**
/v3/api-docs.yaml
/swagger-ui/**
/swagger-ui.html
```

Это необходимо, потому что Swagger UI сначала должен загрузить HTML, JavaScript и OpenAPI-документ,
а JWT пользователь получает уже после вызова `/api/auth/login`.

Публичность документации не делает публичными бизнес-операции. После правил документации продолжают
действовать существующие ограничения:

- регистрация и вход — публичные;
- `/api/orders/all`, изменение статуса и операции пользователей — только `ADMIN`;
- остальные бизнес-операции — только с действительным JWT.

Для production-окружения документацию можно отдельно ограничить или отключить свойствами
`springdoc.api-docs.enabled` и `springdoc.swagger-ui.enabled`. В учебном проекте она включена, потому
что является обязательным результатом этапа 14.

## Группы и операции

`@Tag` объединяет методы в смысловые разделы:

| Группа | Контроллер | Операции |
|---|---|---|
| Аутентификация | `AuthController` | `register`, `login`, `currentUser` |
| Пользователи | `UserController` | `getAllUsers`, `deleteUser` |
| Заказы | `OrderController` | создание, два списка, изменение статуса, удаление |

`@Operation` содержит короткий `summary` и более подробный `description`. Эти тексты объясняют не
только HTTP-действие, но и важные бизнес-правила: серверное назначение владельца, доступ ADMIN или
сокрытие чужого заказа ответом `404`.

`@ApiResponses` перечисляет наблюдаемые результаты конкретной операции. Например, изменение статуса
заказа документирует:

```text
200 — статус изменён
400 — некорректный UUID, JSON или статус
401 — JWT отсутствует или недействителен
403 — требуется роль ADMIN
404 — заказ не найден
```

Ошибочные ответы ссылаются на `ApiErrorResponse`, поэтому клиент видит единый формат ошибки вместо
несвязанного текстового описания.

## Схемы DTO

`@Schema` добавлена к DTO и их полям. Она уточняет:

- назначение объекта;
- описание поля;
- пример значения;
- `uuid` и `date-time` форматы;
- минимальную и максимальную длину;
- шаблон username;
- минимальные и максимальные числовые значения.

Стандартные ограничения `@NotBlank`, `@Size`, `@Pattern` и `@NotNull` springdoc также умеет читать.
Однако проект содержит собственные валидаторы:

- `@MinCodePoints`;
- `@MaxUtf8Bytes`;
- `@TrimmedSize`.

OpenAPI не знает их смысл автоматически, поэтому ограничения дополнительно сформулированы в
`@Schema`. Для пароля намеренно не указан `maxLength = 72`: бизнес-правило считает байты UTF-8, а
`maxLength` в JSON Schema описывает длину строки. Вместо неточного ограничения схема прямо сообщает
про лимит 72 байта.

## Параметры page и size

Параметры списков заказов описаны через `@Parameter`:

| Параметр | Тип | По умолчанию | Ограничение |
|---|---|---:|---:|
| `page` | integer, int32 | `0` | минимум `0` |
| `size` | integer, int32 | `20` | от `1` до `100` |

Для `@Schema` явно задан `implementation = Integer.class`. Если указать только `minimum` и
`maximum`, не передав тип, схема параметра может стать строковой. Интеграционный тест закрепляет
правильные `type: integer` и `format: int32`.

Схема описывает контракт для клиента, а `OrderService.createPageable` остаётся источником реальной
проверки. Если клиент всё же отправит `size=101`, сервис вернёт `400 Bad Request`.

## Служебный Authentication не является параметром API

Контроллер получает текущего пользователя через аргумент:

```java
Authentication authentication
```

Клиент не передаёт этот объект. Его создаёт Spring Security после проверки JWT. Поэтому аргумент
отмечен как:

```java
@Parameter(hidden = true)
```

Без скрытия инструмент документирования мог бы ошибочно представить внутренний объект Spring
Security как параметр клиентского запроса.

## Явный media type

На контроллерах указан:

```java
@RequestMapping(value = "/api/...", produces = MediaType.APPLICATION_JSON_VALUE)
```

Аннотацию обрабатывает Spring MVC, а springdoc использует эту информацию в OpenAPI. Благодаря этому
успешные и ошибочные ответы показаны как `application/json`, а не как неопределённый `*/*`.

Для ответов `204 No Content` тело по-прежнему отсутствует.

## Путь запроса из Swagger UI

Пример для списка заказов текущего пользователя:

```text
1. Пользователь нажимает Authorize и вводит JWT
                         ↓
2. Swagger UI читает @SecurityRequirement операции
                         ↓
3. Браузер отправляет GET /api/orders?page=0&size=20
   с заголовком Authorization: Bearer <token>
                         ↓
4. JwtAuthenticationFilter проверяет JWT
                         ↓
5. DatabaseUserDetailsService загружает пользователя и роль
                         ↓
6. SecurityConfig разрешает аутентифицированный запрос
                         ↓
7. OrderController получает Authentication, page и size
                         ↓
8. OrderService и OrderRepository получают страницу владельца
                         ↓
9. PagedResponse<OrderResponse> сериализуется в JSON
                         ↓
10. Swagger UI показывает 200, response headers и response body
```

Swagger UI является HTTP-клиентом. После отправки запроса он не участвует в работе контроллера,
сервиса, репозитория или базы данных.

## Ручная проверка Swagger UI

Через реальную страницу Swagger UI выполнен сценарий с одноразовым пользователем:

1. `POST /api/auth/login` вернул `200 OK`, JWT и срок действия 1800 секунд.
2. JWT был сохранён через `Authorize`; Swagger UI отметил защищённые операции закрытым замком.
3. `GET /api/auth/me` вернул `200 OK` и профиль владельца JWT.
4. `POST /api/orders` вернул `201 Created`, назначил владельца, `CREATED` и `createdAt`.
5. `GET /api/orders?page=0&size=20` вернул созданный заказ и пять полей метаданных.
6. `GET /api/orders/all` с ролью USER вернул ожидаемый `403 Forbidden` в едином JSON-формате.

После проверки одноразовый пользователь удалён через административный endpoint. Его заказ удалён
каскадно, поэтому в учебной базе не осталось тестовых данных этого сценария.

## Автоматическая проверка OpenAPI

`OpenApiIntegrationTest` загружает полный Spring Context и вызывает технические endpoints через
`MockMvc`.

Тест проверяет:

- публичный доступ к `/v3/api-docs`;
- заголовок, версию и OpenAPI 3.x;
- `bearerAuth` с `type=http`, `scheme=bearer`, `bearerFormat=JWT`;
- все восемь путей и десять операций контракта;
- отсутствие security requirement у регистрации и входа;
- наличие security requirement у восьми защищённых операций;
- ограничения username и description;
- структуру `PagedResponse<OrderResponse>`;
- integer-тип, значения по умолчанию и границы `size`;
- ссылку ошибок на `ApiErrorResponse`;
- редирект `/swagger-ui.html` и загрузку HTML;
- доступность YAML-документа;
- сохранение `401` у `/api/auth/me` без JWT.

Результаты проверки этапа:

- целевой `./mvnw -Dtest=OpenApiIntegrationTest test` — 4 теста, 0 ошибок и 0 падений;
- полный `./mvnw test` — 77 тестов, 0 ошибок и 0 падений.

## Почему Swagger UI не заменяет тесты

Swagger UI удобен для изучения и ручной проверки одного сценария, но он не доказывает, что:

- все негативные сценарии работают после каждого изменения;
- два пользователя не видят данные друг друга;
- транзакции и каскадное удаление сохраняют целостность;
- одинаковые `createdAt` не нарушают стабильность страниц;
- внутренний сервис корректно ведёт себя во всех ветках.

Автоматические тесты повторяемы и запускаются одной командой. Swagger UI дополняет их как
интерактивное представление контракта и инструмент ручного исследования.

## Новые аннотации и кто их обрабатывает

### `@OpenAPIDefinition`

Обрабатывается springdoc. Задаёт общую информацию OpenAPI-документа. Без неё endpoints останутся в
схеме, но заголовок, версия и описание будут стандартными или отсутствующими.

### `@Info`

Является частью `@OpenAPIDefinition`. Заполняет раздел `info`. Без него API сложнее отличить от другого
сервиса или версии.

### `@SecurityScheme`

Обрабатывается springdoc и создаёт `components.securitySchemes.bearerAuth`. Без неё ссылки
`@SecurityRequirement` не будут описывать реальный способ ввода JWT.

### `@SecurityRequirement`

Обрабатывается springdoc на уровне контроллера или метода. Связывает операцию с `bearerAuth`. Не
заменяет Spring Security и не выдаёт доступ самостоятельно.

### `@Tag`

Обрабатывается springdoc и группирует операции в Swagger UI. Без неё методы будут распределены по
автоматическим названиям контроллеров.

### `@Operation`

Обрабатывается springdoc и добавляет понятные summary и description. Без неё endpoint останется
доступен, но будет описан в основном именем Java-метода.

### `@ApiResponse` и `@ApiResponses`

Обрабатываются springdoc. Фиксируют HTTP-статусы, смысл ответа и схему ошибки. Без них документация не
покажет все бизнес-ошибки, потому что они формируются в `ApiExceptionHandler` и Spring Security.

### `@Schema`

Обрабатывается swagger-core, используемым springdoc. Описывает DTO и поля. На выполнение Bean
Validation не влияет.

### `@Parameter`

Обрабатывается springdoc. Описывает path/query-параметр или скрывает внутренний аргумент. Реальное
связывание `page`, `size` и `id` по-прежнему выполняет Spring MVC.

## Граница этапа

На этапе 14 не добавлялись:

- новые бизнес-endpoints;
- `GET /api/orders/{id}`;
- изменение описания заказа;
- новые роли или правила владельца;
- миграции или изменение таблиц;
- клиентская сортировка;
- unit-тесты сервисов из этапа 15;
- Docker и Docker Compose из этапа 16.

## Практическое задание

1. Запустите приложение и откройте `http://localhost:8080/swagger-ui.html`.
2. Найдите `POST /api/orders`.
3. Сопоставьте каждый элемент Swagger UI с кодом:
   - группу — с `@Tag`;
   - краткое название — с `@Operation`;
   - тело запроса — с `CreateOrderRequest`;
   - успешную схему — с `OrderResponse`;
   - `400` и `401` — с `@ApiResponses` и `ApiErrorResponse`;
   - замок — с `@SecurityRequirement` и `bearerAuth`.
4. Объясните, какие из этих аннотаций влияют только на документацию, а какие также обрабатывает
   Spring MVC во время реального запроса.

## Контрольные вопросы

1. Чем OpenAPI-документ отличается от Swagger UI?
2. Как springdoc узнаёт пути, HTTP-методы и типы ответов без отдельного контроллера документации?
3. Что создаёт `@SecurityScheme`, а что делает `@SecurityRequirement`?
4. Почему эти две аннотации не заменяют `JwtAuthenticationFilter` и `SecurityConfig`?
5. Почему Swagger UI и `/v3/api-docs` разрешены без JWT, но `/api/auth/me` остаётся защищённым?
6. Почему в поле `Authorize` нужно вставлять только token, а не строку `Bearer <token>`?
7. Зачем аргумент `Authentication` скрыт через `@Parameter(hidden = true)`?
8. Как стандартные Bean Validation-аннотации попадают в OpenAPI-схему?
9. Почему лимит пароля 72 байта нельзя честно описать как `maxLength = 72`?
10. Зачем для `page` и `size` явно задан `Integer.class`?
11. Почему ошибки контроллера нужно описывать явно, хотя существует `ApiExceptionHandler`?
12. Что проверяет `OpenApiIntegrationTest`, чего не доказывает ручной запуск Swagger UI?
