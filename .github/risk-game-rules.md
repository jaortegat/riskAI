# Risk Game Rules

Complete mechanics and rules for the RiskAI board game implementation.

**Note**: This game supports **multiple maps** (Classic World, Europe, etc.). Rules are universal, but territory counts and continent bonuses vary by map. Always call `getGameState` to see the active map configuration.

## Game Objective

Conquer territories and continents to earn armies and eliminate opponents. Win conditions vary by game mode:

- **CLASSIC**: Eliminate all opponents by conquering every territory on the map
- **DOMINATION**: Control a target percentage of territories (e.g., 75%)
- **TURN_LIMIT**: Control the most territories after a fixed number of turns

## Map Structure

The game supports **multiple maps** with different territory counts and continent configurations. Continent bonuses are awarded at the start of your turn if you control *all* territories in that continent.

**To see which map is in use**, call `getGameState` and examine the `continents` array for:
- Continent names and bonus values
- Territories within each continent
- Border complexity (number of entry points)

### Available Maps

#### Classic World Map
- **42 territories** across **6 continents**
- Best for: 4-6 player games, full Risk experience
- Game length: 60-120 minutes

| Continent | Territories | Bonus Armies | Borders | Difficulty |
|-----------|-------------|--------------|---------|------------|
| **Australia** | 4 | +2 | 1 | ⭐ Easiest to defend |
| **South America** | 4 | +2 | 2 | ⭐⭐ Easy |
| **Africa** | 6 | +3 | 3 | ⭐⭐⭐ Moderate |
| **North America** | 9 | +5 | 3 | ⭐⭐⭐ Moderate |
| **Europe** | 7 | +5 | 4 | ⭐⭐⭐⭐ Hard |
| **Asia** | 12 | +7 | 6 | ⭐⭐⭐⭐⭐ Hardest |

#### Europe Map
- **24 territories** across **4 regions**
- Best for: 2-4 player games, shorter matches
- Game length: 30-60 minutes

| Region | Territories | Bonus Armies | Borders | Difficulty |
|--------|-------------|--------------|---------|------------|
| **Northern Europe** | 5 | +3 | 3 | ⭐⭐ Easy |
| **Western Europe** | 6 | +4 | 3 | ⭐⭐⭐ Moderate |
| **Central Europe** | 7 | +5 | 4 | ⭐⭐⭐⭐ Hard |
| **Eastern Europe** | 6 | +5 | 4 | ⭐⭐⭐⭐ Hard |

**Strategic Note**: Smaller maps (Europe) lead to faster, more aggressive games. Larger maps (Classic World) reward patient continent control.

## Turn Structure

Each turn consists of three phases, executed in order:

### 1. REINFORCEMENT Phase

**Receive armies** based on:
- **Base calculation**: `Math.max(3, territoryCount / 3)`
  - You always receive at least 3 armies, even if you control only 1-2 territories
  - 9 territories = 3 armies, 12 territories = 4 armies, 15 territories = 5 armies, etc.
- **Continent bonuses**: Add bonus armies for each continent you fully control

**Examples** (Classic World Map, 42 territories):
- 8 territories, no continents: 3 armies (minimum)
- 12 territories + Australia: 4 + 2 = 6 armies
- 20 territories + South America + Africa: 6 + 2 + 3 = 11 armies

**Examples** (Europe Map, 24 territories):
- 6 territories, no regions: 3 armies (minimum)
- 9 territories + Northern Europe: 3 + 3 = 6 armies
- 15 territories + Western + Central Europe: 5 + 4 + 5 = 14 armies

**Place armies** on any territories you own. You can:
- Place all armies on one territory
- Split armies across multiple territories
- Call `placeArmies` multiple times until all reinforcements are used

Once all armies are placed, you automatically advance to the **ATTACK** phase.

### 2. ATTACK Phase

**Rules**:
- You must attack *from* a territory with **2+ armies** (you must leave at least 1 army behind)
- You can only attack *adjacent* territories owned by opponents
- You declare how many armies to attack with: **1, 2, or 3** (limited by your available armies)
- You can attack as many times as you want during your turn
- Each attack is a separate combat resolution

**Combat Mechanics**:

1. **Attacker rolls dice** — 1-3 dice based on attacking armies:
   - 1 attacking army = 1 die
   - 2 attacking armies = 2 dice
   - 3+ attacking armies = 3 dice

2. **Defender rolls dice** — 1-2 dice based on defending armies:
   - 1 defending army = 1 die
   - 2+ defending armies = 2 dice

3. **Compare highest dice**:
   - Sort all dice descending (highest to lowest)
   - Compare attacker's highest vs. defender's highest
   - If both rolled 2+ dice, also compare second-highest vs. second-highest
   - **Ties favor the defender** — defender wins on equal rolls

4. **Losses**:
   - Loser of each comparison loses 1 army
   - Maximum 2 armies lost per attack (one per die comparison)
   - Armies are removed from territories immediately

**Example Combat**:
- Attacker rolls [5, 4, 2], Defender rolls [6, 3]
- Compare: 6 > 5 (attacker loses 1), 4 > 3 (defender loses 1)
- **Result**: Attacker -1 army, Defender -1 army

**Conquest**:
- If the defender's army count drops to 0, the territory is conquered
- The attacker *must* move at least the attacking armies into the conquered territory
- Check if the defender was eliminated (no territories remaining)

**Ending the Attack Phase**:
- Call `endAttackPhase` when you're done attacking
- Advances to **FORTIFY** phase

### 3. FORTIFY Phase

**Move armies** between two of your adjacent territories:
- Source territory must have **2+ armies** (must leave at least 1 behind)
- Destination territory must be **adjacent** to the source
- You can move as many armies as you want (minus the 1 that must stay)
- You can only fortify **once per turn**

**Alternatively**:
- Call `skipFortify` to end your turn without moving armies

After fortifying or skipping, your turn ends and the next player begins their **REINFORCEMENT** phase.

## Strategic Concepts

### Army Advantage Ratios

Probability of winning an attack varies by army ratio:

| Attacker Armies | Defender Armies | Advantage | Risk Level |
|----------------|----------------|----------|------------|
| 3+ | 1 | 3:1 | **Low risk** — recommended |
| 4+ | 1 | 4:1 | **Very safe** — conservative play |
| 2+ | 1 | 2:1 | **Moderate risk** — aggressive play |
| Equal or less | Any | ≤1:1 | **High risk** — avoid unless strategic |

**Note**: Dice favor the defender due to tie rules. Always attack with numerical superiority when possible.

### Continent Control Priority

**General Strategy** (applies to all maps):
1. **Target small continents first** — fewer territories = easier to complete and defend
2. **Prioritize low border counts** — 1-2 borders are defensible, 4+ borders are vulnerable
3. **Balance bonus value vs. defense cost** — a +2 bonus with 1 border is better than +5 with 5 borders

**Classic World Map Priority**:
- **Early Game** (turns 1-5): Australia (1 border) → South America (2 borders)
- **Mid Game** (turns 6-15): Africa (3 borders), then Europe or North America (3-4 borders)
- **Late Game** (turns 16+): Asia (6 borders, only if dominant)

**Europe Map Priority**:
- **Early Game** (turns 1-5): Northern Europe (3 borders, +3 bonus)
- **Mid Game** (turns 6-15): Western Europe (3 borders, +4 bonus)
- **Late Game** (turns 16+): Central or Eastern Europe (+5 bonus each, but 4 borders)

**Universal Tactic**: Break enemy continent bonuses by capturing 1 key territory — denying an opponent +5 armies per turn is often better than gaining +2 yourself.

### Border Management

**Minimum border strength**:
- **2 armies**: Risky — can be taken by a lucky 1-army attack
- **3 armies**: Standard defense — stops most single attacks
- **4+ armies**: Strong defense — deters attacks
- **8+ armies**: Offensive staging area — ready to attack

**Rule of thumb**: Keep at least 3 armies on every border territory. Interior territories (surrounded by your own territories) can have just 1 army.

### Fortification Strategy

**Defensive fortification**:
- Move armies from interior → border territories
- Strengthen weak points facing strong opponents

**Offensive fortification**:
- Consolidate armies into one "spearhead" territory
- Prepare for a major offensive next turn

**When to skip fortify**:
- All your armies are already well-positioned
- You have no safe interior territories to pull from
- Moving armies would create new weak points

## Game Phases Summary

| Phase | Purpose | Key Actions | Ends When |
|-------|---------|-------------|-----------|
| **REINFORCEMENT** | Receive and place armies | `placeArmies` | All reinforcements placed |
| **ATTACK** | Conquer territories | `attack`, `getAttackableTargets`, `endAttackPhase` | Player calls `endAttackPhase` |
| **FORTIFY** | Reposition armies | `fortify`, `skipFortify` | Player fortifies once or skips |

## Win Condition Details

### CLASSIC Mode
- **Goal**: Eliminate all opponents
- **Victory**: Be the last player with territories
- **Strategy**: Focus on eliminating weak players early

### DOMINATION Mode
- **Goal**: Control a target percentage of the map (e.g., 75% of territories)
  - Classic World (42 territories): 32 territories needed
  - Europe (24 territories): 18 territories needed
- **Victory**: First player to reach the threshold
- **Strategy**: Expand rapidly, don't waste time eliminating players

### TURN_LIMIT Mode
- **Goal**: Control the most territories when the turn limit is reached
- **Victory**: Highest territory count at game end (ties possible)
- **Strategy**: Balance offense and defense, outlast opponents

## Advanced Tactics

### Card Trading (Not Yet Implemented)
*Reserved for future expansion — territory cards can be traded for bonus armies*

### Alliance Dynamics (Multiplayer)
- **Temporary truces**: Don't attack the player who could eliminate you
- **Kingmaking**: Avoid weakening yourself if it lets a third player win
- **Calculated aggression**: Attack the leader to prevent runaway victories

### Psychological Warfare
- **Predictable patterns**: Vary your attack targets to keep opponents guessing
- **Feints**: Stage armies on a border you don't plan to attack from
- **Rapid expansion**: Grab multiple weak territories in one turn to intimidate

## Quick Reference Card

**My Turn Workflow**:
1. `waitForMyTurn(gameId, playerId, 30)` — wait until it's your turn
2. Read `currentPhase` from the response
3. **If REINFORCEMENT**: `placeArmies` until all placed
4. **If ATTACK**: `getAttackableTargets`, then `attack` or `endAttackPhase`
5. **If FORTIFY**: `fortify` or `skipFortify`
6. Repeat from step 1

**Key Formulas**:
- Reinforcements = `max(3, territories ÷ 3) + continent bonuses`
- Attack armies = 1-3 (must leave 1+ behind)
- Defend armies = 1-2 (automatic, based on defender's count)
- Win = attacker die > defender die (ties favor defender)

---

*For tool usage and agent strategies, see [agents/RiskAI.agent.md](agents/RiskAI.agent.md).*
