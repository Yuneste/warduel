/**
 * Game State Management
 * Centralized state for the game
 */

export const gameState = {
    // Connection
    socket: null,
    isConnected: false,
    currentGameState: 'CONNECTING',

    // Timer
    timerInterval: null,
    gameEndTime: null,

    // Rematch
    rematchRequested: false,
    opponentWantsRematch: false,

    // Forfeit
    isForfeiting: false,

    // Input
    currentAnswer: '',

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
        this.rematchRequested = false;
        this.opponentWantsRematch = false;
        this.isForfeiting = false;
        this.stopTimer();
    },

    // Timer management
    setGameEndTime(seconds) {
        this.gameEndTime = Date.now() + (seconds * 1000);
    },

    getRemainingTime() {
        if (!this.gameEndTime) return 0;
        return Math.max(0, Math.floor((this.gameEndTime - Date.now()) / 1000));
    },

    startTimer(callback) {
        this.stopTimer();
        this.timerInterval = setInterval(callback, 1000);
    },

    stopTimer() {
        if (this.timerInterval) {
            clearInterval(this.timerInterval);
            this.timerInterval = null;
        }
    }
};
