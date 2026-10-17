/**
 * Message Handlers
 * Handle incoming WebSocket messages
 */

import { gameState } from './gameState.js';
import { ui } from './uiController.js';
import { animations } from './animations.js';
import { gameActions } from './gameActions.js';

// How long the ✓ / ✗ next to the answer stays before the next question replaces it
const FEEDBACK_HOLD_MS = 350;
const RETURN_TO_LOBBY_MS = 2500;

let showingFeedbackUntil = 0;

export function handleMessage(message) {
    switch (message.type) {
        case 'GAME_STATE':
            handleGameState(message);
            break;
        case 'ROOM_CREATED':
            handleRoomCreated(message);
            break;
        case 'COUNTDOWN':
            handleCountdown(message);
            break;
        case 'QUESTION':
            handleQuestion(message);
            break;
        case 'SCORE_UPDATE':
            handleScoreUpdate(message);
            break;
        case 'GAME_OVER':
            handleGameOver(message);
            break;
        case 'REMATCH':
            handleRematch(message);
            break;
        case 'ERROR':
            handleError(message);
            break;
        default:
            console.warn('Unknown message type:', message.type);
    }
}

function handleRoomCreated(message) {
    const url = `${window.location.origin}/?room=${encodeURIComponent(message.roomCode)}`;
    ui.showRoomLink(message.roomCode, url);
}

function handleGameState(message) {
    gameState.currentGameState = message.gameStatus;
    if (message.yourName) ui.setNames(message.yourName, message.opponentName);
    if (message.challengeType) ui.setChallenge(message.challengeType);
    if (message.gameStatus === 'RUNNING') {
        gameState.opponentGone = false;
        ui.resetGameBoard();
        gameState.setGameEndTime(Number(message.remainingSeconds));
    }
}

function handleCountdown(message) {
    ui.showCountdown(message.countdown);
}

function handleQuestion(message) {
    const show = () => {
        ui.showGame();
        ui.updateQuestion(message);
        gameState.setGameEndTime(Number(message.remainingSeconds));
        startTimer();
    };

    // Let the player see whether their last answer was right before the board changes
    const wait = showingFeedbackUntil - Date.now();
    if (wait > 0) {
        setTimeout(show, wait);
    } else {
        show();
    }
}

function handleScoreUpdate(message) {
    ui.updateScores(message.yourScore, message.opponentScore);

    // wasCorrect is null when the update is about the opponent's answer
    if (message.wasCorrect !== null && message.wasCorrect !== undefined) {
        ui.showAnswerFeedback(message.wasCorrect);
        animations.vibrate(message.wasCorrect ? 15 : [30, 40, 30]);
        showingFeedbackUntil = Date.now() + FEEDBACK_HOLD_MS;
    }
}

function handleGameOver(message) {
    gameState.stopTimer();
    ui.stopTimerAnimation();
    gameState.currentGameState = 'FINISHED';
    gameState.rematchRequested = false;
    gameState.awaitingResult = false;
    // "Player 2 disconnected" already tells the player the opponent is gone
    gameState.opponentGone = / disconnected$/.test(message.disconnectMessage || '');

    // Keep the final answer's ✓ / ✗ visible for a moment, like between questions
    const wait = Math.max(0, showingFeedbackUntil - Date.now());
    setTimeout(() => {
        ui.updateGameOver(message);
        if (gameState.opponentGone) ui.disableRematch(); // "Opponent left" may arrive before this runs
        ui.showResult();
    }, wait);
}

function handleRematch(message) {
    ui.updateRematchStatus(message.opponentAccepted, message.statusMessage);
}

function handleError(message) {
    const text = message.errorMessage || '';
    console.error('Server error:', text);
    gameState.awaitingResult = false;

    if (text === 'Opponent left') {
        // After a game, while a rematch was being set up, or right after a disconnect win
        const alreadyExplained = gameState.opponentGone || gameState.currentGameState !== 'FINISHED';
        gameState.opponentGone = true;
        ui.disableRematch();
        if (!alreadyExplained) ui.showToast('Your opponent left the game.');
        return;
    }

    if (text === 'Room not found') {
        // Expired or already used invite link: back to the normal lobby
        gameActions.forgetInvite();
        gameActions.leaveQueue();
        ui.showToast('This invite link has expired or the game already started. Ask your friend for a new one.', 6000);
        return;
    }

    if (text === 'Opponent left the queue') {
        ui.showToast('Your opponent left before the start. Back to the lobby…', RETURN_TO_LOBBY_MS);
        setTimeout(() => location.reload(), RETURN_TO_LOBBY_MS);
        return;
    }

    ui.showError(text);
}

// Timer management
function startTimer() {
    gameState.startTimer(() => {
        const remaining = gameState.getRemainingTime();
        ui.updateTimer(remaining);

        if (remaining === 0) {
            gameState.stopTimer();
            ui.stopTimerAnimation();
        }
    });
}
