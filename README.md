# ⚔️ WarDuel

A real-time multiplayer math quiz game built with Spring Boot and WebSockets.

## 🎮 Features

- Real-time multiplayer gameplay
- 20 random math questions per game
- Live score updates
- 60-second timer
- Rematch functionality
- Mobile-responsive design

## 🛠️ Tech Stack

**Backend:**
- Java 17
- Spring Boot 3.2.0
- WebSocket
- Lombok

**Frontend:**
- Vanilla JavaScript
- WebSocket API
- CSS3 (Glassmorphism design)

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Installation

1. Clone the repository:
```bash
git clone https://github.com/IHR_USERNAME/warduel.git
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

## 🎯 How to Play

1. Open the game in two browser windows (or on two devices on the same network)
2. Both players will be automatically matched
3. Answer math questions as fast as you can
4. Player with the highest score after 60 seconds wins!
5. Click "Rematch" to play again

## 📱 Mobile Support

The game is fully responsive and works on mobile devices. To play on mobile:
1. Find your PC's local IP address
2. Open `http://YOUR_IP:8080` on your mobile device

## 🐛 Known Issues

- Mobile WebSocket connections may disconnect intermittently
- Rematch functionality needs testing for multiple consecutive games

## 🤝 Contributing

Contributions are welcome! This is an early-stage project with room for improvement.

**Areas that need help:**
- Mobile WebSocket stability
- Rematch system improvements
- Question variety and difficulty levels
- UI/UX enhancements
- Testing

### How to Contribute

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built as a learning project
- Inspired by classic quiz games
- Thanks to the Spring Boot community

## 📞 Contact

Project Link: [https://github.com/yuneste/warduel](https://github.com/yuneste/warduel)

---

⭐ If you find this project interesting, please star it on GitHub!
