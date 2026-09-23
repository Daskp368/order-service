# Этап 11. Операции администратора

## Результат

Приложение получило три endpoint, доступных только пользователю с ролью `ADMIN`:

```http
GET /api/users
GET /api/orders/all
PUT /api/orders/{id}
Authorization: Bearer <token>
```

Администратор видит всех пользователей и все заказы, а также меняет статус любого существующего
заказа. Обычный пользователь получает `403 Forbidden`, потому что его личность подтверждена, но роли
недостаточно. Анонимный запрос по-прежнему получает `401 Unauthorized`.

Также добавлен безопасный способ создания первого администратора из переменных окружения.

## Добавленные и изменённые классы и файлы

| Статус | Класс или файл | Что сделано |
|---|---|---|
| Новый | `UpdateOrderStatusRequest` | Принимает и проверяет единственное изменяемое поле `status` |
| Новый | `UserController` | Обрабатывает `GET /api/users` |
| Новый | `UserService` | Получает пользователей и преобразует их в безопасный `UserResponse` |
| Новый | `AdminInitializer` | Создаёт первого ADMIN при старте, если администратора ещё нет |
| Изменён | `OrderController` | Добавлены общий список заказов и обновление статуса |
| Изменён | `OrderService` | Добавлены чтение всех заказов и транзакционное изменение статуса |
| Изменён | `Order` | Добавлен доменный метод `changeStatus` |
| Изменён | `OrderRepository` | Добавлена выборка всех заказов по убыванию времени создания |
| Изменён | `UserRepository` | Добавлены поиск роли и упорядоченный список пользователей |
| Изменён | `SecurityConfig` | Три административных маршрута ограничены через `hasRole("ADMIN")` |
| Изменён | `application.properties` | Добавлены настройки первого администратора |
| Изменён | `application-test.properties` | Инициализатор отключён в тестовом профиле |
| Изменён | `.env.example` | Добавлены имена переменных `ADMIN_USERNAME` и `ADMIN_PASSWORD` |
| Новый | `AdminOperationsIntegrationTest` | Проверяет HTTP-контракт, роли и изменение статуса |
| Новый | `AdminInitializerTest` | Проверяет создание ADMIN, BCrypt, повторный запуск и конфликты |
| Изменён | `README.md` | Зафиксирован результат этапа и локальная настройка ADMIN |

Новые зависимости, миграции и таблицы на этом этапе не понадобились.

## Матрица доступа

| Endpoint | anonymous | USER | ADMIN |
|---|---:|---:|---:|
| `GET /api/users` | `401` | `403` | `200` |
| `GET /api/orders/all` | `401` | `403` | `200` |
| `PUT /api/orders/{id}` | `401` | `403` | `200`, `400` или `404` по содержимому запроса |
| `GET /api/orders` | `401` | собственные заказы | собственные заказы |
| `POST /api/orders` | `401` | разрешено | разрешено |

Роль `ADMIN` не превращает `GET /api/orders` в общий список. Этот endpoint всегда использует владельца
текущего JWT. Для общей выборки существует отдельный маршрут `/api/orders/all`.

## Role и GrantedAuthority

В доменной модели роль хранится как enum:

```java
public enum Role {
    USER,
    ADMIN
}
```

Spring Security принимает решения не по доменному enum напрямую, а по объектам `GrantedAuthority`.
При загрузке пользователя `DatabaseUserDetailsService` преобразует роль следующим образом:

```text
Role.USER  → ROLE_USER
Role.ADMIN → ROLE_ADMIN
```

В `SecurityConfig` используется:

```java
hasRole("ADMIN")
```

`hasRole` самостоятельно добавляет префикс `ROLE_`, поэтому фактически ищет authority `ROLE_ADMIN`.
Эквивалентная явная запись выглядела бы так:

```java
hasAuthority("ROLE_ADMIN")
```

Передавать `ROLE_ADMIN` в `hasRole` нельзя: тогда Spring добавил бы префикс повторно.

## Почему правило роли находится в SecurityConfig

Три новые операции полностью административные. У них нет допустимого сценария для `USER`, поэтому
доступ к ним ограничивается до вызова контроллера:

```text
JWT подтверждён
        ↓
Authentication содержит ROLE_USER или ROLE_ADMIN
        ↓
SecurityFilterChain проверяет hasRole("ADMIN")
        ↓
ADMIN → Controller
USER  → RestAccessDeniedHandler → 403
```

В сервисе остаются бизнес-проверки, которые не зависят от способа вызова:

- существует ли заказ;
- входит ли статус в `OrderStatus`;
- какие поля заказа разрешено изменять.

На этапе 12 проверка владельца заказа будет бизнес-правилом сервиса, потому что один endpoint удаления
будет доступен и владельцу, и ADMIN. На этапе 11 дублировать `hasRole` через `@PreAuthorize` не требуется.

## Список всех пользователей

Запрос:

```http
GET /api/users
Authorization: Bearer <admin-token>
```

Ответ:

```json
[
  {
    "id": "0dbdfd3c-439c-46ab-ab78-a7cbe86d540f",
    "username": "user_1",
    "role": "USER"
  }
]
```

`UserService` возвращает существующий `UserResponse`. Entity `User` наружу не передаётся, поэтому в JSON
нет ни исходного пароля, ни `passwordHash`. Пользователи упорядочены по username, чтобы результат был
предсказуемым.

Путь запроса:

```text
GET /api/users + Bearer JWT
          ↓
JwtAuthenticationFilter проверяет JWT
          ↓
DatabaseUserDetailsService загружает актуальную роль
          ↓
SecurityFilterChain требует ROLE_ADMIN
          ↓
UserController.getAllUsers()
          ↓
UserService.getAllUsers()
          ↓
UserRepository.findAllByOrderByUsernameAsc()
          ↓
User Entity → UserResponse → 200 OK
```

## Список всех заказов

Запрос:

```http
GET /api/orders/all
Authorization: Bearer <admin-token>
```

Ответ этапа 11 — обычный JSON-массив:

```json
[
  {
    "id": "2715fc32-0bc4-435d-ad2d-d09a7f19c607",
    "userId": "0dbdfd3c-439c-46ab-ab78-a7cbe86d540f",
    "description": "Новый заказ",
    "status": "CREATED",
    "createdAt": "2026-09-11T15:30:00"
  }
]
```

`OrderRepository.findAllByOrderByCreatedAtDesc()` получает заказы всех владельцев и сортирует их от новых
к старым в PostgreSQL. Затем `OrderService` повторно использует тот же маппинг в `OrderResponse`, что и
для личного списка.

Пагинация намеренно не добавлена: она относится к этапу 13. Тогда оба списка заказов будут возвращать
объект с `content`, `page`, `size`, `totalElements` и `totalPages`.

## Изменение статуса заказа

Запрос:

```http
PUT /api/orders/2715fc32-0bc4-435d-ad2d-d09a7f19c607
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{
  "status": "IN_PROGRESS"
}
```

Успешный ответ содержит актуальное представление заказа:

```json
{
  "id": "2715fc32-0bc4-435d-ad2d-d09a7f19c607",
  "userId": "0dbdfd3c-439c-46ab-ab78-a7cbe86d540f",
  "description": "Новый заказ",
  "status": "IN_PROGRESS",
  "createdAt": "2026-09-11T15:30:00"
}
```

`UpdateOrderStatusRequest` содержит только `status`. Поэтому endpoint не меняет:

- `id`;
- владельца;
- описание;
- дату создания.

Допустимы все значения существующего enum: `CREATED`, `IN_PROGRESS` и `COMPLETED`. В первой версии нет
графа переходов: ADMIN может установить любое из этих значений, включая возврат к `CREATED`.

Ошибки:

| Ситуация | Ответ |
|---|---:|
| Поле `status` отсутствует или равно `null` | `400 Bad Request` с `fieldErrors` |
| Передано неизвестное значение, например `CANCELLED` | `400 Bad Request` |
| В URL передан некорректный UUID | `400 Bad Request` |
| Заказ с таким UUID отсутствует | `404 Not Found` |
| Запрос отправляет USER | `403 Forbidden` до контроллера |

## JPA dirty checking

`OrderService.updateOrderStatus()` помечен `@Transactional`. Внутри транзакции репозиторий возвращает
управляемую JPA-сущность:

```text
OrderRepository.findById(id)
          ↓
managed Order
          ↓
order.changeStatus(newStatus)
          ↓
завершение транзакции
          ↓
Hibernate обнаруживает изменение и выполняет UPDATE
```

Повторный вызов `orderRepository.save(order)` здесь не нужен. Hibernate сравнивает состояние управляемой
сущности и формирует SQL при flush/commit. Без транзакционной границы объект после завершения репозиторного
вызова не оставался бы управляемым в рамках всей бизнес-операции.

## Создание первого ADMIN

Публичная регистрация по-прежнему всегда создаёт только `USER`. Роль нельзя передать в JSON регистрации.
Первый администратор создаётся отдельно при запуске приложения.

Локальные переменные:

```properties
ADMIN_USERNAME=local_admin
ADMIN_PASSWORD=<надёжный пароль>
```

`AdminInitializer` реализует `ApplicationRunner`, поэтому Spring Boot вызывает его после создания контекста.
Алгоритм:

1. Проверить через `existsByRole(Role.ADMIN)`, есть ли любой администратор.
2. Если ADMIN уже существует, завершить работу без изменений.
3. Проверить username и password теми же Bean Validation-правилами, что и регистрацию.
4. Нормализовать username в нижний регистр.
5. Убедиться, что username не занят обычным пользователем.
6. Создать BCrypt-хеш.
7. Сохранить нового `User` с ролью `ADMIN`.

Инициализатор не повышает существующего USER до ADMIN и не перезаписывает пароль существующего
администратора. Пароль и хеш не записываются в лог.

В `application-test.properties` задано:

```properties
app.admin.enabled=false
```

Поэтому обычные интеграционные тесты сами создают необходимые фикстуры и не зависят от локальных секретов.

## Новые аннотации и механизмы

### `@PutMapping("/{id}")`

Связывает Java-метод с HTTP `PUT /api/orders/{id}`. Без неё Spring MVC не зарегистрирует маршрут.

### `@PathVariable`

Преобразует часть URL `{id}` в параметр `UUID id`. Некорректная UUID-строка не дойдёт до сервиса и
вернёт `400 Bad Request`.

### `@NotNull`

Запрещает отсутствующий или `null`-статус в `UpdateOrderStatusRequest`. Ограничение начинает работать,
потому что аргумент контроллера помечен `@Valid`.

### `@ConditionalOnProperty`

Регистрирует `AdminInitializer` только когда `app.admin.enabled=true`. Эту аннотацию обрабатывает
автоконфигурационный механизм Spring Boot до создания Bean.

### `ApplicationRunner`

Это интерфейс жизненного цикла Spring Boot. Его метод `run()` выполняется после успешного создания
контекста приложения.

### `@Value`

Передаёт в конструктор инициализатора значения `app.admin.username` и `app.admin.password`, которые в
основной конфигурации связаны с переменными окружения.

### `@Transactional`

Объединяет проверку и создание администратора либо поиск и изменение заказа в одну бизнес-операцию.
Для списков применяется `@Transactional(readOnly = true)`.

## Проверки

`AdminOperationsIntegrationTest` проходит реальный путь Security → Controller → Service → Repository →
PostgreSQL и проверяет:

- безопасный список всех пользователей;
- отсутствие пароля и хеша в JSON;
- заказы разных владельцев в общем списке;
- сортировку заказов от новых к старым;
- все три значения `OrderStatus`;
- неизменность владельца, описания и даты;
- `400` для отсутствующего и неизвестного статуса;
- `400` для некорректного UUID в URL;
- `404` для отсутствующего заказа;
- `403` для USER;
- `401` без JWT;
- отсутствие изменения заказа после запрещённого запроса.

`AdminInitializerTest` проверяет:

- нормализацию username;
- BCrypt-хеширование;
- назначение роли ADMIN;
- отсутствие повторного создания;
- запрет автоматического повышения существующего USER;
- безопасную ошибку конфигурации без раскрытия пароля.

Проверка после реализации:

- `./mvnw -Dtest=AdminOperationsIntegrationTest,AdminInitializerTest test` — 12 тестов, 0 ошибок;
- `./mvnw test` — 55 тестов, 0 ошибок.

## Граница этапа

На этапе 11 не добавлялись:

- `DELETE /api/orders/{id}`;
- `DELETE /api/users/{id}`;
- проверка владельца при удалении;
- пагинация;
- Swagger / OpenAPI;
- Docker;
- редактирование описания заказа;
- `GET /api/orders/{id}`;
- сложные правила перехода статусов.

## Практическое задание

Составьте собственную таблицу доступа для `anonymous`, `USER` и `ADMIN`, не подсматривая в таблицу выше.
Для каждого запрещённого запроса объясните выбор между `401` и `403`.

Затем проследите `PUT /api/orders/{id}` от Bearer-токена до SQL `UPDATE`. Назовите объект, существующий
на каждом шаге: JWT, `UserDetails`, `Authentication`, `UpdateOrderStatusRequest`, управляемый `Order` и
`OrderResponse`.

## Контрольные вопросы

1. Чем доменный `Role.ADMIN` отличается от authority `ROLE_ADMIN`?
2. Почему `hasRole("ADMIN")` ищет именно `ROLE_ADMIN`?
3. В какой момент USER получает `403` и почему метод контроллера при этом не вызывается?
4. Почему анонимный запрос получает `401`, а не `403`?
5. Почему `GET /api/orders` даже для ADMIN возвращает только его собственные заказы?
6. Какие поля может изменить `PUT /api/orders/{id}` и почему DTO не содержит остальные поля?
7. Как `@Valid` и `@NotNull` взаимодействуют при отсутствующем статусе?
8. Что произойдёт при передаче значения `CANCELLED`, которого нет в enum?
9. Почему после `order.changeStatus()` не вызывается `orderRepository.save()`?
10. Зачем `AdminInitializer` сначала проверяет наличие ADMIN, а затем занятость настроенного username?
11. Почему нельзя автоматически повысить существующего USER до ADMIN по совпавшему username?
12. Почему инициализатор отключён в тестовом профиле?
