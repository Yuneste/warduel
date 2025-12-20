# WarDuel

A real-time multiplayer math quiz game built with Spring Boot and WebSockets.

🎮 **[Play Live Demo](https://warduel-production.up.railway.app/)**

## Screenshots

<p align="center">
  <img src="screenshots/Screenshot%202025-12-20%20231511.png" width="45%" alt="Lobby Screen" />
  <img src="screenshots/Screenshot%202025-12-20%20233923.png" width="45%" alt="Gameplay" />
</p>

## Features

**Gameplay:**
- Real-time 1v1 multiplayer matches
- 20 random math questions per game (addition, subtraction, multiplication, division)
- 60-second rounds with visual timer warnings
- Live score synchronization
- First to 20 points or highest score at time-up wins
- Rematch system for consecutive games

**User Experience:**
- Click-to-play lobby (no auto-matching)
- Modern green/orange/white color scheme
- Smooth animations and visual feedback
- Answer feedback with color-coded blink effects (green = correct, red = wrong)
- Timer breathing animation at ≤5 seconds
- Forfeit/leave game functionality
- Mobile haptic feedback support
- Fully responsive design for desktop and mobile

**Technical:**
- Comprehensive test suite (200+ tests)
- Thread-safe concurrent game management
- WebSocket-based real-time communication
- No page reloads - single-page experience

## Tech Stack

**Backend:**
- Java 17
- Spring Boot 3.2.0
- WebSocket (TextWebSocketHandler)
- Lombok
- Jackson (JSON serialization)
- JUnit 5 + Mockito (testing)

**Frontend:**
- Vanilla JavaScript (ES6+)
- WebSocket API
- CSS3 animations
- Responsive design

## Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yuneste/warduel.git
cd warduel
```

2. Run the application:
```bash
mvn spring-boot:run
```

3. Open your browser:
```
http://localhost:8080
```

## How to Play

1. **Open the game** in two browser windows (or on two devices)
2. **Click "Jetzt spielen"** on both devices to enter matchmaking
3. **Players are matched** automatically when two players are queued
4. **Answer math questions** as fast as you can
   - Type your answer using the number pad
   - Use `<` to clear, `−` to toggle negative
   - Submit with the green `✓` button
5. **Win condition:** First to 20 points OR highest score after 60 seconds
6. **Play again:** Click "Rematch" or "Neues Spiel"
7. **Leave anytime:** Click "Aufgeben" (forfeit) or "Warteschlange verlassen" (leave queue)

## Mobile Support

The game is fully responsive and includes mobile-specific features:
- Touch-optimized controls
- Haptic feedback (vibration) on button taps
- Prevents double-tap zoom during gameplay
- Optimized animations for mobile performance

To play on mobile:
1. Find your PC's local IP address
2. Open `http://YOUR_IP:8080` on your mobile device
3. Both devices should be on the same network

## Project Structure

```
src/main/java/com/warduel/warduel/
├── dto/              # Data Transfer Objects (messages)
├── model/            # Game entities (GameSession, Player, Question)
├── service/          # Business logic (GameService, QuestionGenerator)
└── websocket/        # WebSocket handler

src/main/resources/
├── static/
│   ├── css/         # Styles with animations
│   └── js/          # Client-side game logic
└── templates/       # HTML templates

src/test/java/       # Comprehensive test suite
```

## Contributing

This is an active project with planned features. Contributions are welcome!

**Upcoming Features:**
- User authentication and profiles
- Friend system (play with specific people)
- Multiple game modes (speed mode, survival, etc.)
- Statistics and leaderboards
- Match history
- Different difficulty levels

**Current Priorities:**
- User system foundation
- Friend invites and private matches
- Enhanced matchmaking

### How to Contribute

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Development

**Run tests:**
```bash
mvn test
```

**Build:**
```bash
mvn clean package
```

**Run with custom port:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built as a learning project to explore real-time multiplayer game architecture
- Designed with inspiration from modern quiz games like Brilliant
- Thanks to the Spring Boot and WebSocket communities

## Links

- 🎮 **Live Demo:** [https://warduel-production.up.railway.app/](https://warduel-production.up.railway.app/)
- 📦 **GitHub:** [https://github.com/yuneste/warduel](https://github.com/yuneste/warduel)

---

⭐ Star this project if you find it interesting!
