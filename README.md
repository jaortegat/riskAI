# 🌍 RiskAI - World Domination

A modern web-based implementation of the classic Risk board game with CPU players, built with the latest Java technologies.

## ✨ Features

- **Multiplayer Support**: Create games and invite friends to join
- **CPU Players**: Play against computer opponents with different difficulty levels (Easy, Medium, Hard)
- **Game Modes**: Classic (eliminate all), Domination (control X% of the map), Turn Limit (most territories after N turns)
- **Custom Maps**: Load built-in maps or add your own via the `maps/` directory
- **Real-time Updates**: WebSocket-based live game updates
- **Interactive Map**: SVG-based world map with clickable territories
- **Full Game Rules**: Reinforcement, Attack, and Fortify phases
- **Spectator Mode**: Watch ongoing games without participating
- **In-game Chat**: Communicate with other players during matches
- **Responsive Design**: Play on desktop or tablet

## 🛠️ Technologies

- **Java 25** - Latest version
- **Spring Boot 4.0** - Modern Spring framework (Spring Framework 7)
- **Spring WebSocket** - Real-time bidirectional communication
- **Spring Data JPA** - Database persistence (Hibernate 7)
- **Spring Security 7** - Authentication and authorization
- **H2 Database** - In-memory database for development
- **Jackson 3** - JSON serialization
- **Thymeleaf** - Server-side HTML templating
- **Bootstrap 5** - Responsive CSS framework
- **Lombok 1.18.42** - Reduce boilerplate code
- **Maven** - Build and dependency management

## 🚀 Getting Started

### Prerequisites

- Java 25 or higher
- Maven 3.9+

### Running the Application

1. **Clone the repository**
   ```bash
   cd riskai
   ```

2. **Build the project**
   ```bash
   mvn clean install
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

4. **Open your browser**
   Navigate to `http://localhost:8080`

### Running Tests

```bash
mvn test
```

## 🎮 How to Play

### Creating a Game

1. Click "Create New Game" on the lobby page
2. Enter a game name and your player name
3. Choose a map and game mode
4. Optionally add CPU players with a difficulty level
5. Share the game link with friends or add more CPU players
6. Click "Start Game" when ready

### Game Modes

- **Classic**: Eliminate all other players by conquering every territory
- **Domination**: First player to control a target percentage of the map wins
- **Turn Limit**: The player with the most territories after a set number of turns wins

### Game Phases

1. **Reinforcement Phase**
   - Receive armies based on territories owned and continent bonuses
   - Click on your territories to place armies

2. **Attack Phase**
   - Select one of your territories with 2+ armies
   - Click an adjacent enemy territory to attack
   - Dice are rolled automatically
   - Continue attacking or end the phase

3. **Fortify Phase**
   - Move armies between connected territories you own
   - Or skip to end your turn

## 📁 Project Structure

```
src/main/java/com/risk/
├── RiskAIGameApplication.java       # Main application entry point
├── config/                          # Spring configuration
│   ├── AsyncConfig.java             # Async execution config
│   ├── MapLoader.java               # Built-in & custom map loader
│   ├── MapDefinition.java           # Map definition model
│   ├── AreaDefinition.java          # Area/continent definition
│   ├── TerritoryDefinition.java     # Territory definition
│   ├── SecurityConfig.java          # Security settings
│   └── WebSocketConfig.java         # WebSocket setup
├── controller/                      # REST controllers
│   ├── GameController.java          # Game API endpoints
│   └── WebController.java           # Web page controllers
├── cpu/                             # CPU player strategies
│   ├── CPUStrategy.java             # Strategy interface
│   ├── CPUAction.java               # CPU action model
│   ├── CPUStrategyFactory.java      # Factory for strategy selection
│   ├── EasyCPUStrategy.java         # Random/simple CPU
│   ├── MediumCPUStrategy.java       # Balanced CPU
│   └── HardCPUStrategy.java         # Strategic CPU
├── dto/                             # Data Transfer Objects
│   ├── CreateGameRequest.java       # Game creation request
│   ├── JoinGameRequest.java         # Game join request
│   ├── GameStateDTO.java            # Full game state
│   ├── GameSummaryDTO.java          # Lobby game summary
│   ├── GameActionDTO.java           # Game action
│   ├── PlayerDTO.java               # Player info
│   ├── TerritoryDTO.java            # Territory info
│   ├── ContinentDTO.java            # Continent info
│   └── MapInfoDTO.java              # Available map info
├── exception/                       # Exception handlers
│   └── GlobalExceptionHandler.java  # REST error handling
├── model/                           # JPA entities & enums
│   ├── Game.java                    # Game entity
│   ├── Player.java                  # Player entity
│   ├── Territory.java               # Territory entity
│   ├── Continent.java               # Continent entity
│   ├── GameMode.java                # Classic / Domination / Turn Limit
│   ├── GamePhase.java               # Reinforcement / Attack / Fortify
│   ├── GameStatus.java              # Waiting / In Progress / Finished
│   ├── PlayerType.java              # Human / CPU
│   ├── PlayerColor.java             # Player colors enum
│   └── CPUDifficulty.java           # Easy / Medium / Hard
├── repository/                      # JPA repositories
│   ├── GameRepository.java
│   ├── PlayerRepository.java
│   ├── TerritoryRepository.java
│   └── ContinentRepository.java
├── service/                         # Business logic
│   ├── GameService.java             # Core game logic
│   ├── MapService.java              # Map initialization
│   └── CPUPlayerService.java        # CPU turn execution
└── websocket/                       # WebSocket handlers
    ├── GameWebSocketController.java # STOMP message handlers
    └── GameWebSocketHandler.java    # Broadcast utilities
```

## 🔧 Configuration

### Application Properties

Key configuration options in `application.yml`:

```yaml
game:
  maps-directory: maps        # External maps directory
  max-players: 6              # Maximum players per game
  min-players: 2              # Minimum players to start
  cpu:
    think-delay-ms: 3000      # CPU decision delay (ms)
    default-difficulty: MEDIUM
```

### Database

By default, the application uses H2 in-memory database. For production, configure PostgreSQL:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/riskdb
    username: your_username
    password: your_password
```

## 🔌 API Endpoints

### REST API (`/api/games`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/games/maps` | List available maps |
| GET | `/api/games` | List all games |
| POST | `/api/games` | Create a new game |
| GET | `/api/games/{id}` | Get game state |
| POST | `/api/games/{id}/join` | Join a game |
| POST | `/api/games/{id}/cpu` | Add a CPU player |
| POST | `/api/games/{id}/start` | Start the game |
| POST | `/api/games/{id}/reinforce` | Place armies |
| POST | `/api/games/{id}/attack` | Attack a territory |
| POST | `/api/games/{id}/endAttack` | End attack phase |
| POST | `/api/games/{id}/fortify` | Move armies |
| POST | `/api/games/{id}/skipFortify` | Skip fortify phase |

### WebSocket (STOMP over SockJS)

Connect to `/ws` using SockJS/STOMP for real-time updates.

Subscribe to `/topic/game/{gameId}` for game events:
- `GAME_UPDATE` — state changed
- `GAME_STARTED` — game began
- `GAME_OVER` — game finished
- `ATTACK_RESULT` — dice roll outcome
- `CPU_FORTIFY` — CPU army movement
- `CPU_TURN_END` — CPU finished turn
- `PLAYER_JOINED` — new player joined

Send actions to `/app/game/{gameId}/{action}` (reinforce, attack, endAttack, fortify, skipFortify, chat).

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## 📝 License

This project is for educational purposes. Risk is a trademark of Hasbro. RiskAI is a fan project.

## 🎯 Future Improvements

- [ ] Player authentication with accounts
- [ ] Territory cards and trading
- [ ] Game replay / history
- [ ] Mobile-responsive improvements
- [ ] Tournament mode
- [ ] Sound effects and animations
