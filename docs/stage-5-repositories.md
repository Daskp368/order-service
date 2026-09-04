# Этап 5. Репозитории Spring Data JPA

## Результат

Созданы `UserRepository` и `OrderRepository`. Приложение умеет сохранять пользователей и заказы, искать пользователя по username и получать только заказы выбранного владельца в нужном порядке.

Репозитории проверены интеграционными тестами на локальной PostgreSQL. Изменения каждого теста автоматически откатываются.

## Место Repository в приложении

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Spring Data JPA
    ↓
Hibernate
    ↓
PostgreSQL
```

Controller и Service появятся на следующих этапах. Repository отвечает только за доступ к хранимым данным и не должен содержать правила HTTP или бизнес-решения.

## `JpaRepository`

```java
public interface UserRepository extends JpaRepository<User, UUID> {
}
```

Generic-параметры имеют строгий смысл:

- `User` — тип Entity;
- `UUID` — тип поля, отмеченного `@Id`.

Из `JpaRepository` наследуются готовые операции:

| Метод | Назначение |
|---|---|
| `save(entity)` | Сохранить новую или изменённую Entity |
| `findById(id)` | Найти по первичному ключу |
| `findAll()` | Получить все записи |
| `existsById(id)` | Проверить существование |
| `deleteById(id)` | Удалить по идентификатору |
| `saveAndFlush(entity)` | Сохранить и немедленно синхронизировать с БД |

Spring Data во время запуска создаёт объект-реализацию интерфейса на основе `SimpleJpaRepository` и регистрирует его как Spring Bean. Поэтому собственный `UserRepositoryImpl` не нужен.

Аннотация `@Repository` над интерфейсом также не требуется: наследование от Spring Data Repository и сканирование package дают Spring достаточно информации.

## Репозиторий пользователей

```java
Optional<User> findByUsername(String username);

boolean existsByUsername(String username);
```

Spring Data разбирает имена методов и строит запросы по свойствам `User`.

Примерный SQL-смысл:

```sql
SELECT * FROM users WHERE username = ?;
SELECT EXISTS(SELECT 1 FROM users WHERE username = ?);
```

`Optional<User>` обозначает два нормальных результата: пользователь найден или отсутствует. Это заставляет вызывающий код явно обработать отсутствие вместо получения неожиданного `null`.

`existsByUsername` подходит регистрации, когда нужен только ответ «занято ли имя», а не весь объект пользователя.

## Репозиторий заказов

```java
List<Order> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);
```

Имя читается по частям:

```text
findAll               найти все
By                    начало условия
User_Id               пройти Order.user → User.id
OrderByCreatedAtDesc  сортировать по createdAt по убыванию
```

Примерный SQL-смысл:

```sql
SELECT *
FROM orders
WHERE user_id = ?
ORDER BY created_at DESC;
```

Подчёркивание в `User_Id` вручную обозначает границу вложенного свойства. Java-поле называется `user`, а его поле идентификатора — `id`.

Пагинация здесь намеренно не добавлена: `Page` и `Pageable` изучаются на отдельном этапе 13.

## Почему пока нет `@Query`

Для этих простых условий достаточно derived query methods — запросов, полученных из имени метода. Если имя свойства написано неверно, Spring Data обнаружит это при запуске контекста.

`@Query` понадобится только тогда, когда запрос невозможно ясно выразить именем или требуется точный JPQL/SQL. Лишний ручной запрос сейчас увеличил бы код без пользы.

## Тестовый JPA-контекст

```java
@DataJpaTest
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
```

`@DataJpaTest` загружает ограниченную часть Spring Context:

- Entity;
- Hibernate;
- DataSource;
- Flyway;
- JPA-репозитории.

Веб-сервер, контроллеры и вся будущая бизнес-логика не запускаются. Такой тест быстрее полного `@SpringBootTest` и точнее показывает работу слоя данных.

`Replace.NONE` запрещает автоматически заменять настроенный DataSource встроенной базой. Поэтому тесты используют реальную PostgreSQL и проверяют тип `UUID`, внешний ключ и ограничения нашей схемы.

## Внедрение репозитория в тест

```java
@Autowired
UserRepositoryTest(UserRepository userRepository) {
    this.userRepository = userRepository;
}
```

Spring передаёт созданный runtime-объект репозитория в конструктор теста. Сам интерфейс создать через `new UserRepository()` невозможно.

В production-коде тоже будет использоваться внедрение через конструктор. Аннотация `@Autowired` на единственном конструкторе Spring-компонента обычно не обязательна, но тестовый класс сам не является обычным компонентом, поэтому зависимость указана явно.

## `save()` и `saveAndFlush()`

JPA может отложить SQL до конца транзакции. `saveAndFlush()` просит Hibernate сразу синхронизировать текущие изменения с базой.

Это важно в интеграционном тесте: ошибка `NOT NULL`, `FOREIGN KEY` или `CHECK` должна возникнуть во время проверяемой операции, а не после завершения тестового метода.

`flush` не завершает транзакцию и не делает `commit`.

## Откат тестовых данных

Каждый `@DataJpaTest` по умолчанию выполняется в транзакции:

```text
BEGIN
→ подготовка пользователей и заказов
→ вызов Repository
→ assertions
ROLLBACK
```

Даже после `saveAndFlush()` строки исчезают после теста, потому что транзакция откатывается. Это позволяет повторять тесты без ручной очистки базы.

## Что проверяют тесты

`UserRepositoryTest`:

- Hibernate генерирует UUID;
- пользователь действительно записывается в PostgreSQL;
- поиск по существующему username возвращает `Optional` с пользователем;
- неизвестный username возвращает пустой `Optional`;
- `existsByUsername` возвращает правильный результат.

`OrderRepositoryTest`:

- сохраняет двух пользователей и три заказа;
- запрашивает заказы только первого пользователя;
- не возвращает чужой заказ;
- сортирует результат по `createdAt` от нового к старому.

Тестовый password hash — строка нужной длины для проверки слоя хранения. Настоящее BCrypt-хеширование будет реализовано на этапе регистрации.

## Контрольные вопросы

1. Кто создаёт реализацию `UserRepository`?
2. Что означают типы `User` и `UUID` в `JpaRepository<User, UUID>`?
3. Почему `findByUsername` возвращает `Optional<User>`?
4. Как Spring понимает запрос из имени `findAllByUser_IdOrderByCreatedAtDesc`?
5. Чем `saveAndFlush()` отличается от окончательного `commit`?
6. Почему тестовые строки не остаются в PostgreSQL?
