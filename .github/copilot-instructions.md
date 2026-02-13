# RiskAI Game Project

## Project Overview
A web-based RiskAI board game implementation using modern Java technologies.

## Technology Stack
- Java 25
- Spring Boot 4.0 (Spring Framework 7, Hibernate 7, Jackson 3)
- Spring WebSocket for real-time updates
- Spring Data JPA with H2 (in-memory)
- Spring Security 7
- Thymeleaf for server-side rendering
- Bootstrap 5 for UI
- Lombok 1.18.42

## Key Patterns
- Strategy Pattern for CPU players (Easy, Medium, Hard difficulties)
- Repository Pattern for data access
- DTO Pattern for API responses
- Factory Pattern for CPU strategy selection

## Running the Project
```bash
mvn spring-boot:run
```
Then navigate to http://localhost:8080

## Project Structure
- `cpu/` - CPU player strategies using Strategy pattern
- `config/` - Spring configuration classes (security, async, websocket, map loading)
- `controller/` - REST and web controllers
- `dto/` - Data Transfer Objects
- `exception/` - Global exception handling
- `model/` - JPA entities (Game, Player, Territory, Continent) and enums (GameMode, GamePhase, GameStatus, PlayerType, PlayerColor, CPUDifficulty)
- `repository/` - Spring Data JPA repositories
- `service/` - Business logic services (GameService, MapService, CPUPlayerService)
- `websocket/` - WebSocket handlers for real-time communication

## Game Modes
- **Classic** - Eliminate all opponents
- **Domination** - Control a target percentage of the map
- **Turn Limit** - Most territories after N turns

## Coding Conventions
- Use Lombok annotations (@Data, @Builder, @RequiredArgsConstructor)
- Use Java 25 features (records, pattern matching, switch expressions)
- Follow RESTful API design for endpoints
- Use DTOs for API responses, not entities directly

## Testing
Run tests with:
```bash
mvn test
```
