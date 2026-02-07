# Standard Java Project

Standard Java project template with Spring Framework, JPA, and PostgreSQL.

## Prerequisites

- Java 21 or higher
- Maven 3.8.1 or higher
- PostgreSQL 12 or higher

## Project Structure

```
src/
├── main/
│   ├── java/ru/myapp/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers
│   │   ├── service/         # Business logic
│   │   ├── repository/      # Data access layer
│   │   ├── model/           # Entity classes
│   │   └── dto/             # Data transfer objects
│   └── resources/
│       └── application.properties
└── test/
    └── java/ru/myapp/
```

## Build

```bash
mvn clean package
```

## Run

```bash
mvn spring-boot:run
```

## Dependencies

- Spring Framework 6.1.7
- Spring Data JPA 3.3.0
- Hibernate 6.5.1
- PostgreSQL Driver 42.7.3
- Lombok 1.18.32
- Jackson 2.17.1
- Logback 1.5.6
