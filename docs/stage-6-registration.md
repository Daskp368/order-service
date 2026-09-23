# Этап 6. Регистрация пользователя

## Результат

Реализован публичный endpoint:

```http
POST /api/auth/register
```

Он проверяет входные данные, приводит username к нижнему регистру, хеширует пароль BCrypt, всегда назначает роль `USER`, сохраняет пользователя в PostgreSQL и возвращает `201 Created`.

Полный путь запроса теперь выглядит так:

```text
HTTP POST /api/auth/register
          ↓
Spring SecurityFilterChain
          ↓
AuthController + Bean Validation
          ↓
AuthService + транзакция
          ↓
UserRepository
          ↓
Hibernate
          ↓
PostgreSQL
```

## Добавленные классы

| Класс | Ответственность |
|---|---|
| `RegisterRequest` | Принимает и проверяет JSON с username и паролем |
| `UserResponse` | Определяет безопасный JSON ответа без пароля и хеша |
| `AuthController` | Связывает HTTP-запрос с методом Java |
| `AuthService` | Выполняет правила регистрации |
| `SecurityConfig` | Открывает регистрацию и создаёт BCrypt encoder |
| `UsernameAlreadyExistsException` | Обозначает конфликт занятого username |
| `MaxUtf8Bytes` | Пользовательская аннотация проверки числа байтов |
| `MaxUtf8BytesValidator` | Реализует проверку длины строки в UTF-8 |
| `MinCodePoints` | Пользовательская аннотация минимального числа Unicode code points |
| `MinCodePointsValidator` | Корректно считает Unicode-символы, включая emoji |
| `ApiExceptionHandler` | Безопасно превращает ошибку валидации в `400` |

## Зачем нужны DTO

Входной JSON не преобразуется прямо в Entity `User`. Для него создан отдельный DTO:

```java
public class RegisterRequest {
    private String username;
    private String password;
}
```

DTO — Data Transfer Object, объект передачи данных. Он описывает данные на границе API.

Разделение важно по нескольким причинам:

- у `User` нет поля с исходным паролем, только `passwordHash`;
- клиент не может назначить себе роль через DTO;
- правила проверки HTTP-запроса не смешиваются с JPA Entity;
- изменение таблицы не обязано автоматически менять JSON API.

`RegisterRequest` имеет публичный конструктор без параметров и сеттеры. Jackson сначала создаёт объект, затем записывает в него значения из JSON.

Ответ представлен другим DTO:

```java
public class UserResponse {
    private final UUID id;
    private final String username;
    private final Role role;
}
```

В нём физически нет ни исходного пароля, ни хеша. Поэтому Jackson не сможет случайно включить их в успешный JSON-ответ.

## Как запускается валидация

Метод контроллера принимает запрос так:

```java
public ResponseEntity<UserResponse> register(
        @Valid @RequestBody RegisterRequest request)
```

- `@RequestBody` просит Spring прочитать тело HTTP-запроса и с помощью Jackson создать `RegisterRequest`;
- `@Valid` запускает Bean Validation для созданного DTO;
- если хотя бы одно ограничение нарушено, метод контроллера и сервис не вызываются;
- корректный объект передаётся в `AuthService`.

Ограничения username:

```java
@NotBlank
@Size(min = 3, max = 50)
@Pattern(regexp = "[a-zA-Z0-9_]+")
```

- `@NotBlank` отклоняет `null`, пустую строку и строку только из пробелов;
- `@Size` проверяет длину от 3 до 50 символов;
- `@Pattern` разрешает латинские буквы, цифры и `_`.

Заглавные буквы во входном DTO допустимы. После валидации сервис приводит username к нижнему регистру. PostgreSQL получает уже нормализованное значение, соответствующее ограничению миграции `^[a-z0-9_]+$`.

Пробелы намеренно не удаляются через `trim()`. Значение `" user "` считается ошибочным запросом, а не неявно исправляется сервером.

## Почему пароль ограничен байтами

Для пароля используются:

```java
@NotBlank
@MinCodePoints(8)
@MaxUtf8Bytes(72)
```

Обычный `@Size(min = 8)` здесь недостаточно точен. Java хранит строки в UTF-16, а `String.length()` считает 16-битные code units. Один emoji `😀` занимает два таких элемента, поэтому четыре emoji дали бы `length() == 8`, хотя Unicode code points всего четыре.

`MinCodePointsValidator` использует:

```java
value.codePointCount(0, value.length()) >= minimumCodePoints
```

Так минимальная граница означает восемь Unicode code points, а не восемь внутренних элементов UTF-16.

BCrypt принимает не более 72 байт. Число символов и число байт не всегда совпадают:

```text
"a" → 1 байт в UTF-8
"я" → 2 байта в UTF-8
```

Поэтому 37 букв `я` занимают 74 байта, хотя строка содержит только 37 букв. Обычная проверка `@Size(max = 72)` не заметила бы проблему, а BCrypt отклонил бы пароль во время хеширования.

Пользовательские аннотации связаны со своими валидаторами через `@Constraint`. Например:

```java
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
```

- `@Constraint` сообщает Bean Validation, какой класс выполняет проверку;
- `@Target` задаёт места, где разрешена аннотация;
- `@Retention(RUNTIME)` сохраняет её во время работы приложения;
- `message`, `groups` и `payload` являются стандартной частью контракта validation-аннотации;
- `value = 72` передаётся валидатору через метод `initialize()`.

Проверка выполняется так:

```java
value.getBytes(StandardCharsets.UTF_8).length <= maximumBytes
```

Для `null` валидатор возвращает `true`: обязанность проверить наличие значения уже принадлежит `@NotBlank`. Каждая аннотация отвечает за одно правило.

Пароль никогда не обрезается. Незаметное обрезание сделало бы разные длинные пароли эквивалентными.

## Контроллер и HTTP

Класс контроллера начинается с двух аннотаций:

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
}
```

- `@RestController` регистрирует класс как Spring Bean и сообщает, что возвращаемые объекты нужно сериализовать в тело HTTP-ответа;
- `@RequestMapping` задаёт общий префикс всех методов контроллера.

Регистрация связана с конкретным HTTP-методом и путём:

```java
@PostMapping("/register")
```

Spring объединяет два пути и получает `POST /api/auth/register`.

Контроллер не хеширует пароль и не работает с базой. Он передаёт корректный DTO сервису и формирует HTTP-ответ:

```java
return ResponseEntity.status(HttpStatus.CREATED).body(response);
```

`ResponseEntity` позволяет явно задать статус `201 Created` и тело типа `UserResponse`.

## Сервис и транзакция

`@Service` делает `AuthService` Spring Bean слоя бизнес-логики. Зависимости передаются через конструктор:

```java
public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder)
```

Spring находит подходящие Bean и передаёт их сам. Поля объявлены `final`, поэтому после создания сервиса зависимости нельзя заменить.

Метод регистрации отмечен `@Transactional`:

```java
@Transactional
public UserResponse register(RegisterRequest request)
```

При обычном HTTP-запросе Spring открывает транзакцию перед методом. Если метод завершается успешно, транзакция фиксируется через `commit`. Если наружу выходит runtime-исключение, выполняется `rollback`.

Последовательность метода:

1. `toLowerCase(Locale.ROOT)` нормализует username независимо от языка операционной системы.
2. `existsByUsername()` заранее проверяет занятость имени.
3. `passwordEncoder.encode()` создаёт BCrypt-хеш.
4. `new User(..., Role.USER)` создаёт Entity с серверной ролью.
5. `saveAndFlush()` отправляет `INSERT` в PostgreSQL.
6. Сохранённая Entity вручную преобразуется в `UserResponse`.

## `PasswordEncoder` и BCrypt

В сервис внедряется интерфейс:

```java
private final PasswordEncoder passwordEncoder;
```

Конкретную реализацию создаёт конфигурация:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

- `@Configuration` обозначает класс с настройками Spring;
- `@Bean` регистрирует возвращённый объект в Spring Context;
- `PasswordEncoder` задаёт операции `encode()` и `matches()`;
- `BCryptPasswordEncoder` выполняет эти операции с алгоритмом BCrypt.

BCrypt добавляет случайную соль. Поэтому два хеша одного пароля обычно различаются. Проверять пароль сравнением строк хешей нельзя; используется:

```java
passwordEncoder.matches(rawPassword, storedHash)
```

Готовый BCrypt-хеш занимает 60 символов и соответствует `VARCHAR(60)` и `CHECK`-ограничению в PostgreSQL.

## Две проверки уникальности

Ранняя проверка удобна, но одна не защищает от одновременных запросов:

```text
Запрос A: username свободен
Запрос B: username свободен
Запрос A: INSERT выполнен
Запрос B: пытается выполнить INSERT
```

Окончательную защиту даёт ограничение PostgreSQL `uk_users_username`. Один запрос сохранится, второй получит ошибку уникальности.

Используется `saveAndFlush()`, а не только `save()`, чтобы SQL и возможная ошибка произошли внутри блока `try`. Код проверяет имя нарушенного ограничения:

```java
uk_users_username → 409 Conflict
любое другое ограничение → исходная ошибка пробрасывается дальше
```

Нельзя превращать любую `DataIntegrityViolationException` в «username занят»: причиной может быть ошибка роли, длины хеша или другая проблема схемы.

После ошибки PostgreSQL сервис сразу выбрасывает runtime-исключение. Продолжать запросы в той же аварийной транзакции нельзя; она должна откатиться.

## Настройка Spring Security

Регистрация должна быть публичной, но только для одного HTTP-метода. Создан точный matcher:

```java
PathPatternRequestMatcher.withDefaults()
        .matcher(HttpMethod.POST, "/api/auth/register")
```

Он совпадает одновременно по пути и по методу. Например, `GET /api/auth/register` под это правило не подходит.

В `SecurityFilterChain` есть два независимых решения:

```java
csrf.ignoringRequestMatchers(REGISTRATION_REQUEST)
authorize.requestMatchers(REGISTRATION_REQUEST).permitAll()
```

- `permitAll()` разрешает запрос без вошедшего пользователя;
- CSRF-настройка разрешает этот POST без CSRF-токена.

`permitAll()` не отключает CSRF автоматически. Это разные проверки.

Остальные запросы пока требуют аутентификацию через правило `anyRequest().authenticated()`. HTTP Basic, форма входа и JWT на этом этапе не добавлены. Окончательная stateless-конфигурация появится на этапе JWT.

Отдельно разрешён `DispatcherType.ERROR`. Это не открывает клиенту защищённые endpoint'ы: правило относится к внутренней повторной обработке уже возникшей ошибки. Без него Spring Security мог бы перехватить переход на `/error` и замаскировать исходный `500 Internal Server Error` ответом `403 Forbidden`.

## Обработка ошибок на этом этапе

Занятый username обозначается собственным runtime-исключением:

```java
@ResponseStatus(HttpStatus.CONFLICT)
public class UsernameAlreadyExistsException
```

`@ResponseStatus` связывает исключение с HTTP-статусом `409 Conflict`.

Для ошибок Bean Validation добавлен минимальный `ApiExceptionHandler`:

```java
@RestControllerAdvice
@ExceptionHandler(MethodArgumentNotValidException.class)
```

`@RestControllerAdvice` действует на все REST-контроллеры. `@ExceptionHandler` выбирает конкретный тип ошибки. Сейчас обработчик возвращает пустой `400 Bad Request` и намеренно не выводит объект исключения в лог: внутри него находится отклонённое значение поля, которым может быть исходный пароль.

На этапе 7 этот же класс будет расширен и начнёт возвращать согласованный JSON с `timestamp`, `status`, `error`, `message`, `path` и `fieldErrors`.

## Интеграционные тесты

`AuthRegistrationIntegrationTest` использует:

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
```

- `@SpringBootTest` загружает полный Spring Context, включая Security, Controller, Service, JPA и PostgreSQL;
- `@AutoConfigureMockMvc` создаёт `MockMvc` для HTTP-запросов без запуска отдельного сетевого порта;
- `@Transactional` оборачивает каждый тест в транзакцию, которая затем откатывается.

В отличие от `@DataJpaTest` предыдущего этапа, здесь проверяется весь путь запроса, а не только Repository.

Успешный запрос отправляется без пользователя и без CSRF-токена. Полученный `201` одновременно доказывает, что оба правила Security настроены правильно.

Тесты проверяют:

- ответ `201`, нормализацию username и роль `USER`;
- успешное принятие граничного пароля ровно в 72 байта;
- невозможность повысить роль, даже если клиент добавил `"role": "ADMIN"` в JSON;
- отсутствие `password` и `passwordHash` в JSON;
- сохранение 60-символьного BCrypt-хеша и работу `matches()`;
- `409` для повторного username с другим регистром;
- `400` для короткого username, пробела, короткого и пустого пароля;
- `400` для четырёх emoji, которые занимают восемь элементов UTF-16, но являются четырьмя code points;
- `400` для 37 русских букв, занимающих 74 байта UTF-8.
- сохранение исходного статуса `500` при внутреннем `ERROR`-dispatch вместо его замены на `403`.

Перед чтением сохранённого пользователя тест вызывает:

```java
entityManager.flush();
entityManager.clear();
```

`flush()` синхронизирует изменения с PostgreSQL, а `clear()` очищает persistence context. Следующий поиск действительно восстанавливает Entity из базы, а не возвращает тот же Java-объект из кэша первого уровня.

Параметризованный тест выполняется пять раз с разными входными значениями. Поэтому Maven сообщает 10 выполненных тестов в этом классе, хотя в исходнике видно шесть тестовых методов.

Предупреждение Mockito о динамической загрузке Java agent остаётся предупреждением среды Java 23 и не означает падение тестов. Его настройка запланирована на общем этапе тестирования.

## Ручная проверка

Запустить приложение:

```bash
./mvnw spring-boot:run
```

В другом окне терминала отправить запрос:

```bash
curl -i \
  -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"Learning_User","password":"strong-password"}'
```

Ожидается статус `201` и ответ:

```json
{
  "id": "<UUID>",
  "username": "learning_user",
  "role": "USER"
}
```

Повторный запрос с `LEARNING_USER` вернёт `409 Conflict`.

В отличие от тестов, ручной запрос фиксирует транзакцию через `commit`, поэтому созданный пользователь останется в PostgreSQL.

## Контрольные вопросы

1. Почему для запроса и ответа используются DTO, а не Entity `User`?
2. Чем `@RequestBody` отличается от `@Valid`?
3. Почему username разрешает заглавные буквы в DTO, но в базе хранится строчными?
4. Почему для пароля недостаточно `@Size(max = 72)`?
5. Зачем сервису одновременно `existsByUsername()` и ограничение `UNIQUE` в PostgreSQL?
6. Почему здесь используется `saveAndFlush()`, а не только `save()`?
7. Чем `permitAll()` отличается от исключения CSRF?
8. Почему пароль проверяют через `matches()`, а не сравнением двух хешей?
