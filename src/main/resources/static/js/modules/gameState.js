/**
 * Game State Management
 * Centralized state for the game
 */

// Game settings rendered into <body data-*> by the server
const settings = document.body.dataset;

export const gameState = {
    // Settings from the server (fall back to the defaults if the page was opened without them)
    config: {
        questionsPerGame: Number(settings.questions) || 20,
        durationSeconds: Number(settings.duration) || 60,
        winScore: Number(settings.winScore) || 20
    },

    // Connection
    socket: null,
    isConnected: false,
    currentGameState: 'CONNECTING',

    // Timer
    timerInterval: null,
    gameEndTime: null,

    // Rematch
    rematchRequested: false,
    opponentGone: false, // the opponent left; rematch is impossible

    // Input
    currentAnswer: '',
    awaitingResult: false, // an answer was sent and its SCORE_UPDATE hasn't arrived yet

    // Getters
    getSocket() {
        return this.socket;
    },

    setSocket(socket) {
        this.socket = socket;
    },

    isSocketConnected() {
        return this.socket && this.socket.readyState === WebSocket.OPEN;
    },

    // Reset for new game
    reset() {
        this.isConnected = false;
        this.currentGameState = 'CONNECTING';
        this.currentAnswer = '';
        this.awaitingResult = false;
        this.rematchRequested = false;
        this.opponentGone = false;
        this.stopTimer();
    },

    // Timer management
    setGameEndTime(seconds) {
        this.gameEndTime = Date.now() + (seconds * 1000);
    },

    getRemainingTime() {
        if (!this.gameEndTime) return 0;
        return Math.max(0, Math.ceil((this.gameEndTime - Date.now()) / 1000));
    },

    startTimer(callback) {
        this.stopTimer();
        callback();
        this.timerInterval = setInterval(callback, 250);
    },

    stopTimer() {
        if (this.timerInterval) {
            clearInterval(this.timerInterval);
            this.timerInterval = null;
        }
    }
};
