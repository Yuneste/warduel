/**
 * MathWars - Main Game Entry Point
 * Modular architecture for better maintainability
 */

import { gameState } from './modules/gameState.js';
import { elements } from './modules/domElements.js';
import { ui } from './modules/uiController.js';
import { websocket } from './modules/websocket.js';
import { inputHandler } from './modules/inputHandler.js';
import { animations } from './modules/animations.js';
import { gameActions } from './modules/gameActions.js';

// Modules load deferred, so the DOM is ready here
inputHandler.setup();
setupButtons();
websocket.setupVisibilityDetection();
ui.showLobby();

function setupButtons() {
    const buttons = [
        [elements.playButton, () => gameActions.startGame()],
        [elements.leaveWaitingButton, () => gameActions.leaveQueue()],
        [elements.forfeitButton, () => gameActions.forfeit()],
        [elements.rematchButton, () => gameActions.requestRematch()],
        [elements.newGameButton, () => gameActions.findNewOpponent()]
    ];

    for (const [button, action] of buttons) {
        animations.addHapticFeedback(button);
        button.addEventListener('click', action);
    }
}

window.addEventListener('beforeunload', () => {
    gameState.stopTimer();
    websocket.close();
});
