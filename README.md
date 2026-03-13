# TOON Spring

Spring Boot library for serialization and deserialization of **TOON** (Token-Oriented Object Notation) format.

Convert Java objects to TOON strings and back, just like Jackson does for JSON.

## What is TOON?

TOON is a compact, human-readable serialization format designed to reduce token usage when sending structured data to Large Language Models. Compared to JSON, TOON can reduce tokens by **30-60%** while preserving the same semantics.

**Specification**: [github.com/toon-format/spec](https://github.com/toon-format/spec) (v3.0)

---

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>io.github.gio97sec</groupId>
    <artifactId>toon-spring</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Basic Usage

```java
ToonMapper mapper = new ToonMapper();

// ── Serialization ────────────────────────────────
User user = new User(123, "Ada Lovelace", true);
String toon = mapper.writeValueAsString(user);
// Output:
// id: 123
// name: Ada Lovelace
// active: true

// ── Deserialization ──────────────────────────────
User restored = mapper.readValue(toon, User.class);
```

---

## Supported Formats

### Simple Objects

```java
Map<String, Object> config = Map.of(
    "version", 1,
    "debug", false,
    "label", "production"
);
mapper.writeValueAsString(config);
```

```
version: 1
debug: false
label: production
```

### Nested Objects

```java
Person person = new Person("Alice", 30, new Address("Rome", "Italy"));
mapper.writeValueAsString(person);
```

```
name: Alice
age: 30
address:
  city: Rome
  country: Italy
```

### Primitive Arrays (Inline Format)

```java
Map<String, Object> data = Map.of("tags", List.of("admin", "developer", "ops"));
mapper.writeValueAsString(data);
```

```
tags[3]: admin,developer,ops
```

### Uniform Object Arrays (Tabular Format)

This is where TOON shines — field names are declared once in the header.

```java
List<Product> products = List.of(
    new Product("A1", 2, 9.99),
    new Product("B2", 1, 14.50)
);
Map<String, Object> data = Map.of("products", products);
mapper.writeValueAsString(data);
```

```
products[2]{sku,qty,price}:
  A1,2,9.99
  B2,1,14.5
```

### Non-Uniform Arrays (List Format)

```
items[3]:
  - alpha
  - beta
  - gamma
```

---

## Annotations

### `@ToonField` — Rename a Field

```java
public class User {
    @ToonField("user_name")
    private String userName;
    private int score;
}
// Output: user_name: Ada
//         score: 42
```

### `@ToonIgnore` — Exclude a Field

```java
public class User {
    private String name;
    @ToonIgnore
    private String passwordHash;
}
// Output: name: Ada
// (passwordHash is excluded)
```

---

## Configuration Options

```java
ToonOptions options = new ToonOptions()
    .setDelimiter(ToonDelimiter.PIPE)     // Delimiter: COMMA (default), TAB, PIPE
    .setIndent(4)                          // Spaces per level (default: 2)
    .setLengthMarker(true)                 // Adds # to count: tags[#3]
    .setStrict(true);                      // Strict mode for validation

ToonMapper mapper = new ToonMapper(options);
```

### Delimiters

```
# COMMA (default)
tags[3]: admin,developer,ops

# PIPE
tags[3|]: admin|developer|ops

# TAB
tags[3	]: admin	developer	ops
```

---

## Spring Boot Integration

### Auto-Configuration

With Spring Boot, simply add the dependency. The following beans are automatically registered:

- `ToonOptions`
- `ToonMapper`
- `ToonHttpMessageConverter` (media type: `text/toon`)

### Controller with TOON Content Type

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping(produces = "text/toon")
    public List<User> getUsers() {
        return userService.findAll();
    }

    @PostMapping(consumes = "text/toon")
    public void createUser(@RequestBody User user) {
        userService.save(user);
    }
}
```

### Manual Configuration

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        ToonMapper mapper = new ToonMapper(
            new ToonOptions().setDelimiter(ToonDelimiter.COMMA)
        );
        converters.add(new ToonHttpMessageConverter(mapper));
    }
}
```

---

## API Reference

### `ToonMapper`

| Method | Description |
|--------|-------------|
| `writeValueAsString(Object)` | Java object → TOON string |
| `readValue(String, Class<T>)` | TOON string → POJO |
| `readList(String, Class<T>)` | TOON string (root array) → `List<T>` |
| `readMap(String)` | TOON string → `Map<String, Object>` |
| `readTree(String)` | TOON string → generic tree (Map/List/primitives) |

### Quoting Rules

Strings are **unquoted** by default. They are quoted only when necessary:

- Empty string → `""`
- Leading/trailing spaces → `" padded "`
- Contains active delimiter → `"a,b"`
- Contains `:` → `"key:value"`
- Looks like a boolean → `"true"`, `"false"`
- Looks like null → `"null"`
- Looks like a number → `"42"`, `"3.14"`
- Contains `"` or `\` → escaped with `\"` and `\\`
- Contains newline → `\n`

### Type Normalization

| Java Type                         | TOON Output                            |
| --------------------------------- | -------------------------------------- |
| `String`                          | Unquoted (or quoted if needed)         |
| `int`, `long`, `double`, ...      | Decimal format, no scientific notation |
| `boolean`                         | `true` / `false`                       |
| `null`                            | `null`                                 |
| `Double.NaN`, `Infinity`          | `null`                                 |
| `BigInteger` (outside safe range) | Quoted string                          |
| `Date`, `Temporal`                | Quoted ISO string                      |
| `Enum`                            | Enum name                              |
| `UUID`                            | UUID string                            |

---

## Project Structure

```
toon-spring/
├── pom.xml
└── src/
    ├── main/java/com/toonformat/spring/
    │   ├── ToonMapper.java                 ← Main entry point
    │   ├── ToonSerializer.java             ← Java → TOON
    │   ├── ToonDeserializer.java           ← TOON → Java
    │   ├── ToonOptions.java                ← Configuration
    │   ├── ToonDelimiter.java              ← Delimiter enum
    │   ├── ToonException.java              ← Custom exception
    │   ├── annotation/
    │   │   ├── ToonField.java              ← Rename field
    │   │   └── ToonIgnore.java             ← Ignore field
    │   └── spring/
    │       ├── ToonAutoConfiguration.java  ← Spring Boot auto-config
    │       └── ToonHttpMessageConverter.java ← HTTP converter
    ├── main/resources/META-INF/spring/
    │   └── ...AutoConfiguration.imports    ← Auto-config registration
    └── test/java/com/toonformat/spring/
        └── ToonMapperTest.java             ← Full test suite
```

---

## JSON vs TOON Comparison

**JSON** (257 tokens):

```json
{
  "users": [
    { "id": 1, "name": "Alice", "role": "admin", "salary": 75000 },
    { "id": 2, "name": "Bob", "role": "user", "salary": 65000 },
    { "id": 3, "name": "Charlie", "role": "user", "salary": 70000 }
  ]
}
```

**TOON** (166 tokens, **-35%**):

```
users[3]{id,name,role,salary}:
  1,Alice,admin,75000
  2,Bob,user,65000
  3,Charlie,user,70000
```

---

## License

MIT License — Copyright (c) 2025 Giovanni Secondo alias Gio97Sec

See [LICENSE](LICENSE) for details.
