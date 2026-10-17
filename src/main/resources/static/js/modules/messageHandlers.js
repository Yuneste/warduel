/**
 * Message Handlers
 * Handle incoming WebSocket messages
 */

import { gameState } from './gameState.js';
import { ui } from './uiController.js';
import { animations } from './animations.js';
import { gameActions } from './gameActions.js';
import { sound } from './sound.js';
import { effects } from './effects.js';
import { elements } from './domElements.js';

const STREAK_CALLOUTS = [3, 5, 10, 15];
let streak = 0;
let lastOpponentScore = 0;
let lastTickSecond = null;

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
        case 'CHALLENGE':
            handleChallenge(message);
            break;
        case 'CHALLENGE_STATUS':
            handleChallengeStatus(message);
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

// Host: someone opened the invite link
function handleChallenge(message) {
    ui.showChallengePopup(message.fromName, message.timeoutSeconds);
    sound.challenge();
    animations.vibrate([60, 60, 60]);
}

function handleChallengeStatus(message) {
    const name = message.name || 'Your friend';
    const backToLobby = text => {
        ui.stopChallengeCountdown();
        gameActions.leaveQueue();
        ui.showToast(text, 6000);
    };
    switch (message.status) {
        // challenger side
        case 'SENT':
            gameState.inviteHost = message.name || null;
            ui.showChallengeSent(name, message.timeoutSeconds);
            break;
        case 'DECLINED':
            backToLobby(`${name} declined your challenge. You can try again with the same link.`);
            break;
        case 'TIMED_OUT':
            if (ui.isChallengePopupOpen()) {
                // host side: didn't answer in time
                ui.hideChallengePopup();
                ui.showToast(`You didn't answer ${name}'s challenge in time.`);
            } else {
                backToLobby(`${name} didn't answer in time. You can try again with the same link.`);
            }
            break;
        case 'BUSY':
            backToLobby(`${name} is answering another challenge right now. Try again in a moment.`);
            break;
        case 'HOST_LEFT':
            gameActions.forgetInvite();
            backToLobby(`${name} closed the game. Ask for a new link.`);
            break;
        // host side
        case 'CANCELLED':
            ui.hideChallengePopup();
            ui.showToast('Your friend cancelled the challenge. Your link still works.');
            break;
        default:
            console.warn('Unknown challenge status:', message.status);
    }
}

function handleGameState(message) {
    ui.hideChallengePopup();
    gameState.currentGameState = message.gameStatus;
    if (message.yourName) ui.setNames(message.yourName, message.opponentName);
    if (message.challengeType) ui.setChallenge(message.challengeType);
    if (message.gameStatus === 'RUNNING') {
        streak = 0;
        lastOpponentScore = 0;
        gameState.opponentGone = false;
        ui.resetGameBoard();
        gameState.setGameEndTime(Number(message.remainingSeconds));
    }
}

function handleCountdown(message) {
    const first = elements.countdownState.hidden;
    ui.showCountdown(message.countdown);
    if (first) {
        // Replay the names' slide-in for every new match
        elements.countdownState.classList.remove('is-intro');
        void elements.countdownState.offsetWidth;
        elements.countdownState.classList.add('is-intro');
        sound.matchFound();
    }
    setTimeout(() => sound.countdown(message.countdown), first ? 220 : 0);
}

function handleQuestion(message) {
    const show = () => {
        if (message.questionNumber === 1) sound.go();
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

        const answerElement = gameState.questionFormat === 'NUMBER' ? elements.answerDisplay : gameState.chosenButton;
        if (message.wasCorrect) {
            streak++;
            sound.correct();
            effects.dust(answerElement);
            if (STREAK_CALLOUTS.includes(streak)) {
                sound.streak(streak);
                // Below the answer line (or below the problem in button modes), never on top of the next problem
                const anchor = gameState.questionFormat === 'NUMBER' ? elements.answerDisplay : elements.questionText;
                effects.callout(anchor, `${streak} in a row`);
            }
        } else {
            streak = 0;
            sound.wrong();
        }
    } else if (message.opponentScore > lastOpponentScore) {
        sound.opponentPoint();
        const them = elements.opponentTally.closest('.hud-player');
        them.classList.remove('is-scoring');
        void them.offsetWidth;
        them.classList.add('is-scoring');
    }
    lastOpponentScore = message.opponentScore;
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
        if (message.draw) {
            sound.draw();
        } else if (message.youWon) {
            sound.win();
            requestAnimationFrame(() => effects.celebrate(elements.resultMessage));
        } else {
            sound.lose();
        }
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
    lastTickSecond = null;
    gameState.startTimer(() => {
        const remaining = gameState.getRemainingTime();
        ui.updateTimer(remaining);
        // Tick once per second during the last five seconds
        if (remaining > 0 && remaining <= 5 && remaining !== lastTickSecond) {
            lastTickSecond = remaining;
            sound.tick();
        }

        if (remaining === 0) {
            gameState.stopTimer();
            ui.stopTimerAnimation();
        }
    });
}
