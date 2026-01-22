# Titanium Underwriting Project

This is the underwriting service for the Titanium insurance platform. It provides core functionality for underwriting processes, including risk assessment, approval/rejection workflows, and status tracking.

## Project Structure

The project follows a modular architecture with clear separation of concerns:

```
titanium-underwriting/
├── titanium-underwriting-api/          # API layer - DTOs and interfaces
├── titanium-underwriting-application/   # Application layer - business logic
│   └── src/main/java/com/titanium/underwriting/
│       ├── command/                    # Command services
│       └── query/                      # Query services
├── titanium-underwriting-domain/        # Domain layer - core business logic
│   └── src/main/java/com/titanium/underwriting/
│       ├── aggregate/                  # Aggregate roots
│       ├── command/                    # Command definitions
│       ├── event/                      # Event definitions
│       ├── query/                      # Query definitions
│       ├── repository/                 # Repository interfaces
│       └── valueobject/                # Value objects
├── titanium-underwriting-infrastructure/ # Infrastructure layer - persistence and external systems
│   └── src/main/java/com/titanium/underwriting/
│       ├── entity/                     # JPA entities
│       ├── infrastructure/             # Infrastructure components
│       │   └── query/                  # Query handlers
│       ├── mapper/                     # Entity mappers
│       └── repository/                 # Repository implementations
│           └── jpa/                    # JPA repositories
└── titanium-underwriting-web/          # Web layer - REST APIs
    └── src/main/java/com/titanium/underwriting/
        └── controller/                 # REST controllers
```

## Key Features

- **CQRS Architecture**: Command and Query Responsibility Segregation using Axon Framework
- **Event Sourcing**: Audit trail and history tracking through domain events
- **Multi-Tenancy**: Support for multiple tenants with tenant ID propagation
- **MapStruct**: Type-safe entity-DTO conversion
- **RESTful APIs**: Modern REST interfaces for client integration

## Technologies Used

- Java 21
- Spring Boot 3.2.x
- Axon Framework 4.1.x
- MapStruct 1.5.x
- Hibernate/JPA
- Maven 3.9.x

## Getting Started

### Prerequisites

- JDK 21 or higher
- Maven 3.9 or higher
- PostgreSQL 14 or higher

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/titanium-insurance/titanium-underwriting.git
   ```

2. Navigate to the project directory:
   ```bash
   cd titanium-underwriting
   ```

3. Build the project:
   ```bash
   mvn clean install
   ```

4. Run the application:
   ```bash
   cd titanium-underwriting-web
   mvn spring-boot:run
   ```

### API Documentation

Once the application is running, you can access the Swagger UI at:
```
http://localhost:8080/swagger-ui.html
```

## Usage

### Creating an Underwriting

```bash
curl -X POST "http://localhost:8080/api/underwritings" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant123" \
  -d '{
    "policyId": "policy123",
    "customerId": "customer123",
    "amount": 10000.00,
    "underwritingType": "INDIVIDUAL",
    "requestBy": "user123"
  }'
```

### Getting an Underwriting

```bash
curl -X GET "http://localhost:8080/api/underwritings/{underwritingId}" \
  -H "X-Tenant-ID: tenant123"
```

### Processing an Underwriting

```bash
curl -X PUT "http://localhost:8080/api/underwritings/{underwritingId}/underwrite" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant123" \
  -d '{
    "amount": 10000.00,
    "reason": "Standard approval",
    "underwriteBy": "user123"
  }'
```

## Architecture

### Command Query Responsibility Segregation (CQRS)

The application follows CQRS pattern, separating command (write) and query (read) operations:

- **Commands**: Modify state and publish events
- **Queries**: Read data without modifying state
- **Event Sourcing**: Track state changes through events

### Domain-Driven Design (DDD)

The application is structured around DDD principles:

- **Aggregates**: Root entities that manage state and enforce invariants
- **Value Objects**: Immutable objects that represent descriptive aspects
- **Repositories**: Interfaces for persistence operations
- **Domain Events**: Events that represent significant changes

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
