/**
 * WebSocket Connection Manager
 * Handles all WebSocket communication
 */

import { gameState } from './gameState.js';
import { elements } from './domElements.js';
import { ui } from './uiController.js';
import { handleMessage } from './messageHandlers.js';

const HEARTBEAT_MS = 5000;   // server closes connections that are silent for 10s
const RELOAD_DELAY_MS = 2000;

// Track if connection was intentionally closed
let intentionalClose = false;
let heartbeatInterval = null;

export const websocket = {
    connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const socket = new WebSocket(`${protocol}//${window.location.host}/api/game`);
        gameState.setSocket(socket);
        intentionalClose = false;
        ui.setLobbyBusy(true);

        // Events of a replaced socket (e.g. after "Find a new opponent") are ignored
        const current = handler => event => {
            if (socket === gameState.getSocket()) handler.call(this, event);
        };
        socket.onopen = current(this.handleOpen);
        socket.onmessage = current(this.handleMessage);
        socket.onerror = current(this.handleError);
        socket.onclose = current(this.handleClose);
    },

    handleOpen() {
        gameState.isConnected = true;
        const { createRoom, roomCode, challengeType, playerToken } = gameState.joinRequest;
        this.send({ type: 'JOIN_GAME', playerName: gameState.playerName, createRoom, roomCode, challengeType, playerToken });
        ui.showWaiting(createRoom ? 'room' : roomCode ? 'challenge' : 'queue');
        this.startHeartbeat();
    },

    handleMessage(event) {
        try {
            handleMessage(JSON.parse(event.data));
        } catch (error) {
            console.error('Error handling message:', error);
        }
    },

    handleError(event) {
        console.error('WebSocket error:', event);
        if (!gameState.isConnected) {
            // Never got connected: stay in the lobby and let the player retry
            ui.setLobbyBusy(false);
            ui.showToast("Couldn't reach the game server. Try again in a moment.");
        }
    },

    handleClose() {
        const wasConnected = gameState.isConnected;
        gameState.isConnected = false;
        this.stopHeartbeat();

        if (intentionalClose || !wasConnected || gameState.currentGameState === 'FINISHED') return;
        this.returnToLobby('Connection lost. Back to the lobby…');
    },

    send(message) {
        if (gameState.isSocketConnected()) {
            gameState.getSocket().send(JSON.stringify(message));
        } else {
            ui.showError('Not connected to the game server.');
        }
    },

    close() {
        intentionalClose = true;
        this.stopHeartbeat();
        const socket = gameState.getSocket();
        this.announceLeaving();
        if (socket && socket.readyState <= WebSocket.OPEN) {
            socket.close();
        }
    },

    // The server hears about a closed socket only seconds later, so say it first
    announceLeaving() {
        const socket = gameState.getSocket();
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ type: 'LEAVE' }));
        }
    },

    returnToLobby(message) {
        ui.showToast(message, 0);
        setTimeout(() => location.reload(), RELOAD_DELAY_MS);
    },

    // Phones kill sockets while locked; check again when the page becomes visible
    setupVisibilityDetection() {
        // Closing the tab, reloading or navigating away
        window.addEventListener('pagehide', () => this.announceLeaving());

        document.addEventListener('visibilitychange', () => {
            if (document.hidden || intentionalClose) return;
            const socket = gameState.getSocket();
            const dead = socket && socket.readyState >= WebSocket.CLOSING;
            if (dead && gameState.currentGameState !== 'CONNECTING') {
                this.returnToLobby('Connection lost while the screen was off. Back to the lobby…');
            }
        });
    },

    // Call before actions that need the server
    forceConnectionCheck() {
        if (gameState.isSocketConnected()) return true;
        this.returnToLobby('Connection lost. Back to the lobby…');
        return false;
    },

    startHeartbeat() {
        this.stopHeartbeat();
        heartbeatInterval = setInterval(() => {
            if (gameState.isSocketConnected()) {
                this.send({ type: 'HEARTBEAT' });
            } else if (!intentionalClose) {
                this.stopHeartbeat();
                this.returnToLobby('Connection lost. Back to the lobby…');
            }
        }, HEARTBEAT_MS);
    },

    stopHeartbeat() {
        clearInterval(heartbeatInterval);
        heartbeatInterval = null;
    }
};
