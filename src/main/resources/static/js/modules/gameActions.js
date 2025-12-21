/**
 * Game Actions
 * High-level game actions (join, leave, rematch)
 */

import { gameState } from './gameState.js';
import { ui } from './uiController.js';
import { websocket } from './websocket.js';

export const gameActions = {
    // Start game (connect and join matchmaking)
    startGame() {
        console.log('Starting game...');
        websocket.connect();
    },

    // Leave game/queue
    leaveGame() {
        console.log('Leaving game...');

        // Stop timer
        gameState.stopTimer();
        ui.stopTimerAnimation();

        // Close WebSocket connection
        websocket.close();

        // Reset state
        gameState.reset();

        // Show lobby
        ui.showLobby();
    },

    // Request rematch
    requestRematch() {
        if (!gameState.isSocketConnected()) {
            console.error('Rematch failed: No connection');
            ui.showError('Connection lost! Returning to lobby...');

            // Return to lobby after showing error
            setTimeout(() => {
                location.reload();
            }, 2000);
            return;
        }

        if (gameState.rematchRequested) {
            console.log('Rematch already requested');
            return;
        }

        console.log('Requesting rematch...');
        gameState.rematchRequested = true;

        // Update UI
        ui.setRematchWaiting();

        // Send request
        websocket.send({
            type: 'REMATCH',
            requestRematch: true,
            opponentAccepted: false,
            statusMessage: ''
        });
    }
};

// Make requestRematch available globally for onclick handler
window.requestRematch = gameActions.requestRematch.bind(gameActions);
