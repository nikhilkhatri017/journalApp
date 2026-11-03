# JournalApp — Spring Boot Journaling REST API

A Java-based journaling backend built with **Spring Boot, Spring Security, MongoDB and Redis**. The application supports user registration, authenticated journal-entry management, role-protected administrative operations, weather lookup with Redis caching, and a scheduled cache refresh. It also contains an optional sentiment-summary email workflow.

> **Security clarification:** Although the Maven project description says “E2EE Journal App”, the source code supplied in this repository does **not** implement end-to-end encryption. Journal entry `title` and `content` are stored as ordinary MongoDB fields. The application uses HTTP Basic authentication and BCrypt password hashing. Do not present this project as an implemented E2EE system unless you add and verify client-side encryption and key management.

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [Features actually implemented](#2-features-actually-implemented)
3. [Technology stack](#3-technology-stack)
4. [Architecture and request flow](#4-architecture-and-request-flow)
5. [Data model and MongoDB design](#5-data-model-and-mongodb-design)
6. [Security and authentication](#6-security-and-authentication)
7. [REST API reference](#7-rest-api-reference)
8. [Service-by-service explanation](#8-service-by-service-explanation)
9. [Redis caching and weather integration](#9-redis-caching-and-weather-integration)
10. [Sentiment analysis workflow](#10-sentiment-analysis-workflow)
11. [Transactions and data consistency](#11-transactions-and-data-consistency)
12. [Testing](#12-testing)
13. [Project structure](#13-project-structure)
14. [Setup and execution](#14-setup-and-execution)
15. [Important implementation limitations and improvements](#15-important-implementation-limitations-and-improvements)
16. [Interview revision guide](#16-interview-revision-guide)

---

## 1. Project overview

JournalApp is a RESTful backend for a personal journaling application. A user can register, authenticate, create journal entries, retrieve their own entries, update them, and delete them.

The backend is organised using common Spring application layers:

```text
Client (Postman / frontend)
          |
          | HTTP request + Basic Authentication, where required
          v
Spring Security filter chain
          |
          v
Spring MVC DispatcherServlet
          |
          v
Controller layer
          |
          v
Service layer
          |
          +--------------------+
          |                    |
          v                    v
Repository layer          External services
          |               (weather API / SMTP)
          v
MongoDB

WeatherService <----> Redis cache
```

The main application class is `JournalApplication`. It starts Spring Boot and enables scheduling through `@EnableScheduling`.

**Base URL:** the source configuration is intended to use port `8081` and context path `/journal`, but those settings are indented under `spring.mail.server` in the committed YAML. Verify/fix the YAML before relying on that URL. If corrected as application server settings, the intended base URL is:

```text
http://localhost:8081/journal
```

---

## 2. Features actually implemented

| Feature | Implementation |
|---|---|
| User registration | `POST /public/create-user` |
| Password hashing | BCrypt through Spring Security's `PasswordEncoder` |
| Authentication | HTTP Basic authentication |
| Role-based authorization | `/admin/**` requires `ADMIN` role |
| Journal CRUD | Create, list, fetch by ID, update and delete |
| User ownership check | Journal controller checks the authenticated user's journal-entry references |
| MongoDB persistence | Spring Data MongoDB repositories |
| MongoDB transaction manager | `MongoTransactionManager` configuration |
| Weather lookup | External weather API called through `RestTemplate` |
| Weather caching | Redis with a requested 300-second TTL |
| Configuration cache | MongoDB configuration documents loaded into an in-memory map |
| Scheduled cache refresh | Reloads application cache every 10 minutes |
| Email integration | `JavaMailSender`-based email service |
| Sentiment summary | Code calculates the most frequent journal sentiment in the last seven days; its scheduled annotation is currently commented out |
| Automated tests | Spring context test and service/security-related tests, several of which are disabled |

### What is not implemented

- No encryption/decryption code is present in the supplied Java source.
- No JWT authentication filter is active; the JWT filter line is commented out.
- No frontend application is included in this source tree.
- No complete sentiment-analysis model or NLP classifier is present. The scheduler aggregates `Sentiment` values already attached to journal entries.
- Kafka is listed as a dependency, but no Kafka producer, consumer, listener or topic configuration appears in the supplied source files.

---

## 3. Technology stack

| Technology | How this project uses it |
|---|---|
| Java | Application language |
| Spring Boot 2.7.0 | Application bootstrap and auto-configuration |
| Spring MVC / Starter Web | REST controllers and HTTP request handling |
| Spring Security | HTTP Basic authentication and URL authorization |
| Spring Data MongoDB | MongoDB repositories and document mapping |
| MongoDB | Stores users, journal entries and application configuration |
| Spring Data Redis | Redis connection and key-value operations |
| Redis | Caches weather API responses |
| Spring Boot Starter Mail | Sends email through SMTP using `JavaMailSender` |
| RestTemplate | Calls the external weather API |
| Jackson | Converts JSON and maps API responses |
| Lombok | Generates getters, setters, constructors and logging boilerplate |
| Maven | Dependency management and build |
| JUnit 5 / Spring Test / Mockito | Automated testing |
| GitHub Actions | Build workflow in `.github/workflows/build.yml` |

### Version detail to remember

The Maven parent is Spring Boot **2.7.0**. The POM sets `<java.version>1.8</java.version>`, but the Maven compiler plugin explicitly sets `<source>15</source>` and `<target>15</target>`. These settings conflict. Check the JDK used by the original build and standardise the project on one supported Java version before changing or rebuilding it.

---

## 4. Architecture and request flow

### 4.1 What happens when a request arrives?

For a protected endpoint such as `GET /journal`:

1. The client sends an HTTP request with an `Authorization: Basic ...` header.
2. Spring Security's filter chain extracts and validates the credentials.
3. `UserDetailsServiceImpl` loads the user by username from MongoDB.
4. Spring Security checks the supplied password against the stored BCrypt hash.
5. The configured URL rules check whether the authenticated user is allowed to access the endpoint.
6. Spring MVC routes the request to `JournalEntryController`.
7. The controller reads the authenticated username from `SecurityContextHolder`.
8. The service/repository layer retrieves or modifies data.
9. The controller returns an HTTP response.

### 4.2 Controller, service and repository responsibilities

**Controller**
- Receives HTTP requests.
- Reads path variables and request bodies.
- Obtains the authenticated principal.
- Returns `ResponseEntity` and HTTP status codes.

**Service**
- Implements application operations.
- Coordinates repositories and external services.
- Applies business logic such as password hashing, journal ownership updates, and weather caching.

**Repository**
- Provides database access.
- `UserRepository`, `JournalEntryRepository` and `ConfigJournalAppRepository` extend `MongoRepository`.

### 4.3 Dependency injection

The project uses field injection with `@Autowired`. Spring creates and manages the components (beans) and injects their dependencies.

For example, `JournalEntryController` receives `JournalEntryService` and `UserService`; the service receives repository dependencies.

A useful future refactor is constructor injection, because it makes dependencies explicit and simplifies unit testing.

---

## 5. Data model and MongoDB design

The application maps Java classes to MongoDB collections using `@Document`.

### 5.1 `User` — collection: `users`

Fields in the source:

| Field | Java type | Meaning |
|---|---|---|
| `id` | `ObjectId` | MongoDB document ID |
| `userName` | `String` | Username; annotated with a unique index |
| `email` | `String` | User email |
| `sentimentAnalysis` | `boolean` | Whether the user is opted in to the sentiment-summary workflow |
| `password` | `String` | BCrypt-encoded password after registration |
| `journalEntries` | `List<JournalEntry>` | MongoDB references to the user's journal entries using `@DBRef` |
| `roles` | `List<String>` | Roles such as `USER` and `ADMIN` |

Important annotations:

- `@Document(collection = "users")`: maps this class to the `users` collection.
- `@Id`: marks the MongoDB identifier.
- `@Indexed(unique = true)`: asks MongoDB to enforce unique usernames through an index (index creation is enabled in YAML).
- `@DBRef`: stores references to journal-entry documents rather than embedding the full entry objects.
- Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`: generate common boilerplate.

### 5.2 `JournalEntry` — collection: `journal_entries`

| Field | Java type | Meaning |
|---|---|---|
| `id` | `ObjectId` | Entry ID |
| `title` | `String` | Journal title |
| `content` | `String` | Journal body |
| `date` | `LocalDateTime` | Entry timestamp |
| `sentiment` | `Sentiment` | Optional sentiment enum value |

The `Sentiment` enum contains:

```java
HAPPY, SAD, NEUTRAL, ANXIOUS, ANGRY
```

When a journal entry is saved through `saveEntry(entry, username)`, the service sets its date to `LocalDateTime.now()`.

### 5.3 `ConfigJournalAppEntity` — collection: `config_journal_app`

Contains:

- `key`
- `value`

`AppCache` reads all documents from this collection at startup and puts them into a `Map<String, String>`. The `WEATHER_API` key is used to obtain the weather API URL template.

### 5.4 Relationship between users and journal entries

Conceptually:

```text
User
 |
 | journalEntries: List<JournalEntry> (@DBRef)
 |
 +------> JournalEntry 1
 +------> JournalEntry 2
 +------> JournalEntry 3
```

The application maintains the relationship from the `User` document to journal-entry documents. The journal service saves an entry and then adds the saved entry to the user's `journalEntries` list.

**MongoDB concept:** unlike a relational foreign key, a MongoDB DBRef is a reference to another document. It does not automatically provide all the referential-integrity guarantees of a relational database.

---

## 6. Security and authentication

### 6.1 Authentication mechanism: HTTP Basic

`SpringSecurity` extends `WebSecurityConfigurerAdapter` and configures HTTP Basic authentication.

The route rules are:

| Route pattern | Access |
|---|---|
| `/journal/**` | Authenticated user |
| `/user/**` | Authenticated user |
| `/admin/**` | User with `ADMIN` role |
| All other routes | Permitted without authentication |

The configuration also sets:

```java
SessionCreationPolicy.STATELESS
```

This means Spring Security does not create an HTTP session to maintain the authenticated state. The client sends credentials with each request.

CSRF protection is disabled in this API configuration.

### 6.2 How a username is loaded

`UserDetailsServiceImpl` implements Spring Security's `UserDetailsService`.

Its `loadUserByUsername(username)` method:

1. Calls `userRepository.findByUserName(username)`.
2. If a user exists, builds Spring Security's `UserDetails`.
3. Supplies the username, stored password hash and roles.
4. Throws `UsernameNotFoundException` if the user does not exist.

Spring Security uses this information during authentication.

### 6.3 Password hashing with BCrypt

`UserService.saveNewUser()`:

1. Takes the incoming password.
2. Calls `passwordEncoder.encode(...)`.
3. Stores the resulting BCrypt hash.
4. Assigns the `USER` role.
5. Saves the user to MongoDB.

`BCryptPasswordEncoder` is a password-hashing mechanism, not reversible encryption. The original password is not decrypted during login; Spring Security verifies the supplied password against the stored hash.

### 6.4 Creating an administrator

`saveAdmin()` hashes the password and assigns both `USER` and `ADMIN` roles. The admin controller exposes `POST /admin/create-admin-user`, which is itself protected by the `/admin/**` rule.

This creates an operational bootstrapping problem: an administrator must already exist to call the endpoint that creates another administrator. A secure initial-admin provisioning mechanism is needed.

### 6.5 Important security caveats

- HTTP Basic credentials are only safe in transit when the application is served over HTTPS.
- Do not expose the application or MongoDB/Redis services publicly with development credentials.
- The current `GET /admin/all-users` returns `User` entities, which include the password hash field. Password hashes should never be returned in API responses. Use response DTOs that omit passwords and other sensitive fields.
- `POST /public/create-user` returns the `User` object supplied by the caller. Since the same object is password-hashed by the service, this may return the hash. Return a safe response DTO instead.
- `UserController.updateUser()` passes the existing user to `saveNewUser()`, which hashes the supplied password again and resets roles to `USER`. This can unintentionally remove an admin role. Separate profile update from password change.
- The code uses `SecurityContextHolder` to get the authenticated username. The ownership check should be performed consistently in the service layer too, not only in controller code.
- This repository does not implement E2EE. BCrypt protects passwords; it does not encrypt journal content.

---

## 7. REST API reference

The paths below are controller mappings. Add the configured context path (if corrected) as a prefix when calling them.

### Public endpoints

| Method | Path | Purpose | Authentication |
|---|---|---|---|
| `GET` | `/public/health-check` | Returns `"OK"` | Not required |
| `POST` | `/public/create-user` | Registers a user | Not required |

Example registration request:

```http
POST /public/create-user
Content-Type: application/json
```

```json
{
  "userName": "nikhil",
  "email": "nikhil@example.com",
  "password": "choose-a-strong-password",
  "sentimentAnalysis": false
}
```

The service hashes the password and assigns the `USER` role. The controller currently returns the `User` entity; this should be changed to a response DTO that never contains a password hash.

### Journal endpoints — authenticated

| Method | Path | Purpose | Typical response |
|---|---|---|---|
| `GET` | `/journal` | List the authenticated user's entries | `200 OK`, or `404 NOT_FOUND` when empty |
| `POST` | `/journal` | Create an entry for the authenticated user | `201 CREATED` |
| `GET` | `/journal/id/{id}` | Fetch an entry if it belongs to the authenticated user | `200 OK` or `404 NOT_FOUND` |
| `PUT` | `/journal/id/{id}` | Update title/content if the entry belongs to the user | `200 OK` when updated |
| `DELETE` | `/journal/id/{myId}` | Delete an entry belonging to the user | `204 NO_CONTENT` or `404 NOT_FOUND` |

Example create-entry request:

```http
POST /journal
Authorization: Basic <base64(username:password)>
Content-Type: application/json
```

```json
{
  "title": "A productive day",
  "content": "Worked on my Spring Boot project and revised MongoDB.",
  "sentiment": "HAPPY"
}
```

The service assigns the date when saving through the user-specific method.

### User endpoints — authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/user` | Returns a greeting and weather-feels-like information for Jaipur |
| `PUT` | `/user` | Updates the authenticated user's username and password |
| `DELETE` | `/user` | Deletes the authenticated user's account by username |

### Admin endpoints — `ADMIN` role required

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/all-users` | Returns all user entities (currently exposes sensitive fields; must be fixed) |
| `POST` | `/admin/create-admin-user` | Creates a user with `USER` and `ADMIN` roles |
| `GET` | `/admin/clear-app-cache` | Reloads the in-memory application configuration cache |

### HTTP status codes used in the controllers

- `200 OK`: request succeeded and a response body is returned.
- `201 CREATED`: resource was created.
- `204 NO_CONTENT`: operation succeeded without a response body.
- `400 BAD_REQUEST`: the journal create controller catches an exception.
- `404 NOT_FOUND`: requested data was not found, or a user's list is empty in the list endpoint.

The current API has inconsistent status handling in a few branches. For example, the update endpoint returns `204 NO_CONTENT` when the entry is not associated with the user. A more appropriate response would normally be `404 NOT_FOUND`.

---

## 8. Service-by-service explanation

### 8.1 `UserService`

Responsible for user-related business operations:

- `saveNewUser(User)`: BCrypt-encodes the password, assigns `USER`, saves, and returns success/failure.
- `saveAdmin(User)`: BCrypt-encodes the password, assigns `USER` and `ADMIN`, then saves.
- `saveUser(User)`: saves an existing user without re-encoding the password.
- `getAll()`: retrieves all users.
- `findById(ObjectId)`: retrieves by MongoDB ID.
- `deleteById(ObjectId)`: deletes by ID.
- `findByUserName(String)`: retrieves by username.

**Important:** `saveNewUser()` catches exceptions and returns `false`; callers should check that result. Some current controllers ignore it.

### 8.2 `JournalEntryService`

Responsible for journal-entry persistence and the relationship with a user.

**Create/save flow:**

```text
saveEntry(entry, username)
       |
       v
Find user by username
       |
       v
Set entry date to current LocalDateTime
       |
       v
Save JournalEntry in MongoDB
       |
       v
Add saved entry to user's journalEntries list
       |
       v
Save updated User document
```

The method is annotated with `@Transactional`. The project defines a MongoDB transaction manager, so transaction support is intended for operations that span the journal-entry and user documents.

**Delete flow:**

1. Find the user.
2. Remove the matching entry reference from the user's list.
3. If it was present, save the updated user.
4. Delete the journal entry document.
5. Return whether an entry was removed.

The service also has an overloaded `saveEntry(JournalEntry)` method that directly saves an entry without linking it to a user. It catches and logs exceptions rather than propagating them.

### 8.3 `UserDetailsServiceImpl`

Connects the application's `User` collection to Spring Security's authentication mechanism. It converts the stored user into a Spring Security `UserDetails` object.

### 8.4 `WeatherService`

- Checks Redis for `weather_of_<city>`.
- If a cached response exists, returns it.
- Otherwise, reads the URL template from `AppCache`.
- Replaces `<apiKey>` and `<city>` placeholders.
- Calls the external API using `RestTemplate`.
- Stores a non-null response in Redis with a requested TTL of 300 seconds.

### 8.5 `RedisService`

Provides generic JSON-based `get` and `set` helper methods around `RedisTemplate`.

- `get(key, Class<T>)` reads a value and attempts to deserialize it into the requested class using Jackson.
- `set(key, value, ttl)` serializes the value to JSON and writes it to Redis with an expiry.

The current implementation catches exceptions and logs them. Review the `RedisTemplate` `set(...)` overload and serializer configuration carefully: `RedisConfig` configures string serializers, while `RedisService` passes multiple arguments to the value operation. Test the actual Redis read/write path before relying on it.

### 8.6 `AppCache`

- Defines the `WEATHER_API` key.
- Uses `@PostConstruct` to load configuration documents from MongoDB after bean construction.
- Stores configuration in an in-memory `HashMap`.
- `init()` can be called again to refresh the map.

### 8.7 `UserScheduler`

Contains two scheduled-task methods:

**Cache refresh — active**

```java
@Scheduled(cron = "0 0/10 * ? * *")
public void clearAppCache()
```

This refreshes `AppCache` every ten minutes.

**Sentiment summary email — currently not scheduled**

The `@Scheduled(cron = "0 0 9 * * SUN")` annotation above `fetchUsersAndSendSAMail()` is commented out. If enabled, the method:

1. Queries users whose email is present and valid and whose `sentimentAnalysis` flag is true.
2. Filters each user's journal entries to those from the previous seven days.
3. Counts non-null sentiment values.
4. Finds the most frequent sentiment.
5. Sends that sentiment to the user by email.

This is aggregation of sentiment labels already stored in journal entries; it is not an ML sentiment-classification pipeline.

### 8.8 `EmailService`

Uses Spring's `JavaMailSender` to send a `SimpleMailMessage` with recipient, subject and body. Exceptions are logged and rethrown as runtime exceptions.

### 8.9 `UserRepositoryImpl`

Uses `MongoTemplate` to build a custom query for users:

- email field exists,
- email is not null or empty,
- email matches a regular expression,
- `sentimentAnalysis` is true.

This is an example of using `MongoTemplate` when a query is more complex than a simple derived repository method.

### 8.10 `QuotesService`

Currently contains only an empty API-key field. No quote retrieval functionality is implemented in this class.

---

## 9. Redis caching and weather integration

This project uses Redis as a cache for weather API responses.

### Cache-aside pattern

The `WeatherService` follows a cache-aside approach:

```text
Request weather for city
        |
        v
Look up "weather_of_<city>" in Redis
        |
   +----+----+
   |         |
  HIT       MISS
   |         |
Return    Call external weather API
cached        |
response      v
           Store response in Redis
           with 300-second TTL
              |
              v
           Return response
```

### Why cache weather data?

- Reduces repeated external API calls.
- Reduces response time for repeated city requests.
- Helps avoid unnecessary API usage.
- The 300-second expiry allows the cached data to refresh periodically.

### Redis concepts to revise

- **Key:** identifier used to retrieve a cached value.
- **Value:** data stored against a key.
- **TTL:** time to live; how long a key remains before expiry.
- **Cache hit:** requested key exists.
- **Cache miss:** requested key does not exist.
- **Cache-aside:** application checks the cache first and loads from the source on a miss.
- **Serialization:** converting an object into a storable format such as JSON.
- **Deserialization:** converting stored JSON back into an object.

### Weather API response mapping

`WeatherResponse` maps part of the external JSON response:

- `current.temperature`
- `current.weather_descriptions`
- `current.feelslike`

`@JsonProperty("weather_descriptions")` maps the snake_case JSON field to the Java camelCase property `weatherDescriptions`.

The user greeting endpoint calls the weather service with the hard-coded city `"Jaipur"` and appends the `feelslike` value to the greeting when a response is available.

---

## 10. Sentiment analysis workflow

The code contains a simple journal-sentiment summary feature.

### Data flow

```text
User has sentimentAnalysis = true
            |
            v
UserRepositoryImpl selects eligible users
            |
            v
UserScheduler reads each user's journal entries
            |
            v
Keep entries from the last seven days
            |
            v
Count each non-null Sentiment enum value
            |
            v
Find the most frequent sentiment
            |
            v
Email the summary to the user
```

The enum values are `HAPPY`, `SAD`, `NEUTRAL`, `ANXIOUS`, and `ANGRY`.

**Important distinction:** the project does not infer sentiment from journal text. The `JournalEntry` already contains a `sentiment` field, and the scheduler counts those labels. A separate NLP model or LLM integration would be required to classify raw journal content.

The scheduler's email method is currently not activated because its `@Scheduled` annotation is commented out. The cache-refresh scheduler is active.

---

## 11. Transactions and data consistency

`TransactionConfig` enables transaction management and exposes a `MongoTransactionManager` based on `MongoDatabaseFactory`.

`JournalEntryService.saveEntry(entry, username)` and `deleteById(id, username)` are annotated with `@Transactional`.

### Why a transaction is useful here

Creating a journal entry involves two writes:

1. Save the journal-entry document.
2. Update the user's list of journal-entry references.

Without a transaction, the first write might succeed while the second fails, leaving inconsistent data. A transaction is intended to make the two writes succeed or roll back together.

### MongoDB transaction requirement

MongoDB multi-document transactions require a replica set or a sharded cluster. A standalone MongoDB server does not support multi-document transactions.

The configured cloud MongoDB URI appears to target a replica-set-capable hosted cluster, but confirm the actual deployment topology and transaction behaviour in the environment where you run the application.

---

## 12. Testing

The repository contains these tests:

| Test class | What it tests | Current state |
|---|---|---|
| `JournalApplicationTests` | Spring application context loads | Active |
| `UserDetailsServiceImplTest` | User lookup is converted into Spring Security `UserDetails` | Active |
| `UserServiceTest` | Repository lookup and user-save examples | Most tests disabled |
| `RedisTests` | Redis set/get example | Disabled |

### Concepts demonstrated

- JUnit 5 `@Test`
- `@SpringBootTest`
- Mockito `@Mock` and `@InjectMocks`
- `MockitoAnnotations`
- `@BeforeEach`
- Parameterized tests with `@ValueSource`, `@CsvSource` and `@ArgumentsSource`
- `@Disabled`

### Run tests

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

The existence of test classes does not mean every feature is covered. In particular, the supplied tests do not provide comprehensive controller tests, journal ownership tests, transaction rollback tests, password-registration tests or weather-cache integration tests.

---

## 13. Project structure

```text
journalApp/
├── .github/
│   └── workflows/
│       └── build.yml
├── src/
│   ├── main/
│   │   ├── java/com/practiceandexperiments/journalappplication/
│   │   │   ├── api/response/
│   │   │   │   └── WeatherResponse.java
│   │   │   ├── cache/
│   │   │   │   └── AppCache.java
│   │   │   ├── config/
│   │   │   │   ├── RedisConfig.java
│   │   │   │   ├── RestTemplateConfig.java
│   │   │   │   ├── SpringSecurity.java
│   │   │   │   └── TransactionConfig.java
│   │   │   ├── constants/
│   │   │   │   └── PlaceHolders.java
│   │   │   ├── controller/
│   │   │   │   ├── AdminController.java
│   │   │   │   ├── JournalEntryController.java
│   │   │   │   ├── PublicController.java
│   │   │   │   └── UserController.java
│   │   │   ├── entity/
│   │   │   │   ├── ConfigJournalAppEntity.java
│   │   │   │   ├── JournalEntry.java
│   │   │   │   └── User.java
│   │   │   ├── enums/
│   │   │   │   └── Sentiment.java
│   │   │   ├── repository/
│   │   │   │   ├── ConfigJournalAppRepository.java
│   │   │   │   ├── JournalEntryRepository.java
│   │   │   │   ├── UserRepository.java
│   │   │   │   └── UserRepositoryImpl.java
│   │   │   ├── scheduler/
│   │   │   │   └── UserScheduler.java
│   │   │   ├── service/
│   │   │   │   ├── EmailService.java
│   │   │   │   ├── JournalEntryService.java
│   │   │   │   ├── QuotesService.java
│   │   │   │   ├── RedisService.java
│   │   │   │   ├── UserDetailsServiceImpl.java
│   │   │   │   ├── UserService.java
│   │   │   │   └── WeatherService.java
│   │   │   └── JournalApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── logback.xml
│   └── test/
│       └── java/com/practiceandexperiments/journalappplication/
│           ├── JournalApplicationTests.java
│           └── service/
│               ├── RedisTests.java
│               ├── UserArgumentsProvider.java
│               ├── UserDetailsServiceImplTest.java
│               └── UserServiceTest.java
├── mvnw
├── mvnw.cmd
└── pom.xml
```

Generated `target/`, IDE `.idea/`, and runtime log files should normally not be committed to source control.

---

## 14. Setup and execution

### Prerequisites

- A JDK matching the Maven compiler source/target configuration.
- MongoDB (the configured application uses a hosted MongoDB URI).
- Redis (the configured application uses a hosted Redis endpoint).
- SMTP credentials if testing email delivery.
- A valid weather API key and a configuration document containing the weather URL template.

### 14.1 Clone the repository

```bash
git clone https://github.com/nikhilkhatri017/journalApp.git
cd journalApp
```

### 14.2 Configure application settings safely

The committed `application.yml` contains credentials and service endpoints. **Do not use the committed credentials.** Rotate any credentials that were ever pushed to a public repository, then move secrets into environment variables or a local, untracked configuration file.

The application needs configuration for:

- MongoDB connection URI.
- Redis host, port and password.
- Weather API key.
- SMTP host, port, username and app password.

The existing YAML should also be corrected: `server.port` and `server.servlet.context-path` are currently nested under `spring.mail.server`, not under the top-level `server` property where Spring Boot expects them.

A safe configuration should use placeholders, for example:

```yaml
server:
  port: ${SERVER_PORT:8081}
  servlet:
    context-path: /journal

spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}
      auto-index-creation: true
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

weather:
  api:
    key: ${WEATHER_API_KEY}
```

This is a configuration template. Confirm the Spring Boot 2.7 property names for Redis in your exact dependency version and supply the matching Redis connection details.

The `AppCache` also expects a `config_journal_app` MongoDB document whose key is `WEATHER_API` and whose value is the weather API URL template containing `<apiKey>` and `<city>` placeholders.

### 14.3 Build

```bash
./mvnw clean package
```

Windows:

```powershell
.\mvnw.cmd clean package
```

### 14.4 Run

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

### 14.5 Test the health endpoint

After fixing the server configuration and starting the application:

```bash
curl http://localhost:8081/journal/public/health-check
```

Expected response:

```text
OK
```

---

## 15. Important implementation limitations and improvements

These are observations from the supplied source code, not claims that the application is production-ready.

### High priority

1. **Remove and rotate exposed secrets.** The supplied `application.yml` contains live-looking MongoDB, Redis and weather API credentials. Assume they are compromised if they were pushed to a public Git repository. Rotate them with their providers, remove them from the current file, and consider cleaning Git history.
2. **Do not claim E2EE.** There is no encryption/decryption implementation in the Java source. Journal content is stored as ordinary fields.
3. **Stop returning entities containing password hashes.** Use DTOs for registration and admin user responses.
4. **Fix configuration YAML indentation.** Move `server.port` and `server.servlet.context-path` to the top level.
5. **Fix user update behaviour.** Updating a username/password currently calls `saveNewUser`, which hashes the password again and resets roles to `USER`.
6. **Protect ownership in the service layer.** Every journal read/update/delete must verify that the entry belongs to the authenticated user.
7. **Validate request bodies.** Add DTOs and Jakarta Bean Validation annotations such as `@NotBlank`, `@Email` and `@Size`.

### Medium priority

8. Add controller and service tests for all journal CRUD operations and ownership checks.
9. Add global exception handling using `@RestControllerAdvice`.
10. Use constructor injection rather than field injection.
11. Add pagination to journal listing instead of returning every entry.
12. Review the Redis serializer and `RedisService.set()` implementation; test cache write/read/expiry behaviour.
13. Add timeouts and error handling to external weather API calls.
14. Avoid hard-coding `"Jaipur"`; accept a validated city or use a user preference.
15. Activate the sentiment email schedule only after testing and confirming user consent, timezone and email behaviour.
16. Avoid loading all users and all journal references into memory for scheduled processing at scale.
17. Align the Java version in `pom.xml` and the Maven compiler plugin.
18. Add API documentation (OpenAPI/Swagger), structured logs, health checks and metrics.
19. Remove generated logs, build output and IDE-specific files from Git tracking.

### If you want to make it genuinely E2EE

A real E2EE design needs more than adding an encryption utility on the server:

- Encrypt journal content in the client before sending it to the backend.
- Keep decryption keys unavailable to the backend.
- Use authenticated encryption (for example, AES-GCM) with a unique nonce per encryption.
- Design secure key generation, storage, backup, recovery and rotation.
- Consider how multiple devices receive access to the same user's data.
- Do not log plaintext, keys or sensitive request bodies.
- Document the threat model and test tampering, wrong-key and recovery scenarios.

---

## 16. Interview revision guide

### 16.1 Explain the project in 45–60 seconds

“I developed a journaling REST API using Java and Spring Boot. It supports user registration, password hashing with BCrypt, HTTP Basic authentication, role-based access for admin routes, and CRUD operations for journal entries stored in MongoDB. I separated the application into controllers, services and repositories. I also integrated Redis caching for an external weather API, used a scheduled task to refresh application configuration, and implemented a sentiment-summary email workflow based on sentiment labels stored with journal entries. The project helped me practise Spring Security, Spring Data MongoDB, dependency injection, REST API design, caching, scheduled tasks and testing.”

If asked about E2EE, be accurate: the repository's description mentions it, but the current source does not implement end-to-end encryption.

### 16.2 Questions and answers to revise

**Q1. What is Spring Boot?**  
Spring Boot simplifies Spring application development through auto-configuration, starter dependencies and embedded servers. It reduces manual setup.

**Q2. What is dependency injection?**  
Dependency injection is a design pattern in which an object receives the dependencies it needs from an external container rather than constructing them itself. Spring's IoC container manages application beans.

**Q3. What is the difference between `@Controller` and `@RestController`?**  
`@Controller` is commonly used for MVC views. `@RestController` combines `@Controller` and `@ResponseBody`, so handler return values are written to the HTTP response body, commonly as JSON.

**Q4. What is `MongoRepository`?**  
It is a Spring Data interface that provides common CRUD and paging/sorting operations for a MongoDB document type. Spring creates the implementation at runtime.

**Q5. What is the difference between `MongoRepository` and `MongoTemplate`?**  
`MongoRepository` is a repository abstraction for common operations and derived queries. `MongoTemplate` provides more direct control over MongoDB queries, updates, aggregation and other operations. This project uses both.

**Q6. What is `UserDetailsService`?**  
It is a Spring Security interface used to load user-specific data by username. The implementation returns a `UserDetails` object containing username, password hash and authorities.

**Q7. How does BCrypt work?**  
BCrypt is a salted, adaptive password-hashing function. It is intentionally computationally expensive. Passwords are verified by matching a raw password against the stored hash; the hash is not decrypted.

**Q8. Authentication vs authorization?**  
Authentication verifies identity. Authorization determines what an authenticated identity is permitted to do.

**Q9. What is HTTP Basic authentication?**  
The client sends a Base64-encoded username/password pair in the `Authorization` header. Base64 is encoding, not encryption, so HTTPS is essential.

**Q10. What does stateless session management mean?**  
The server does not maintain an HTTP session for the client. With this project's HTTP Basic setup, credentials are sent on each request and authenticated by the security filter chain.

**Q11. What is Redis used for here?**  
Redis stores weather API responses under keys such as `weather_of_Jaipur`, allowing the service to return cached data rather than call the external API every time.

**Q12. What is TTL?**  
Time to live is the expiry duration for a cache key. This project requests a 300-second TTL for weather responses.

**Q13. What is cache-aside?**  
The application checks the cache first. On a miss, it reads from the source system, stores the result in the cache, and returns it.

**Q14. What is `@Scheduled`?**  
It marks a method for execution according to a schedule. Scheduling must be enabled, which this application does using `@EnableScheduling`.

**Q15. What does `@PostConstruct` do?**  
It marks a method to run after dependency injection and bean construction. Here it loads configuration documents into the application cache.

**Q16. Why use `@Transactional`?**  
It defines a transaction boundary so a group of database operations can succeed or roll back together. Multi-document MongoDB transactions require a replica set or sharded cluster.

**Q17. What is a DTO?**  
A Data Transfer Object defines the data exposed by an API. DTOs prevent internal persistence models from being returned directly and help avoid leaking sensitive fields such as password hashes.

**Q18. What is the difference between encryption and hashing?**  
Encryption is reversible with the appropriate key. Hashing is designed to be one-way. Password storage should use a password-hashing function such as BCrypt.

**Q19. Does this project implement E2EE?**  
No. The current source does not contain client-side encryption or key-management code. It stores journal title and content as normal MongoDB fields. The project uses BCrypt for passwords, which is a different security function.

**Q20. Does this project use Kafka?**  
Kafka appears as a Maven dependency, but the supplied source has no Kafka producer, consumer, listener or topic configuration. A dependency alone does not mean the feature is implemented.

---

## Author

**Nikhil Khatri**  
Java | Spring Boot | Python | Backend Development | AI Integration

- GitHub: [nikhilkhatri017](https://github.com/nikhilkhatri017)
- Repository: [journalApp](https://github.com/nikhilkhatri017/journalApp)
- LinkedIn: [Nikhil Khatri](https://www.linkedin.com/in/nikhilkhatri017/)

---

### Personal project-revision checklist

Use this checklist while opening the source code:

- [ ] Run the application locally with safe, rotated credentials.
- [ ] Explain the main class and `@SpringBootApplication`.
- [ ] Trace registration from `PublicController` to `UserService` to `UserRepository`.
- [ ] Explain BCrypt password hashing and HTTP Basic authentication.
- [ ] Explain how `UserDetailsServiceImpl` loads a user.
- [ ] Explain the security URL rules and roles.
- [ ] Trace journal creation from controller through service to MongoDB.
- [ ] Explain the `User` ↔ `JournalEntry` DBRef relationship.
- [ ] Trace journal update and delete, including ownership checks.
- [ ] Explain `MongoTemplate` and the custom user query.
- [ ] Explain `AppCache`, `@PostConstruct` and the ten-minute scheduled refresh.
- [ ] Trace the weather cache hit/miss flow and 300-second TTL.
- [ ] Explain the `RestTemplate` call and `WeatherResponse` JSON mapping.
- [ ] Explain the sentiment aggregation and why its email schedule is currently inactive.
- [ ] Explain `MongoTransactionManager` and replica-set requirements.
- [ ] Run the tests and identify which are disabled.
- [ ] Be able to explain honestly why the current source is not E2EE.

