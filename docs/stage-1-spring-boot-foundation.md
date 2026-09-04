# Этап 1. Основа проекта Spring Boot

## Результат

Создано минимальное приложение `order-service`, которое собирается, проходит стандартный тест и запускает встроенный HTTP-сервер.

## Параметры проекта

| Параметр | Значение |
|---|---|
| Java | 17 — целевая версия проекта |
| Spring Boot | 3.5.16 |
| Система сборки | Maven Wrapper |
| Упаковка | Jar |
| Group | `com.example` |
| Artifact | `order-service` |
| Базовый package | `com.example.orderservice` |

На компьютере проект запускается на Java 23, но компилируется в байткод Java 17. Это позволяет запускать собранное приложение на Java 17 и более новых совместимых версиях.

## Точка входа

```java
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

Сигнатура `public static void main(String[] args)` распознаётся JVM как точка входа:

- `public` — JVM может вызвать метод;
- `static` — объект класса создавать не требуется;
- `void` — метод не возвращает результат JVM;
- `String[] args` — аргументы командной строки.

`SpringApplication.run(...)` не просто запускает переданный класс как обычный метод. Он использует `OrderServiceApplication.class` как основной источник конфигурации, создаёт Spring Context, выполняет автоконфигурацию и запускает встроенный веб-сервер. Метод возвращает созданный `ConfigurableApplicationContext`.

## `@SpringBootApplication`

Эта составная аннотация включает три основных механизма:

```text
@SpringBootApplication
├── @SpringBootConfiguration
├── @EnableAutoConfiguration
└── @ComponentScan
```

- `@SpringBootConfiguration` обозначает главный класс конфигурации;
- `@EnableAutoConfiguration` настраивает приложение по зависимостям и properties;
- `@ComponentScan` ищет Spring-компоненты в текущем package и его подпакетах.

Если убрать `@SpringBootApplication`, Spring Boot не получит основной источник конфигурации и стандартный запуск приложения перестанет работать ожидаемым образом.

## Maven и `pom.xml`

`pom.xml` описывает:

- координаты и версию проекта;
- целевую версию Java;
- зависимости;
- плагины сборки;
- родительскую конфигурацию Spring Boot.

Starter — это согласованный набор зависимостей для определённой задачи. Например, `spring-boot-starter-web` подключает Spring MVC, JSON-преобразование и встроенный Tomcat.

Tomcat находится не внутри основного класса Spring Boot. Он приходит как зависимость веб-starter и запускается в том же процессе Java, поэтому отдельная установка Tomcat не нужна.

## Maven Wrapper

Команда:

```bash
./mvnw test
```

использует Maven Wrapper. Глобально устанавливать Maven не требуется: wrapper загружает согласованную версию Maven и запускает её для проекта.

Запуск приложения:

```bash
./mvnw spring-boot:run
```

Остановка выполняется сочетанием `Ctrl+C`.

## Стандартный тест

```java
@SpringBootTest
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

`@SpringBootTest` просит Spring Boot создать почти полный контекст приложения. Пустой метод полезен: тест считается успешным только в том случае, если контекст создался без исключений.

`@Test` сообщает JUnit, что метод является тестовым сценарием.

## Первоначальное поведение Spring Security

До собственной настройки безопасности Spring Boot создаёт временного пользователя и выводит сгенерированный пароль в лог запуска.

- запрос без аутентификации получает `401 Unauthorized`;
- запрос с временными правильными данными до появления контроллера получает `404 Not Found`.

Это различие показывает, что сначала запрос проходит проверку безопасности, а затем Spring MVC ищет подходящий endpoint.

## Контрольные вопросы

1. Почему метод `main` должен быть `static`?
2. Что создаёт `SpringApplication.run(...)`?
3. Какие три механизма объединяет `@SpringBootApplication`?
4. Откуда в приложении появляется Tomcat?
5. Почему пустой метод `contextLoads()` всё равно является полезным тестом?
