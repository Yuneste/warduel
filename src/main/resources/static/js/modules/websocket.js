/**
 * WebSocket Connection Manager
 * Handles all WebSocket communication
 */

import { gameState } from './gameState.js';
import { ui } from './uiController.js';
import { handleMessage } from './messageHandlers.js';

// Track if connection was intentionally closed
let intentionalClose = false;

// Heartbeat interval
let heartbeatInterval = null;

export const websocket = {
    // Connect to server
    connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/game`;

        console.log('Connecting to:', wsUrl);
        ui.updateStatus('Connecting to server...');

        try {
            const socket = new WebSocket(wsUrl);
            gameState.setSocket(socket);

            socket.onopen = this.handleOpen.bind(this);
            socket.onmessage = this.handleMessage.bind(this);
            socket.onerror = this.handleError.bind(this);
            socket.onclose = this.handleClose.bind(this);
        } catch (error) {
            console.error('WebSocket error:', error);
            ui.showError('Connection error!');
        }
    },

    // Connection opened
    handleOpen() {
        console.log('WebSocket connected');
        gameState.isConnected = true;
        intentionalClose = false; // Reset flag on successful connection
        ui.showWaiting();

        // Start heartbeat
        this.startHeartbeat();
    },

    // Message received
    handleMessage(event) {
        try {
            const message = JSON.parse(event.data);
            console.log('🔵 Received:', message.type, message);
            handleMessage(message);
        } catch (error) {
            console.error('Error parsing message:', error);
        }
    },

    // Connection error
    handleError(event) {
        console.error('WebSocket error:', event);
        ui.showError('Connection error!');
    },

    // Connection closed
    handleClose(event) {
        console.log('WebSocket closed:', event);
        gameState.isConnected = false;

        // Don't show error if:
        // - Game is finished
        // - We're forfeiting
        // - We just showed a result screen (forfeit, game over)
        if (gameState.currentGameState !== 'FINISHED' &&
            !gameState.isForfeiting &&
            !gameState.justShowedResult) {
            ui.showError('Connection lost!');
        } else {
            console.log('Connection closed after game ended - this is normal');
        }
    },

    // Send message to server
    send(message) {
        if (gameState.isSocketConnected()) {
            gameState.getSocket().send(JSON.stringify(message));
        } else {
            console.error('Cannot send - not connected');
            ui.showError('No connection!');
        }
    },

    // Close connection
    close() {
        intentionalClose = true;
        this.stopHeartbeat(); // Stop heartbeat before closing
        const socket = gameState.getSocket();
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.close();
        }
    },

    // Check if connection is alive
    isConnected() {
        return gameState.isSocketConnected();
    },

    // Setup page visibility detection (for mobile lock/unlock)
    setupVisibilityDetection() {
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) {
                // Page became visible (user unlocked phone or switched back to tab)
                console.log('Page became visible, checking connection...');
                this.checkConnectionOnVisibilityChange();
            }
        });
    },

    // Check connection when page becomes visible
    checkConnectionOnVisibilityChange() {
        const socket = gameState.getSocket();

        console.log('Checking connection:', {
            hasSocket: !!socket,
            readyState: socket ? socket.readyState : 'no socket',
            intentionalClose,
            currentGameState: gameState.currentGameState
        });

        // Check if connection is dead
        const isDisconnected = !socket ||
                             socket.readyState === WebSocket.CLOSED ||
                             socket.readyState === WebSocket.CLOSING;

        if (isDisconnected && !intentionalClose) {
            console.warn('Connection lost! Socket state:', socket ? socket.readyState : 'null');

            // Always reload if we were in a game or waiting
            if (gameState.currentGameState !== 'CONNECTING') {
                ui.showError('Connection lost. Returning to lobby...');

                setTimeout(() => {
                    location.reload();
                }, 2000);
            }
        } else if (socket && socket.readyState === WebSocket.OPEN) {
            console.log('Connection is alive');
        }
    },

    // Force check connection state (call before important actions)
    forceConnectionCheck() {
        const socket = gameState.getSocket();

        if (!socket || socket.readyState !== WebSocket.OPEN) {
            console.error('Connection check failed!');
            ui.showError('Connection lost! Returning to lobby...');

            setTimeout(() => {
                location.reload();
            }, 2000);

            return false;
        }

        return true;
    },

    // Start heartbeat to detect silent disconnections
    startHeartbeat() {
        this.stopHeartbeat(); // Clear any existing heartbeat

        console.log('🔥 Heartbeat started - checking connection and sending pings every 5 seconds');

        // Check connection and send heartbeat every 5 seconds
        heartbeatInterval = setInterval(() => {
            const socket = gameState.getSocket();
            const readyState = socket ? socket.readyState : -1;

            // WebSocket.OPEN = 1, CLOSED = 3, CLOSING = 2
            if (!socket || readyState !== 1) {
                console.error('💀 Heartbeat detected disconnection! ReadyState:', readyState);
                this.stopHeartbeat();

                // Only show error if not intentionally closed and not in lobby
                if (!intentionalClose) {
                    console.error('Forcing reload due to disconnection');
                    ui.showError('Connection lost! Returning to lobby...');

                    setTimeout(() => {
                        location.reload();
                    }, 1500);
                }
            }
            else {
                // Connection is alive - send heartbeat to server
                // This keeps the connection alive even if player doesn't answer questions
                try {
                    this.send({ type: 'HEARTBEAT' });

                    // Only log every ~20% of the time to reduce noise
                    if (Math.random() < 0.2) {
                        console.log('💚 Heartbeat: Connection alive, ping sent');
                    }
                } catch (error) {
                    console.error('Error sending heartbeat:', error);
                }
            }
        }, 5000); // Check and ping every 5 seconds
    },

    // Stop heartbeat
    stopHeartbeat() {
        if (heartbeatInterval) {
            clearInterval(heartbeatInterval);
            heartbeatInterval = null;
        }
    }
};
