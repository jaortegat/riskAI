# 🎮 How to Play RiskAI

A guide to creating, joining, and playing games in RiskAI.

## Creating a Game

1. Click **"Create New Game"** on the lobby page
2. Fill in the game settings:
   - **Game Name** — a name for your match
   - **Your Player Name** — how other players will see you
   - **Map** — choose a map (Classic World or Europe)
   - **Game Mode** — Classic, Domination, or Turn Limit
   - **Max Players** — 2 to 6 players
3. Click **"Create"** to enter the game lobby
4. Optionally add CPU players with a difficulty level (Easy, Medium, Hard)
5. Share the game link with friends or wait for others to join
6. Click **"Start Game"** when at least 2 players have joined

## Joining a Game

1. Browse available games on the lobby page
2. Click **"Join"** on any game showing "Waiting for Players"
3. Enter your player name
4. Wait for the host to start the game

## Maps

### Classic World Map
- **42 territories** across **6 continents**
- Best for 4–6 players
- Game length: 60–120 minutes

| Continent | Territories | Bonus | Borders | Difficulty |
|-----------|:-----------:|:-----:|:-------:|------------|
| Australia | 4 | +2 | 1 | ⭐ Easiest |
| South America | 4 | +2 | 2 | ⭐⭐ Easy |
| Africa | 6 | +3 | 3 | ⭐⭐⭐ Moderate |
| North America | 9 | +5 | 3 | ⭐⭐⭐ Moderate |
| Europe | 7 | +5 | 4 | ⭐⭐⭐⭐ Hard |
| Asia | 12 | +7 | 6 | ⭐⭐⭐⭐⭐ Hardest |

### Europe Map
- **24 territories** across **4 regions**
- Best for 2–4 players
- Game length: 30–60 minutes

| Region | Territories | Bonus | Borders | Difficulty |
|--------|:-----------:|:-----:|:-------:|------------|
| Northern Europe | 5 | +3 | 3 | ⭐⭐ Easy |
| Western Europe | 6 | +4 | 3 | ⭐⭐⭐ Moderate |
| Central Europe | 7 | +5 | 4 | ⭐⭐⭐⭐ Hard |
| Eastern Europe | 6 | +5 | 4 | ⭐⭐⭐⭐ Hard |

## Game Modes

### Classic
Eliminate all other players by conquering every territory on the map. The last player standing wins.

### Domination
First player to control a target percentage of the map wins (default **70%**). The game checks after each attack — if you hit the threshold, you win immediately.

### Turn Limit
The player with the most territories after a fixed number of turns wins. If tied, the first player in turn order among those tied wins. Plan carefully — every territory counts.

## Turn Structure

Each turn consists of three phases, always in order:

### 1. Reinforcement Phase

You receive armies at the start of your turn and must place them before doing anything else.

**How many armies do you get?**
- **Base:** the greater of 3 or (territories owned ÷ 3, rounded down)
- **Continent bonuses:** if you control every territory in a continent, add its bonus

**Examples** (Classic World map):
| Territories | Continents Held | Armies Received |
|:-----------:|-----------------|:---------------:|
| 8 | None | 3 (minimum) |
| 12 | Australia | 4 + 2 = **6** |
| 20 | South America + Africa | 6 + 2 + 3 = **11** |

**How to place:**
- Click on any territory you own to place armies there
- You can split armies across multiple territories
- Your turn advances to the Attack phase once all armies are placed

### 2. Attack Phase

Conquer enemy territories by attacking from your own.

**Rules:**
- You can only attack **from** a territory with **2+ armies** (at least 1 must stay behind)
- You can only attack **adjacent** territories owned by other players
- You choose how many armies to attack with: **1, 2, or 3**
- You can attack as many times as you want
- Click **"End Attack"** when you're done (or if you don't want to attack at all)

**Combat resolution:**

| Attacking Armies | Attacker Dice | Defending Armies | Defender Dice |
|:----------------:|:-------------:|:----------------:|:-------------:|
| 1 | 1 dice | 1 | 1 dice |
| 2 | 2 dice | 2+ | 2 dice |
| 3+ | 3 dice | | |

1. Both sides roll their dice
2. Compare the **highest** die from each side — loser removes 1 army
3. If both rolled 2+ dice, also compare the **second-highest** — loser removes 1 army
4. **Ties go to the defender**

**Example:**
- Attacker rolls **[5, 4, 2]**, Defender rolls **[6, 3]**
- First comparison: 6 > 5 → attacker loses 1 army
- Second comparison: 4 > 3 → defender loses 1 army
- Result: both sides lose 1 army

**Conquest:**
- If the defender loses all armies, you conquer the territory
- Your attacking armies automatically move into the conquered territory
- If a player loses their last territory, they are **eliminated**

### 3. Fortify Phase

Reposition your armies after combat.

- Choose **one** of your territories with 2+ armies
- Move any number of armies (leaving at least 1 behind) to an **adjacent territory you own**
- You can only fortify **once per turn**
- Click **"Skip"** if you don't want to move armies

After fortifying or skipping, your turn ends and the next player begins.

## Phase Summary

| Phase | What to Do | Ends When |
|-------|-----------|-----------|
| **Reinforcement** | Place armies on your territories | All armies placed |
| **Attack** | Attack neighbors or pass | You click "End Attack" |
| **Fortify** | Move armies once, or skip | You move or skip |

## CPU Opponents

CPU players take their turns automatically. Three difficulty levels are available:

| Difficulty | Playstyle |
|------------|-----------|
| **Easy** | Random decisions, rarely attacks, minimal strategy |
| **Medium** | Balanced approach, moderate aggression |
| **Hard** | Strategic continent targeting, prioritized attacks, intelligent fortification |

## AI Agent Players

AI agents (such as GitHub Copilot) can join and play games through the MCP (Model Context Protocol) server endpoint at `/mcp`. They appear as regular players and take turns like everyone else.

To play against an AI agent:
1. Create a game from the web UI
2. Have the AI agent call `joinGame` via MCP
3. Start the game once all players have joined

## Tips & Strategy

- **Secure a continent early** — continent bonuses are the key to snowballing
- **Small continents are easier to hold** — Australia (1 border) and South America (2 borders) are great starting targets
- **Don't spread too thin** — it's better to have 5 armies on 2 borders than 1 army on 10
- **Attack with 3 armies** whenever possible — more dice = better odds
- **Watch your opponents** — break their continent bonuses before they snowball
- **Fortify toward the front** — armies in the interior don't help you
- **Know when to stop attacking** — leaving yourself with 1 army everywhere is a recipe for disaster
