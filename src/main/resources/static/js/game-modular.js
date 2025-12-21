/**
 * WarDuel - Main Game Entry Point
 * Modular architecture for better maintainability
 */

import { gameState } from './modules/gameState.js';
import { elements } from './modules/domElements.js';
import { ui } from './modules/uiController.js';
import { websocket } from './modules/websocket.js';
import { inputHandler } from './modules/inputHandler.js';
import { animations } from './modules/animations.js';
import { gameActions } from './modules/gameActions.js';

// ==================== INITIALIZATION ====================

window.addEventListener('DOMContentLoaded', () => {
    console.log('WarDuel starting...');
    initializeGame();
});

function initializeGame() {
    // Setup input handlers
    inputHandler.setupNumberPad();

    // Setup button event listeners
    setupButtons();

    // Start animations
    animations.startPointingHand();

    console.log('Game initialized successfully');
}

// ==================== BUTTON SETUP ====================

function setupButtons() {
    // Play button
    if (elements.playButton) {
        animations.addHapticFeedback(elements.playButton);
        elements.playButton.addEventListener('click', () => {
            console.log('Play button clicked');
            gameActions.startGame();
        });
    }

    // Leave waiting button
    if (elements.leaveWaitingButton) {
        animations.addHapticFeedback(elements.leaveWaitingButton);
        elements.leaveWaitingButton.addEventListener('click', () => {
            console.log('Leave waiting button clicked');
            gameActions.leaveGame();
        });
    }

    // Forfeit button
    if (elements.forfeitButton) {
        animations.addHapticFeedback(elements.forfeitButton);
        elements.forfeitButton.addEventListener('click', () => {
            console.log('Forfeit button clicked');
            gameActions.leaveGame();
        });
    }
}

// ==================== CLEANUP ====================

window.addEventListener('beforeunload', () => {
    gameState.stopTimer();
    websocket.close();
});
