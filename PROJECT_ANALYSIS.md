# RiskAI Project — Architecture Review & Analysis

> **Date:** February 2026  
> **Last Updated:** February 19, 2026 (verified against `fix/bugs` branch)  
> **Scope:** Full codebase review — structure, architecture, code quality, bugs, testing, and recommendations  
> **Codebase:** 47 Java files, ~3,200 lines of Java (plus JS/HTML/CSS front-end)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Project Structure Assessment](#2-project-structure-assessment)
3. [Architecture & Design Patterns](#3-architecture--design-patterns)
4. [Class-by-Class Analysis](#4-class-by-class-analysis)
5. [Bugs & Potential Issues](#5-bugs--potential-issues)
6. [Security Concerns](#6-security-concerns)
7. [Performance Issues](#7-performance-issues)
8. [Testing Assessment](#8-testing-assessment)
9. [Code Quality & Conventions](#9-code-quality--conventions)
10. [Refactoring Recommendations](#10-refactoring-recommendations)
11. [Recommended Test Plan](#11-recommended-test-plan)
12. [Priority Action Items](#12-priority-action-items)

---

## 1. Executive Summary

The RiskAI project is a well-conceived Spring Boot 4.0 web-based board game with WebSocket real-time updates, CPU opponents via the Strategy pattern, and multiple game modes. The overall structure is logical and the code is readable.

**The `fix/bugs` branch has resolved 5 of the 7 originally identified bugs** (infinite loop, race condition, army miscalculation, turn over-count, and sendError no-op). The remaining work is architectural and quality-focused.

**Current status:**

| Category | Severity | Count |
|----------|----------|-------|
| ✅ Fixed Bugs (on `fix/bugs`) | Resolved | 5 |
| 🔴 Remaining Bugs | Low-Medium | 2 |
| 🟠 Architecture Violations | High | 6 |
| 🟡 Performance Problems | Medium | 8 |
| 🔵 Code Quality Issues | Low | 10 |
| ⚪ Test Coverage Gaps | Critical | ~92% untested |

### Biggest Remaining Risks
1. **`GameService` is a God Class** (~639 lines, 22+ responsibilities)
2. **N+1 query problems** throughout CPU strategies (50+ queries per CPU turn)
3. **Near-zero test coverage** — only 5 tests, all integration, no unit tests
4. **Security gaps** — no player identity validation, CSRF disabled, open CORS
5. **`Thread.sleep()` blocks async thread pool** — can exhaust with 4+ concurrent games

---

## 2. Project Structure Assessment

### Current Package Layout
```
com.risk/
├── config/          ✅ Good — clean configuration separation
├── controller/      ✅ Good — REST + Web separated
├── cpu/             ✅ Good — Strategy pattern well applied
├── dto/             ✅ Good — proper DTO usage
├── exception/       ✅ Good — global handler present
├── model/           ✅ Good — JPA entities
├── repository/      ✅ Good — Spring Data repos
├── service/         🔴 Problem — 3 classes, GameService is a God Class
└── websocket/       🟡 Mixed — handler + controller + inline DTOs
```

### What's Missing

| Missing Element | Impact |
|----------------|--------|
| No `service/` sub-packages | GameService holds 15+ responsibilities in one file |
| No `mapper/` package | DTO↔Entity mapping scattered in static methods |
| No `event/` package | WebSocket broadcasting tightly coupled to services |
| No `validation/` package | Business rules embedded in service methods |
| No domain exceptions | Generic `IllegalArgumentException`/`IllegalStateException` everywhere |
| No `dice/` or `combat/` package | Combat logic embedded in GameService |

### Recommended Package Restructuring

```
com.risk/
├── config/
├── controller/
│   ├── rest/           → GameController (REST API)
│   └── web/            → WebController (Thymeleaf pages)
├── cpu/
│   ├── strategy/       → CPUStrategy, Easy/Medium/Hard implementations
│   └── model/          → CPUAction
├── dto/
│   ├── request/        → CreateGameRequest, JoinGameRequest
│   ├── response/       → GameStateDTO, PlayerDTO, TerritoryDTO, etc.
│   └── websocket/      → GameActionDTO, inline message DTOs
├── event/              → Spring ApplicationEvents for game actions
├── exception/          → Domain-specific exceptions
├── mapper/             → MapStruct or manual DTO mappers
├── model/
│   ├── entity/         → JPA entities
│   └── enums/          → GameMode, GamePhase, etc.
├── repository/
├── service/
│   ├── GameLifecycleService     → create, join, start game
│   ├── TurnManagementService    → phase transitions, end turn
│   ├── CombatService            → attack logic, dice rolling
│   ├── ReinforcementService     → calculate & place reinforcements
│   ├── FortificationService     → fortify logic
│   ├── WinConditionService      → game over checks per mode
│   └── CPUPlayerService         → CPU turn orchestration
└── websocket/
    ├── handler/        → broadcasting
    └── controller/     → STOMP message handlers
```

---

## 3. Architecture & Design Patterns

### Patterns Used ✅
| Pattern | Implementation | Quality |
|---------|---------------|---------|
| Strategy | CPU difficulties | ✅ Well done |
| Repository | Spring Data JPA | ✅ Proper |
| DTO | API responses | ✅ Appropriate |
| Factory | `CPUStrategyFactory` | ✅ Clean |
| Builder | Lombok `@Builder` everywhere | ✅ Good |
| MVC | Controllers + Thymeleaf | ✅ Standard |

### Patterns Missing ❌

| Missing Pattern | Where Needed | Why |
|----------------|-------------|-----|
| **Event-Driven (ApplicationEvent)** | WebSocket broadcasts | Currently tightly coupled; `CPUPlayerService` directly calls `GameWebSocketHandler`. Should publish domain events instead |
| **Command Pattern** | Game actions | `placeArmies()`, `attack()`, `fortify()` should be Commands for undo/replay |
| **State Pattern** | Game phases | Phase transitions are `if/switch` chains in `GameService`. Each phase should be its own State object |
| **Observer Pattern** | Real-time updates | Spring `@EventListener` would decouple services from WebSocket |
| **Specification Pattern** | Win conditions | Win condition checks are hardcoded `if` blocks in `checkGameOver()` |

---

## 4. Class-by-Class Analysis

### 🔴 `GameService.java` (~639 lines) — GOD CLASS

This is the most critical issue. This single class handles:

1. Game creation
2. Player joining
3. Color assignment
4. Game starting
5. Territory distribution
6. Initial army calculation
7. Reinforcement calculation
8. Army placement
9. Attack execution
10. Dice rolling
11. Attack resolution
12. Phase transitions
13. Fortification
14. Turn management
15. Win condition checking (3 modes)
16. Game finishing
17. Game state DTO construction
18. `AttackResult` inner class

**Single Responsibility Principle (SRP)** is severely violated.

**Recommended split:**

| New Service | Responsibility | Est. Lines |
|------------|---------------|------------|
| `GameLifecycleService` | Create, join, start, finish | ~120 |
| `CombatService` | Attack, dice, resolution | ~100 |
| `ReinforcementService` | Calculate & place armies | ~60 |
| `FortificationService` | Move armies, validation | ~50 |
| `TurnService` | Phase transitions, next player | ~60 |
| `WinConditionService` | Classic/Domination/TurnLimit checks | ~70 |
| `GameQueryService` | `getGameState()`, `getJoinableGames()` | ~80 |

---

### 🟡 `CPUPlayerService.java` (~207 lines)

- ✅ **Race condition fixed** — Now uses `ConcurrentHashMap<String, ReentrantLock>` with `tryLock()` per game ID. Concurrent CPU turn execution is properly guarded.

- **Duplicated winner-lookup logic** — The "find winner name" pattern appears 2 times in this file (and 3 more across `GameWebSocketController`).
  Should be extracted to a helper method.

- **`Thread.sleep()` in `@Async` methods** — Blocks the thread pool. Should use `ScheduledExecutorService` or reactive delays.

- **No transaction boundary** — `@Async` methods run outside the caller's transaction. Each `gameService` call inside creates its own transaction, but the overall turn isn't atomic. A failed attack mid-turn leaves inconsistent state.

- **Recursive CPU chaining** — `executeCPUTurn()` calls `checkAndTriggerCPUTurn()` at the end, which may call `executeCPUTurn()` again. With 5 consecutive CPU players, this creates deep call stacks. Note: the `ReentrantLock` means the same thread *can* re-acquire the lock, so this recursive pattern still works but creates deep stacks. Should use a queue-based approach.

---

### 🟡 `GameWebSocketHandler.java` (~256 lines)

- **Too many inner static classes** — `GameMessage`, `AttackResultMessage`, `CPUFortifyMessage`, `ChatMessage` should be standalone classes in a `dto.websocket` package.
- **Circular dependency risk** — `GameWebSocketHandler` depends on `GameService`, and `CPUPlayerService` depends on both. In Spring Boot 4, circular beans are rejected by default.

---

### 🟡 `GameWebSocketController.java` (~234 lines)

- **Duplicated game-over check logic** — The same "check if game finished → broadcast game over" pattern is copy-pasted in `handleAttack()`, `handleFortify()`, and `handleSkipFortify()`.
- ✅ **`sendError()` fixed** — Now calls `webSocketHandler.broadcastError()` which sends error messages to clients via STOMP.
- **Inline message DTOs** — `ReinforceMessage`, `AttackMessage`, `FortifyMessage`, `PlayerIdMessage`, `ChatMessageRequest` are all inner classes. These should be extracted.

---

### 🟡 `GameController.java` (~247 lines)

- **`@CrossOrigin(origins = "*")`** — Wide open CORS in production is dangerous.
- **No pagination** on `getGames()` — Will become a problem as games accumulate.
- **`toGameSummary()` silently swallows exceptions** — `catch (Exception ignored) {}` when loading map name.
- **Inconsistent API** — `reinforce`, `attack`, `fortify` use `@RequestParam` for data that should be `@RequestBody` JSON.

---

### ✅ `MapService.java` (77 lines) — Clean & Focused
Good single responsibility. No issues.

---

### ✅ `MapLoader.java` (125 lines) — Well Designed
Clean two-source map loading (classpath + external). No issues.

---

### ✅ CPU Strategies — Good Strategy Pattern Usage
- **`EasyCPUStrategy`** (75 lines) — Clean, random-based.
- **`MediumCPUStrategy`** (118 lines) — Good heuristic approach.
- **`HardCPUStrategy`** (166 lines) — Solid continent-focused strategy.

**Issues common to all three:**
- All strategies fetch `territoryRepository.findByGameId()` and `findByOwnerId()` **multiple times per decision**, causing N+1 query problems.
- `new Random()` is created per-call or stored as instance field in a singleton `@Component` — not thread-safe when multiple games run concurrently.

---

### ✅ Model Classes — Generally Good

- `Game.java` (~106 lines) — Well-structured entity with good helper methods. ✅ `nextPlayer()` turn-counting bug fixed.
- `Player.java` (68 lines) — Clean.
- `Territory.java` — Clean.
- `Continent.java` — Clean.
- Enums — All clean and well-documented.

**Issue with `@Data` on JPA entities:** Lombok `@Data` generates `equals()`/`hashCode()` based on all fields, which is problematic with Hibernate proxies and lazy loading. You've partially addressed this with `@ToString.Exclude` and `@EqualsAndHashCode.Exclude` on relations, but the entities should use `@Getter`/`@Setter` instead of `@Data`, and implement `equals()`/`hashCode()` based on the business key or ID only.

---

## 5. Bugs & Potential Issues

### ✅ ~~BUG 1: Infinite Loop in `endTurn()` — Eliminated Player Skip~~ **FIXED**

**Status:** Resolved on `fix/bugs` branch. A circuit breaker with `maxAttempts` guard and a `wrapped` boolean for turn-number tracking has been implemented. The turn number now only increments once per actual round, not per skipped player.

---

### ✅ ~~BUG 2: Race Condition in CPU Turn Execution~~ **FIXED**

**Status:** Resolved on `fix/bugs` branch. `CPUPlayerService` now uses `ConcurrentHashMap<String, ReentrantLock>` with `tryLock()` per game ID. If a CPU turn is already running for a game, subsequent attempts return immediately. Locks are cleaned up when no threads are queued.

---

### ✅ ~~BUG 3: Territory Ownership After Conquest — Army Miscalculation~~ **FIXED**

**Status:** Resolved on `fix/bugs` branch. The conquest code now uses `Math.min(attackingArmies, from.getArmies() - 1)` to ensure at least 1 army always remains on the source territory. An `IllegalStateException` is thrown if the constraint can't be satisfied.

---

### ✅ ~~BUG 4: `nextPlayer()` Over-Counts Turns~~ **FIXED**

**Status:** Resolved on `fix/bugs` branch. `nextPlayer()` now only advances the index (`currentPlayerIndex = (currentPlayerIndex + 1) % players.size()`). Turn number tracking has been moved to `endTurn()` which uses a `wrapped` boolean flag to increment the turn count exactly once per actual round.

---

### � CODE SMELL: `getGameState()` Redundant Territory Loading

`GameStateDTO.fromGame()` builds territory/player data from the entity's lazy collections, but `getGameState()` then overwrites this with freshly queried data. The initial work in `fromGame()` is wasted. Not a correctness bug — the fresh data is always used — but it's unnecessary computation. Consider refactoring `fromGame()` to accept pre-loaded collections.

---

### ✅ ~~BUG 6: `sendError()` in WebSocket Controller is a No-Op~~ **FIXED**

**Status:** Resolved on `fix/bugs` branch. `sendError()` now calls `webSocketHandler.broadcastError(gameId, playerId, error)` which sends a `GameMessage.error()` payload to the game's STOMP topic at `/topic/game/{gameId}`.

---

### 🟠 BUG 7: `CPUDifficulty.EXPERT` Has No Dedicated Strategy

```java
// CPUStrategyFactory.java
case HARD, EXPERT -> hardStrategy;
```

The `EXPERT` difficulty exists in the enum but silently falls back to `HARD`. This is misleading to users. Either implement an `ExpertCPUStrategy` or remove `EXPERT` from the enum.

---

## 6. Security Concerns

| Issue | Severity | Location |
|-------|----------|----------|
| CSRF disabled globally | 🔴 High | `SecurityConfig.java` |
| `@CrossOrigin(origins = "*")` | 🔴 High | `GameController.java` |
| No authentication on game actions | 🟠 Medium | All endpoints are `permitAll()` |
| No player identity validation | 🟠 Medium | `playerId` passed as request param — anyone can impersonate |
| H2 console enabled in default profile | 🟡 Low | `application.yml` |
| Hardcoded admin credentials | 🟡 Low | `application.yml` — `admin/admin123` |
| No rate limiting | 🟡 Low | All endpoints open to abuse |

**Critical:** Any player can send actions as another player by simply changing the `playerId` parameter. There's no session-to-player validation in the REST endpoints.

---

## 7. Performance Issues

### 🔴 N+1 Query Problem in CPU Strategies

Every CPU strategy calls `territoryRepository.findByGameId()` and `findByOwnerId()` **multiple times per decision**. During a CPU turn with reinforcement + 10 attacks + fortify, this can result in **50+ database queries**.

```java
// EasyCPUStrategy.decideAttack()
List<Territory> attackCapable = territoryRepository.findAttackCapableTerritories(...);
for (Territory from : attackCapable) {
    List<Territory> allTerritories = territoryRepository.findByGameId(...); // INSIDE THE LOOP!
    ...
}
```

**Fix:** Load all territories once at the start of the CPU turn and pass them as context.

### 🟠 Missing Database Indexes

The following queries will be slow as data grows:
- `TerritoryRepository.findByOwnerId()` — needs index on `owner_id`
- `TerritoryRepository.findByGameIdAndTerritoryKey()` — needs composite index
- `PlayerRepository.findActivePlayersByGameId()` — needs index on `(game_id, eliminated)`

### 🟠 `Thread.sleep()` Blocks Thread Pool

CPU think delays use `Thread.sleep(thinkDelayMs)` (default 3 seconds) in `@Async` methods. With the thread pool configured at `maxPoolSize=10`, 4 concurrent games with CPU players could exhaust the pool.

### 🟡 No Connection Pooling Configuration

H2 is in-memory, but for production PostgreSQL, there's no HikariCP tuning.

---

## 8. Testing Assessment

### Current State — ⚪ Critical Gap

| Metric | Value |
|--------|-------|
| Total test classes | 2 |
| Total test methods | 5 (+1 `contextLoads`) |
| Unit tests | 0 |
| Integration tests | 5 |
| Mocked tests | 0 |
| Estimated line coverage | **~5-8%** |
| Controller tests | 0 |
| WebSocket tests | 0 |
| CPU Strategy tests | 0 |
| Exception handler tests | 0 |

### Problems with Existing Tests

1. **All tests are `@SpringBootTest`** — slow, load the entire application context.
2. **No mocking** — tests hit the real database.
3. **`GameServiceTest.createGame_shouldInitializeMap()`** — asserts 42 territories, which is map-specific. Will break if the default map changes.
4. **`startGame_shouldDistributeTerritories()`** — adds a 3rd CPU player when the game already has 2 players (1 human + 1 CPU). This means the test operates with 3 players, which may not match the intended scenario.
5. **No edge case testing** — no tests for invalid inputs, concurrent access, game over conditions, etc.

### What's Not Tested At All

- Combat mechanics (dice rolling, attack resolution, conquest)
- Win condition checks (Classic, Domination, Turn Limit)
- Phase transitions
- Turn cycling with eliminated players
- CPU strategy decisions
- WebSocket message broadcasting
- REST controller request/response
- Input validation
- Error handling
- Concurrent game access

---

## 9. Code Quality & Conventions

### Good Practices ✅
- Consistent use of Lombok annotations
- Java 25 features (records for config definitions, switch expressions)
- `@Builder` pattern for object construction
- Proper `@Transactional` annotations
- Good Javadoc on public methods
- RESTful URL design (`/api/games/{gameId}/attack`)
- Proper use of `@PrePersist` for default values

### Issues ❌

| Issue | Files Affected |
|-------|---------------|
| `@Data` on JPA entities (should be `@Getter`/`@Setter`) | `Game`, `Player`, `Territory`, `Continent` |
| `new Random()` created repeatedly (non-deterministic for testing) | `GameService`, `EasyCPUStrategy` |
| Magic numbers not extracted as constants | `getInitialArmiesPerPlayer()`, attack validation |
| Generic exceptions instead of domain exceptions | All service methods |
| Static `fromXxx()` methods on DTOs instead of dedicated mappers | All DTOs |
| Inline inner classes in websocket classes | `GameWebSocketHandler`, `GameWebSocketController` |
| `CPUAction` uses manual getters/setters when Lombok is available | `CPUAction.java` |
| Mixed `toList()` and `Collectors.toList()` usage | `GameController` |
| `Object` type for `GameMessage.payload` — no type safety | `GameWebSocketHandler.GameMessage` |
| No API versioning (`/api/v1/games`) | `GameController` |

---

## 10. Refactoring Recommendations

### Phase 1 — Critical Fixes (Week 1)

1. **Split `GameService` into focused services** (see class analysis above)
2. ~~**Fix the infinite loop bug** in `endTurn()` eliminated player skip~~ ✅ Done
3. ~~**Fix the turn counter** over-increment in `nextPlayer()`~~ ✅ Done
4. **Add optimistic locking** (`@Version` field on `Game` entity) to prevent concurrent modification
5. ~~**Implement `sendError()`** in WebSocket controller to actually send errors to clients~~ ✅ Done

### Phase 2 — Architecture Improvements (Week 2)

6. **Introduce domain events** — Use Spring `ApplicationEventPublisher` to decouple services from WebSocket broadcasting:
   ```java
   // Instead of: webSocketHandler.broadcastGameUpdate(gameId)
   // Publish: applicationEventPublisher.publishEvent(new GameUpdatedEvent(gameId))
   // Handle in: @EventListener on WebSocket handler
   ```

7. **Create domain exceptions:**
   ```
   GameNotFoundException extends RuntimeException
   InvalidGameStateException extends RuntimeException
   NotYourTurnException extends RuntimeException
   InvalidActionException extends RuntimeException
   ```

8. **Extract a `DiceService`** — Make dice rolling injectable and mockable for testing:
   ```java
   public interface DiceService {
       int[] roll(int count);
   }
   ```

9. **Create a `GameContext` object** — Pass pre-loaded game state to CPU strategies instead of letting them re-query:
   ```java
   public record GameContext(
       Game game, 
       List<Territory> allTerritories,
       List<Continent> continents,
       Map<String, List<Territory>> territoriesByOwner
   ) {}
   ```

10. **Replace `Thread.sleep()` with a scheduled delay** — Use `CompletableFuture.delayedExecutor()` or Spring's `TaskScheduler`.

### Phase 3 — Quality & Testing (Week 3)

11. **Add MapStruct** for DTO mapping to replace manual `fromXxx()` methods.
12. **Add Swagger/OpenAPI** documentation for the REST API.
13. **Add player identity validation** — Verify session ID matches the player performing the action.
14. **Add `@Version` field** to `Game` entity for optimistic locking.
15. **Replace `@Data` with `@Getter`/`@Setter`** on all JPA entities and implement proper `equals()`/`hashCode()` based on ID.

---

## 11. Recommended Test Plan

### Unit Tests (Mockito-based, no Spring context)

```
test/java/com/risk/
├── service/
│   ├── GameServiceTest.java          → createGame, joinGame, startGame
│   ├── CombatServiceTest.java        → dice rolling, attack resolution, conquest
│   ├── ReinforcementServiceTest.java → calculation, placement, continent bonuses
│   ├── WinConditionServiceTest.java  → Classic, Domination, TurnLimit modes
│   ├── TurnServiceTest.java          → phase transitions, skip eliminated
│   └── MapServiceTest.java           → map initialization
├── cpu/
│   ├── EasyCPUStrategyTest.java      → random decisions, edge cases
│   ├── MediumCPUStrategyTest.java    → border reinforcement, advantage attacks
│   ├── HardCPUStrategyTest.java      → continent targeting, smart fortify
│   └── CPUStrategyFactoryTest.java   → correct strategy selection
├── controller/
│   ├── GameControllerTest.java       → @WebMvcTest, all REST endpoints
│   └── WebControllerTest.java        → page rendering
├── dto/
│   ├── CreateGameRequestValidationTest.java → @Valid constraints
│   └── DTOMappingTest.java                  → fromXxx() methods
├── model/
│   ├── GameTest.java                 → nextPlayer(), canStart(), isFull()
│   ├── TerritoryTest.java            → isOwnedBy(), canAttackFrom(), isNeighborOf()
│   └── ContinentTest.java            → isControlledBy()
├── exception/
│   └── GlobalExceptionHandlerTest.java → all exception types
└── config/
    └── MapLoaderTest.java            → classpath + external loading
```

### Integration Tests

```
test/java/com/risk/
├── integration/
│   ├── FullGameFlowTest.java         → Create → Join → Start → Play → Win
│   ├── CPUGameTest.java              → All-CPU game plays to completion
│   ├── WebSocketIntegrationTest.java → STOMP message flow
│   ├── MultiGameConcurrencyTest.java → Concurrent game sessions
│   └── GameModeTest.java             → Classic, Domination, TurnLimit flows
```

### Key Test Scenarios to Cover

| Scenario | Priority |
|----------|----------|
| Attack with exactly 1 army remaining on source | 🔴 Critical |
| Conquest → eliminated player → game over check | 🔴 Critical |
| Turn limit reached mid-turn | 🔴 Critical |
| All territories conquered in domination mode | 🔴 Critical |
| Skip eliminated players without infinite loop | 🔴 Critical |
| Concurrent CPU turns on same game | 🔴 Critical |
| Invalid player ID on actions | 🟠 High |
| Attack non-adjacent territory | 🟠 High |
| Fortify more armies than available | 🟠 High |
| Create game with > 6 players | 🟡 Medium |
| Join a full game | 🟡 Medium |
| WebSocket reconnection during CPU turn | 🟡 Medium |

---

## 12. Priority Action Items

### ✅ Immediate (Blocking Issues) — ALL RESOLVED

| # | Action | Files | Status |
|---|--------|-------|--------|
| 1 | Fix infinite loop in `endTurn()` eliminated player skip | `GameService.java` | ✅ Fixed |
| 2 | Fix turn counter over-increment in `nextPlayer()` | `Game.java` | ✅ Fixed |
| 3 | Fix possible 0-army territory after conquest | `GameService.java` | ✅ Fixed |
| 4 | Add concurrency control for CPU turns | `CPUPlayerService.java` | ✅ Fixed |
| 5 | Implement actual error sending in `sendError()` | `GameWebSocketController.java` | ✅ Fixed |

### 🟠 Short-Term (Next Sprint)

| # | Action | Files |
|---|--------|-------|
| 6 | Split `GameService` into 5-7 focused services | `service/` package |
| 7 | Add unit tests for combat mechanics | New test files |
| 8 | Fix N+1 queries in CPU strategies | `cpu/*.java` |
| 9 | Add `@Version` optimistic locking to `Game` | `Game.java` |
| 10 | Replace `@Data` with `@Getter`/`@Setter` on entities | `model/*.java` |
| 11 | Create domain-specific exceptions | `exception/` package |
| 12 | Add player session validation on actions | `GameController.java` |

### 🟡 Medium-Term (Next Month)

| # | Action | Files |
|---|--------|-------|
| 13 | Introduce Spring ApplicationEvents for decoupling | `service/`, `websocket/` |
| 14 | Implement `ExpertCPUStrategy` or remove `EXPERT` enum | `cpu/`, `model/` |
| 15 | Add API versioning (`/api/v1/`) | `controller/` |
| 16 | Add Swagger/OpenAPI documentation | `config/`, `controller/` |
| 17 | Add database indexes for production readiness | New migration files |
| 18 | Configure CORS properly per environment | `SecurityConfig.java` |
| 19 | Add comprehensive integration test suite | `test/` |
| 20 | Replace `Thread.sleep()` with non-blocking delays | `CPUPlayerService.java` |

---

## Appendix: File Size Breakdown

| File | Lines | Status |
|------|-------|--------|
| `GameService.java` | ~639 | 🔴 God Class — split required |
| `GameWebSocketHandler.java` | ~256 | 🟡 Inner classes should be extracted |
| `GameController.java` | ~247 | 🟡 Needs param → body refactoring |
| `GameWebSocketController.java` | ~234 | 🟡 Duplicated logic, inline DTOs |
| `CPUPlayerService.java` | ~207 | 🟡 Concurrency fixed, Thread.sleep remains |
| `HardCPUStrategy.java` | ~166 | ✅ Acceptable |
| `MapLoader.java` | ~125 | ✅ Clean |
| `MediumCPUStrategy.java` | ~118 | ✅ Acceptable |
| `Game.java` | ~106 | ✅ `nextPlayer()` bug fixed |
| All other files | < 80 each | ✅ Acceptable |

---

*End of analysis. Reach out for implementation assistance on any of these recommendations.*
