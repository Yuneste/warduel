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

        if (gameState.currentGameState === 'RUNNING') {
            // In a running game - send forfeit and wait for GAME_OVER response from server
            console.log('Forfeiting game - waiting for server response');

            // Disable forfeit button to prevent multiple clicks
            const forfeitButton = document.getElementById('forfeit-button');
            if (forfeitButton) {
                forfeitButton.disabled = true;
                forfeitButton.textContent = 'Forfeiting...';
            }

            websocket.send({
                type: 'FORFEIT'
            });
            // Server will send GAME_OVER message showing the loss
            // UI will be updated when GAME_OVER arrives - don't touch it here
        } else {
            // In queue/waiting - just leave immediately
            gameState.stopTimer();
            ui.stopTimerAnimation();
            websocket.close();
            gameState.reset();
            ui.showLobby();
        }
    },

    // Request rematch
    requestRematch() {
        // Force check connection before allowing rematch
        if (!websocket.forceConnectionCheck()) {
            return; // forceConnectionCheck already shows error and reloads
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
