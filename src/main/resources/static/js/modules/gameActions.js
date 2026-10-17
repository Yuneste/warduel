/**
 * Game Actions
 * High-level game actions (join, leave, give up, rematch, invite)
 */

import { gameState } from './gameState.js';
import { elements } from './domElements.js';
import { ui } from './uiController.js';
import { websocket } from './websocket.js';
import { storage } from './storage.js';

const FORFEIT_CONFIRM_MS = 3000;
const NAME_KEY = 'mathwars.name';
let forfeitTimeout = null;

function connectWith(joinRequest) {
    gameState.playerName = elements.playerName.value.trim();
    storage.set(NAME_KEY, gameState.playerName);
    gameState.joinRequest = joinRequest;
    ui.hideToast();
    websocket.connect();
}

export const gameActions = {
    // Fill in the nickname used last time on this device
    restoreName() {
        elements.playerName.value = storage.get(NAME_KEY, '');
    },

    // Public matchmaking
    startGame() {
        connectWith({ createRoom: false, roomCode: null });
    },

    // Private game: the server answers with a room code that becomes the invite link
    playFriend() {
        if (gameState.inviteCode) {
            // On an invite page this button offers the public queue instead
            this.forgetInvite();
            this.startGame();
            return;
        }
        connectWith({ createRoom: true, roomCode: null });
    },

    joinInvite() {
        connectWith({ createRoom: false, roomCode: gameState.inviteCode });
    },

    // Remove ?room=... so a reload or "back to menu" doesn't reuse a spent invite
    forgetInvite() {
        gameState.inviteCode = null;
        if (window.location.search) {
            history.replaceState(null, '', window.location.pathname);
        }
    },

    async copyLink() {
        const link = elements.roomLink.value;
        try {
            await navigator.clipboard.writeText(link);
        } catch {
            // Clipboard API unavailable (e.g. insecure context): select the text for manual copying
            elements.roomLink.select();
            document.execCommand('copy');
        }
        ui.confirmLinkCopied();
    },

    async shareLink() {
        try {
            await navigator.share({
                title: 'MathWars',
                text: `${gameState.playerName || 'A friend'} challenges you to a 60-second math duel`,
                url: elements.roomLink.value
            });
        } catch {
            // share sheet dismissed
        }
    },

    // Leave the queue (or an unanswered invite) before a match is found
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
        this.forgetInvite();
        ui.hideToast();
        ui.showLobby();
    },

    // Leave the finished game and go straight back into public matchmaking
    findNewOpponent() {
        websocket.close();
        gameState.reset();
        this.forgetInvite();
        ui.resetGameBoard();
        connectWith({ createRoom: false, roomCode: null });
    }
};
