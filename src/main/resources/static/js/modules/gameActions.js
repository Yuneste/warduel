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
            // In a running game - forfeit means instant loss
            console.log('Forfeiting game - you lose');

            // Stop timer and animations
            gameState.stopTimer();
            ui.stopTimerAnimation();

            // Set state to FINISHED to prevent error messages
            gameState.currentGameState = 'FINISHED';
            gameState.isForfeiting = false;
            gameState.justShowedResult = true;  // Flag that we just showed result

            // Send forfeit to server (opponent will win)
            websocket.send({
                type: 'FORFEIT'
            });

            // Show defeat screen immediately
            const fakeGameOverMessage = {
                yourScore: 0,  // Doesn't matter, you lost by forfeiting
                opponentScore: 999,
                youWon: false,
                isDraw: false,
                winnerName: 'Opponent'
            };

            ui.showResult();
            ui.updateGameOver(fakeGameOverMessage);
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
