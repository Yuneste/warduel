# API Controller Package

This package contains REST API controllers for user management and game features.

## Planned Controllers:

### User & Auth
- **AuthController** - Login, registration, guest creation
  - POST /api/auth/register
  - POST /api/auth/login
  - POST /api/auth/guest
  - GET /api/auth/me

### Friends
- **FriendController** - Friend management
  - GET /api/friends
  - POST /api/friends/request
  - POST /api/friends/{id}/accept
  - DELETE /api/friends/{friendId}

### Game Features
- **InviteController** - Match invite system
  - POST /api/invites
  - POST /api/invites/{code}/accept
  - GET /api/invites/{code}

- **StatsController** - Player statistics
  - GET /api/stats/me
  - GET /api/stats/leaderboard

- **GameModeController** - Game mode selection
  - GET /api/game-modes
  - GET /api/game-modes/{id}

## Status: **Ready for implementation**
