/**
 * Game Actions
 * High-level game actions (join, leave, give up, rematch)
 */

import { gameState } from './gameState.js';
import { ui } from './uiController.js';
import { websocket } from './websocket.js';

const FORFEIT_CONFIRM_MS = 3000;
let forfeitTimeout = null;

export const gameActions = {
    // Connect and join matchmaking
    startGame() {
        ui.hideToast();
        websocket.connect();
    },

    // Leave the queue before a match is found
    leaveQueue() {
        gameState.stopTimer();
        websocket.close();
        gameState.reset();
        ui.showLobby();
    },

    // Giving up needs a second tap within a few seconds, so a stray tap on a phone can't end the game
    forfeit() {
        if (gameState.currentGameState !== 'RUNNING') return;

        if (!forfeitTimeout) {
            ui.armForfeitButton();
            forfeitTimeout = setTimeout(() => this.disarmForfeit(), FORFEIT_CONFIRM_MS);
            return;
        }

        this.disarmForfeit();
        websocket.send({ type: 'FORFEIT' });
    },

    disarmForfeit() {
        clearTimeout(forfeitTimeout);
        forfeitTimeout = null;
        ui.resetForfeitButton();
    },

    requestRematch() {
        if (!websocket.forceConnectionCheck() || gameState.rematchRequested) return;

        gameState.rematchRequested = true;
        ui.setRematchWaiting();
        websocket.send({ type: 'REMATCH', requestRematch: true });
    },

    // Leave the finished game and return to the start screen (the opponent is told we left)
    backToMenu() {
        websocket.close();
        gameState.reset();
        ui.hideToast();
        ui.showLobby();
    },

    // Leave the finished game and go straight back into matchmaking
    findNewOpponent() {
        websocket.close();
        gameState.reset();
        ui.resetGameBoard();
        websocket.connect();
    }
};
