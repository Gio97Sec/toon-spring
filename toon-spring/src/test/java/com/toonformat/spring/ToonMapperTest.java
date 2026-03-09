package com.toonformat.spring;

import com.toonformat.spring.annotation.ToonField;
import com.toonformat.spring.annotation.ToonIgnore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ToonMapperTest {

    private final ToonMapper mapper = new ToonMapper();

    // ── Test POJOs ──────────────────────────────────────────────────────

    public static class User {
        public int id;
        public String name;
        public boolean active;

        public User() {}
        public User(int id, String name, boolean active) {
            this.id = id;
            this.name = name;
            this.active = active;
        }
    }

    public static class Product {
        public String sku;
        public int qty;
        public double price;

        public Product() {}
        public Product(String sku, int qty, double price) {
            this.sku = sku;
            this.qty = qty;
            this.price = price;
        }
    }

    public static class Address {
        public String city;
        public String country;

        public Address() {}
        public Address(String city, String country) {
            this.city = city;
            this.country = country;
        }
    }

    public static class Person {
        public String name;
        public int age;
        public Address address;

        public Person() {}
        public Person(String name, int age, Address address) {
            this.name = name;
            this.age = age;
            this.address = address;
        }
    }

    public static class AnnotatedUser {
        @ToonField("user_name")
        public String userName;

        @ToonIgnore
        public String secret;

        public int score;

        public AnnotatedUser() {}
        public AnnotatedUser(String userName, String secret, int score) {
            this.userName = userName;
            this.secret = secret;
            this.score = score;
        }
    }

    public static class Order {
        public String orderId;
        public List<Product> products;

        public Order() {}
        public Order(String orderId, List<Product> products) {
            this.orderId = orderId;
            this.products = products;
        }
    }

    // ── Serialization Tests ─────────────────────────────────────────────

    @Nested
    class Serialization {

        @Test
        void simpleObject() {
            User user = new User(123, "Ada Lovelace", true);
            String toon = mapper.writeValueAsString(user);

            assertThat(toon).contains("id: 123");
            assertThat(toon).contains("name: Ada Lovelace");
            assertThat(toon).contains("active: true");
        }

        @Test
        void nestedObject() {
            Person person = new Person("Alice", 30, new Address("Rome", "Italy"));
            String toon = mapper.writeValueAsString(person);

            assertThat(toon).contains("name: Alice");
            assertThat(toon).contains("age: 30");
            assertThat(toon).contains("address:");
            assertThat(toon).contains("  city: Rome");
            assertThat(toon).contains("  country: Italy");
        }

        @Test
        void primitiveArray() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tags", List.of("admin", "developer", "ops"));

            String toon = mapper.writeValueAsString(data);
            assertThat(toon).isEqualTo("tags[3]: admin,developer,ops");
        }

        @Test
        void tabularArray() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("products", List.of(
                    new Product("A1", 2, 9.99),
                    new Product("B2", 1, 14.5)
            ));

            String toon = mapper.writeValueAsString(data);
            assertThat(toon).contains("products[2]{sku,qty,price}:");
            assertThat(toon).contains("  A1,2,9.99");
            assertThat(toon).contains("  B2,1,14.5");
        }

        @Test
        void nullValues() {
            assertThat(mapper.writeValueAsString(null)).isEqualTo("null");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", null);
            assertThat(mapper.writeValueAsString(data)).isEqualTo("value: null");
        }

        @Test
        void emptyArray() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", List.of());
            assertThat(mapper.writeValueAsString(data)).isEqualTo("items[0]:");
        }

        @Test
        void annotatedFields() {
            AnnotatedUser user = new AnnotatedUser("Ada", "s3cret", 42);
            String toon = mapper.writeValueAsString(user);

            assertThat(toon).contains("user_name: Ada");
            assertThat(toon).contains("score: 42");
            assertThat(toon).doesNotContain("secret");
            assertThat(toon).doesNotContain("s3cret");
        }

        @Test
        void mapSerialization() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("version", 1);
            data.put("is_active", true);
            data.put("label", "test");

            String toon = mapper.writeValueAsString(data);
            assertThat(toon).isEqualTo("version: 1\nis_active: true\nlabel: test");
        }

        @Test
        void stringQuoting() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("plain", "hello world");
            data.put("quoted", "true");          // looks like boolean
            data.put("num_str", "42");           // looks like number
            data.put("colon", "key:value");      // contains colon
            data.put("empty", "");               // empty string

            String toon = mapper.writeValueAsString(data);
            assertThat(toon).contains("plain: hello world");
            assertThat(toon).contains("quoted: \"true\"");
            assertThat(toon).contains("num_str: \"42\"");
            assertThat(toon).contains("colon: \"key:value\"");
            assertThat(toon).contains("empty: \"\"");
        }

        @Test
        void specialNumbers() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nan", Double.NaN);
            data.put("inf", Double.POSITIVE_INFINITY);
            data.put("decimal", 0.000001);

            String toon = mapper.writeValueAsString(data);
            assertThat(toon).contains("nan: null");
            assertThat(toon).contains("inf: null");
            assertThat(toon).contains("decimal: 0.000001");
        }

        @Test
        void pipeDelimiter() {
            ToonMapper pipeMapper = new ToonMapper(new ToonOptions().setDelimiter(ToonDelimiter.PIPE));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tags", List.of("a", "b", "c"));

            String toon = pipeMapper.writeValueAsString(data);
            assertThat(toon).isEqualTo("tags[3|]: a|b|c");
        }

        @Test
        void lengthMarker() {
            ToonMapper markerMapper = new ToonMapper(new ToonOptions().setLengthMarker(true));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", List.of("x", "y"));

            String toon = markerMapper.writeValueAsString(data);
            assertThat(toon).isEqualTo("items[#2]: x,y");
        }

        @Test
        void objectWithNestedList() {
            Order order = new Order("ORD-001", List.of(
                    new Product("A1", 2, 9.99),
                    new Product("B2", 1, 14.5)
            ));

            String toon = mapper.writeValueAsString(order);
            assertThat(toon).contains("orderId: ORD-001");
            assertThat(toon).contains("products[2]{sku,qty,price}:");
            assertThat(toon).contains("  A1,2,9.99");
        }

        @Test
        void rootArray() {
            List<String> items = List.of("alpha", "beta", "gamma");
            String toon = mapper.writeValueAsString(items);
            assertThat(toon).isEqualTo("[3]: alpha,beta,gamma");
        }

        @Test
        void rootTabularArray() {
            List<Product> products = List.of(
                    new Product("A1", 2, 9.99),
                    new Product("B2", 1, 14.5)
            );
            String toon = mapper.writeValueAsString(products);
            assertThat(toon).contains("[2]{sku,qty,price}:");
            assertThat(toon).contains("  A1,2,9.99");
            assertThat(toon).contains("  B2,1,14.5");
        }
    }

    // ── Deserialization Tests ────────────────────────────────────────────

    @Nested
    class Deserialization {

        @Test
        void simpleObject() {
            String toon = "id: 123\nname: Ada Lovelace\nactive: true";
            User user = mapper.readValue(toon, User.class);

            assertThat(user.id).isEqualTo(123);
            assertThat(user.name).isEqualTo("Ada Lovelace");
            assertThat(user.active).isTrue();
        }

        @Test
        void nestedObject() {
            String toon = """
                    name: Alice
                    age: 30
                    address:
                      city: Rome
                      country: Italy""";
            Person person = mapper.readValue(toon, Person.class);

            assertThat(person.name).isEqualTo("Alice");
            assertThat(person.age).isEqualTo(30);
            assertThat(person.address).isNotNull();
            assertThat(person.address.city).isEqualTo("Rome");
            assertThat(person.address.country).isEqualTo("Italy");
        }

        @Test
        void primitiveArray() {
            String toon = "tags[3]: admin,developer,ops";
            Map<String, Object> result = mapper.readMap(toon);

            assertThat(result).containsKey("tags");
            @SuppressWarnings("unchecked")
            List<Object> tags = (List<Object>) result.get("tags");
            assertThat(tags).containsExactly("admin", "developer", "ops");
        }

        @Test
        void tabularArray() {
            String toon = """
                    products[2]{sku,qty,price}:
                      A1,2,9.99
                      B2,1,14.5""";
            Map<String, Object> result = mapper.readMap(toon);

            assertThat(result).containsKey("products");
            List<?> products = (List<?>) result.get("products");
            assertThat(products).hasSize(2);

            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) products.get(0);
            assertThat(first.get("sku")).isEqualTo("A1");
            assertThat(first.get("qty")).isEqualTo(2);
            assertThat(first.get("price")).isEqualTo(9.99);
        }

        @Test
        void tabularToPojoList() {
            String toon = """
                    [2]{sku,qty,price}:
                      A1,2,9.99
                      B2,1,14.5""";
            List<Product> products = mapper.readList(toon, Product.class);

            assertThat(products).hasSize(2);
            assertThat(products.get(0).sku).isEqualTo("A1");
            assertThat(products.get(0).qty).isEqualTo(2);
            assertThat(products.get(0).price).isEqualTo(9.99);
            assertThat(products.get(1).sku).isEqualTo("B2");
        }

        @Test
        void nullValue() {
            String toon = "name: null";
            Map<String, Object> result = mapper.readMap(toon);
            assertThat(result.get("name")).isNull();
        }

        @Test
        void booleanValues() {
            String toon = "a: true\nb: false";
            Map<String, Object> result = mapper.readMap(toon);
            assertThat(result.get("a")).isEqualTo(true);
            assertThat(result.get("b")).isEqualTo(false);
        }

        @Test
        void quotedStrings() {
            String toon = """
                    label: "true"
                    num: "42"
                    empty: ""
                    escaped: "line1\\nline2"
                    """;
            Map<String, Object> result = mapper.readMap(toon);

            assertThat(result.get("label")).isEqualTo("true");
            assertThat(result.get("num")).isEqualTo("42");
            assertThat(result.get("empty")).isEqualTo("");
            assertThat(result.get("escaped")).isEqualTo("line1\nline2");
        }

        @Test
        void annotatedDeserialization() {
            String toon = "user_name: Ada\nscore: 42";
            AnnotatedUser user = mapper.readValue(toon, AnnotatedUser.class);

            assertThat(user.userName).isEqualTo("Ada");
            assertThat(user.score).isEqualTo(42);
            assertThat(user.secret).isNull(); // ignored
        }

        @Test
        void emptyInput() {
            assertThat(mapper.readTree(null)).isNull();
            assertThat(mapper.readTree("")).isNull();
            assertThat(mapper.readTree("   ")).isNull();
        }

        @Test
        void listFormat() {
            String toon = """
                    items[3]:
                      - alpha
                      - beta
                      - gamma""";
            Map<String, Object> result = mapper.readMap(toon);
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) result.get("items");
            assertThat(items).containsExactly("alpha", "beta", "gamma");
        }

        @Test
        void listOfObjects() {
            String toon = """
                    users[2]:
                      - id: 1
                        name: Alice
                      - id: 2
                        name: Bob""";
            Map<String, Object> result = mapper.readMap(toon);
            List<?> users = (List<?>) result.get("users");
            assertThat(users).hasSize(2);

            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) users.get(0);
            assertThat(first.get("id")).isEqualTo(1);
            assertThat(first.get("name")).isEqualTo("Alice");
        }
    }

    // ── Round-trip Tests ────────────────────────────────────────────────

    @Nested
    class RoundTrip {

        @Test
        void simpleObject() {
            User original = new User(1, "Ada", true);
            String toon = mapper.writeValueAsString(original);
            User restored = mapper.readValue(toon, User.class);

            assertThat(restored.id).isEqualTo(original.id);
            assertThat(restored.name).isEqualTo(original.name);
            assertThat(restored.active).isEqualTo(original.active);
        }

        @Test
        void nestedObject() {
            Person original = new Person("Bob", 25, new Address("Paris", "France"));
            String toon = mapper.writeValueAsString(original);
            Person restored = mapper.readValue(toon, Person.class);

            assertThat(restored.name).isEqualTo("Bob");
            assertThat(restored.age).isEqualTo(25);
            assertThat(restored.address.city).isEqualTo("Paris");
            assertThat(restored.address.country).isEqualTo("France");
        }

        @Test
        void mapWithPrimitiveArray() {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("tags", List.of("a", "b", "c"));
            original.put("count", 3);

            String toon = mapper.writeValueAsString(original);
            Map<String, Object> restored = mapper.readMap(toon);

            assertThat(restored.get("count")).isEqualTo(3);
            @SuppressWarnings("unchecked")
            List<Object> tags = (List<Object>) restored.get("tags");
            assertThat(tags).containsExactly("a", "b", "c");
        }

        @Test
        void annotatedRoundTrip() {
            AnnotatedUser original = new AnnotatedUser("Charlie", "hidden", 99);
            String toon = mapper.writeValueAsString(original);
            AnnotatedUser restored = mapper.readValue(toon, AnnotatedUser.class);

            assertThat(restored.userName).isEqualTo("Charlie");
            assertThat(restored.score).isEqualTo(99);
            assertThat(restored.secret).isNull(); // ignored field
        }

        @Test
        void mapRoundTrip() {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("str", "hello");
            original.put("num", 42);
            original.put("bool", true);
            original.put("nil", null);

            String toon = mapper.writeValueAsString(original);
            Map<String, Object> restored = mapper.readMap(toon);

            assertThat(restored.get("str")).isEqualTo("hello");
            assertThat(restored.get("num")).isEqualTo(42);
            assertThat(restored.get("bool")).isEqualTo(true);
            assertThat(restored.get("nil")).isNull();
        }
    }
}
