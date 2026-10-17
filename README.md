# MathWars

[![CI](https://github.com/Yuneste/warduel/actions/workflows/ci.yml/badge.svg)](https://github.com/Yuneste/warduel/actions/workflows/ci.yml)

Real-time multiplayer math duel: two players race to solve 20 problems in 60 seconds, on a chalkboard.

![Gameplay on a phone: the score is drawn as tally marks, a wrong answer gets crossed out, and giving up needs a second tap](docs/screenshots/gameplay.png)

| Desktop | Every ending |
|---|---|
| ![Desktop: framed chalkboard with the question on the left and the number pad on the right](docs/screenshots/desktop.png) | ![Win, lose and draw screens with final tally marks](docs/screenshots/results.png) |

## Features

- Real-time 1v1 matches via WebSocket: random opponent, or a friend through a private invite link
- Nicknames, and post-game stats with every answer and how long it took
- Four challenge types: Classic, True or false, Missing number and Which is bigger (fractions, powers, roots); problems get harder through the game and never repeat
- 60-second rounds with live score updates
- Rematch system for consecutive games
- Chalkboard design built phone-first: scores drawn as tally marks, on-screen number pad with haptic feedback
- Full keyboard play on desktop (digits, Backspace, minus, Enter)

## Tech Stack

**Backend:**
- Java 17+ (tested on 17 and 25) + Spring Boot 3.5
- WebSocket (TextWebSocketHandler)
- Concurrent game management with thread safety
- Rate limiting and connection monitoring

**Frontend:**
- Vanilla JavaScript ES6 modules
- WebSocket API for real-time communication
- Hand-written CSS with design tokens, no framework
- Self-hosted fonts (no third-party requests)

## Quick Start

### Prerequisites
- Java 17+ (Maven is included via the wrapper)

### Run Locally

```bash
git clone https://github.com/Yuneste/warduel.git
cd warduel
./mvnw spring-boot:run
```

Open `http://localhost:8080` in two browser windows to play.

### Tests

```bash
./mvnw test
```

Besides unit tests for the game rules, matchmaking and message format, `GameWebSocketIntegrationTest` starts the server on a random port and plays real matches with two WebSocket clients: countdown, timer, win score, rematch, forfeit, disconnects and rate limiting.

## Project Structure

```
src/main/java/com/warduel/warduel/
├── config/           # WebSocket, Security, Game configuration
├── websocket/        # WebSocket handler (core game logic)
├── service/          # GameService, QuestionGenerator
├── model/            # GameSession, Player, Question
├── dto/              # WebSocket message types
└── controller/       # HomeController

src/main/resources/
├── static/
│   ├── css/         # Styling with animations
│   └── js/
│       ├── game-modular.js      # Entry point
│       └── modules/             # State, UI, WebSocket, Input handlers
└── templates/
    └── index.html   # Single-page app
```

## How It Works

1. Players connect via WebSocket
2. Matchmaking pairs two players in queue
3. 3-second countdown with tips
4. 60 seconds to answer 20 questions
5. Real-time score synchronization
6. Winner determined (20 points or highest score)
7. Option to rematch or return to lobby

## Key Features

**Backend:**
- Thread-safe matchmaking with `ConcurrentHashMap` and `synchronized` blocks
- Async task scheduling with `ScheduledExecutorService` (no blocking)
- Rate limiting (10 messages/second per player)
- Connection timeout detection (10 seconds)
- Race condition protection for game start and rematch

**Frontend:**
- Modular ES6 architecture (state, UI, actions separated)
- Heartbeat mechanism (5-second pings) for mobile stability
- Number pad input with validation
- Visual feedback (timer breathing, answer blink effects)

## License

MIT License - See [LICENSE](LICENSE) file for details.

---

⭐ Star if you find this project interesting!
