# Информационная безопасность
## Лабораторная работа №1

### Стек
- Java 17 / Spring Boot 4
- Spring Security
- Hibernate (Spring Data JPA)
- PostgreSQL
- JWT (библиотека jjwt)



### Описание API

`POST /auth/register`: метод для регистрации пользователя (принимает логин и пароль). Логин — от 3 до 50 символов (латиница, цифры, `_`), пароль — от 8 до 72 символов.
```json
{
  "username": "student1",
  "password": "Str0ngPass!"
}
```
Ответы: `201` — пользователь создан, `400` — некорректные данные, `409` — логин уже занят.

`POST /auth/login`: метод для аутентификации пользователя (принимает логин и пароль). Возвращает JWT-токен.
```json
{
  "username": "student1",
  "password": "Str0ngPass!"
}
```
Ответ `200`:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9..."
}
```
При неверном логине или пароле — `401`.

`GET /api/data`: метод для получения списка пользователей. Доступ только у аутентифицированных пользователей, токен передаётся в заголовке:
```
Authorization: Bearer <token>
```
Ответы: `200` — список пользователей (без паролей), `401` — токен отсутствует, подделан или истёк.

### Описание реализованных мер защиты

- От **SQLi** код защищен с помощью ORM Hibernate (Spring Data JPA). Запросы генерируются по имени метода репозитория и выполняются как параметризованные (`select ... from users where username = ?`), конкатенация строк для SQL нигде не используется. Строка вида `' OR '1'='1' --` передаётся как значение параметра и не может изменить запрос.

```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
```

- От **XSS** защищен с помощью экранирования пользовательских данных, которые возвращаются в ответах API

```java
public static UserResponse from(User user) {
    return new UserResponse(user.getId(), HtmlUtils.htmlEscape(user.getUsername()), user.getCreatedAt());
}
```

Дополнительно логин проверяется при регистрации (строка с `<script>` отклоняется с кодом `400`), а в ответы добавляются заголовки `Content-Security-Policy: default-src 'none'` и `X-Content-Type-Options: nosniff`.

```java
@Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "only latin letters, digits and underscore are allowed")
String username,
```

- От **Broken Authentication**: пароли хранятся только в виде хэша **bcrypt**

```java
User user = new User(request.username(), passwordEncoder.encode(request.password()));
```

При успешном входе выдаётся **JWT-токен**, подписанный HMAC-SHA512 секретным ключом из `JWT_SECRET`, со сроком жизни `JWT_EXPIRATION` (15 минут). При неверном логине и при неверном пароле возвращается одинаковая ошибка `401`, чтобы нельзя было подобрать существующие логины.

```java
return Jwts.builder()
        .subject(username)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expirationMs))
        .signWith(key)
        .compact();
```

Middleware `JwtAuthFilter` проверяет токен на всех защищённых эндпоинтах: подпись и срок действия проверяются методом `parseSignedClaims`, при ошибке возвращается `401`.

```java
try {
    username = jwtService.extractUsername(header.substring(BEARER_PREFIX.length()));
} catch (JwtException | IllegalArgumentException e) {
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    return;
}
```

Открыты только `/auth/**`, все остальные эндпоинты требуют аутентификации:

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/**", "/error").permitAll()
        .anyRequest().authenticated())
.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
```

### Отчеты из pipeline

Pipeline (`.github/workflows/ci.yml`) запускается при каждом push и pull request и состоит из трёх этапов:
- **Build** — сборка проекта (`mvn package`);
- **SAST** — статический анализ кода SpotBugs с плагином FindSecBugs;
- **SCA** — проверка зависимостей на известные уязвимости OWASP Dependency-Check (сборка падает при CVSS ≥ 7).

Первый запуск Dependency-Check обнаружил критические уязвимости в зависимостях (Spring Framework, Spring Security, Tomcat, PostgreSQL JDBC, Jackson). Они были исправлены обновлением до Spring Boot 4.1.1, Tomcat 11.0.26 и Jackson 2.21.7 / 3.1.7.

### Dependency-check (до исправления):
<img width="1901" height="896" alt="image" src="https://github.com/user-attachments/assets/2494c2bd-93b0-41cf-b871-19684385013d" />


### Dependency-check (после исправления):
<img width="1902" height="908" alt="image" src="https://github.com/user-attachments/assets/9b0c87d2-b029-4987-81d1-f8b98ddc67ca" />


### Spotbugs:

<img width="617" height="846" alt="image" src="https://github.com/user-attachments/assets/93bdd731-5fcd-498e-b721-5d8d4ce2f92a" />


### Последний запуск пайплайна

https://github.com/F4d4/infosec1/actions/runs/36706870917
