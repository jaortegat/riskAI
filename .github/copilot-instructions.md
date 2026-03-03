# RiskAI Game Project

## Project Overview
A web-based RiskAI board game implementation using modern Java technologies, with an MCP (Model Context Protocol) server powered by Spring AI that exposes game operations as AI-callable tools.

## Technology Stack
- Java 25
- Spring Boot 4.0 (Spring Framework 7, Hibernate 7, Jackson 3)
- Spring AI 1.0+ with MCP Server support
- Spring WebSocket for real-time updates
- Spring Data JPA with H2 (in-memory)
- Spring Security 7
- Thymeleaf for server-side rendering
- Bootstrap 5 for UI
- Lombok 1.18.42

## MCP Server (Spring AI)
The project includes an MCP server that exposes game functionality as tools for AI agents and LLM-based clients via the Model Context Protocol.

### Dependencies
- `spring-ai-starter-mcp-server-webmvc` — MCP server over HTTP using Spring MVC (SSE transport)

### MCP Configuration
Configure in `application.yml`:
```yaml
spring:
  ai:
    mcp:
      server:
        name: riskai-mcp-server
        version: 1.0.0
        type: SYNC
        sse-message-endpoint: /mcp/messages
```

### Exposing Tools
Use `@Tool` and `@ToolParam` annotations from Spring AI to expose service methods as MCP-callable tools:
```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Tool(description = "Get the current state of a game")
public GameStateDTO getGameState(@ToolParam(description = "The game ID") Long gameId) { ... }
```

Register tool providers as beans via `ToolCallbackProvider`:
```java
@Bean
public ToolCallbackProvider gameTools(GameMCPService gameMCPService) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(gameMCPService)
        .build();
}
```

### MCP Project Structure
- `mcp/` - MCP tool service classes annotated with `@Tool`
- `config/` - MCP configuration and tool registration beans

### MCP Coding Conventions
- Create dedicated MCP service classes (e.g., `GameMCPService`) — do not add `@Tool` annotations directly to existing service classes
- Use `@Tool(description = "...")` on public methods to expose them as MCP tools
- Use `@ToolParam(description = "...")` on method parameters for clear tool schemas
- Return DTOs or records from tool methods, never JPA entities
- Keep MCP services thin — delegate business logic to existing service classes
- Use `SYNC` server type with SSE transport for the WebMVC stack

## Key Patterns
- Strategy Pattern for CPU players (Easy, Medium, Hard difficulties)
- Repository Pattern for data access
- DTO Pattern for API responses
- Factory Pattern for CPU strategy selection
- MCP Tool Pattern for AI-accessible game operations

## Running the Project
```bash
mvn spring-boot:run
```
Then navigate to http://localhost:8080

### MCP Server Endpoint
The MCP SSE endpoint is available at:
```
http://localhost:8080/sse
```

## Project Structure
- `cpu/` - CPU player strategies using Strategy pattern
- `config/` - Spring configuration classes (security, async, websocket, map loading, MCP tool registration)
- `controller/` - REST and web controllers
- `dto/` - Data Transfer Objects
- `exception/` - Global exception handling
- `mcp/` - MCP tool services exposing game operations to AI agents
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
- Use `@Tool` / `@ToolParam` annotations for MCP tool definitions
- Keep MCP tool services separate from core business services

## Testing
Run tests with:
```bash
mvn test
```
