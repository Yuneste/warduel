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
import { sound } from './modules/sound.js';

// Modules load deferred, so the DOM is ready here
inputHandler.setup();
setupButtons();
websocket.setupVisibilityDetection();
gameActions.restoreName();
ui.showLobby();
setupSound();

// Browsers only allow audio after a user gesture
function setupSound() {
    const unlock = () => sound.unlock();
    document.addEventListener('pointerdown', unlock, { capture: true });
    document.addEventListener('keydown', unlock, { capture: true });

    const render = () => {
        const muted = sound.isMuted();
        elements.muteButton.setAttribute('aria-pressed', String(muted));
        elements.muteButton.setAttribute('aria-label', muted ? 'Sound off' : 'Sound on');
        elements.muteButton.title = muted ? 'Sound off' : 'Sound on';
    };
    elements.muteButton.addEventListener('click', () => {
        sound.setMuted(!sound.isMuted());
        render();
        sound.tap();
    });
    render();
}

function setupButtons() {
    const buttons = [
        [elements.playButton, () => gameActions.startGame()],
        [elements.joinInviteButton, () => gameActions.joinInvite()],
        [elements.friendButton, () => gameActions.playFriend()],
        [elements.createRoomButton, () => gameActions.createRoom()],
        [elements.leaderboardButton, () => gameActions.openLeaderboard()],
        [elements.leaderboardBackButton, () => gameActions.closeLeaderboard()],
        [elements.friendBackButton, () => gameActions.closeFriendOptions()],
        [elements.leaveWaitingButton, () => gameActions.leaveQueue()],
        [elements.leaveRoomButton, () => gameActions.leaveQueue()],
        [elements.copyLinkButton, () => gameActions.copyLink()],
        [elements.shareLinkButton, () => gameActions.shareLink()],
        [elements.forfeitButton, () => gameActions.forfeit()],
        [elements.rematchButton, () => gameActions.requestRematch()],
        [elements.newGameButton, () => gameActions.findNewOpponent()],
        [elements.menuButton, () => gameActions.backToMenu()]
    ];

    for (const [button, action] of buttons) {
        animations.addHapticFeedback(button);
        button.addEventListener('click', action);
    }

    // Enter in the name field starts the main action of the lobby
    elements.playerName.addEventListener('keydown', event => {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        (gameState.inviteCode ? elements.joinInviteButton : elements.playButton).click();
    });
}

window.addEventListener('beforeunload', () => {
    gameState.stopTimer();
    websocket.close();
});
