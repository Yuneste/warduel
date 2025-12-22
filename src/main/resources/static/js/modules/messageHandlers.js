/**
 * Message Handlers
 * Handle incoming WebSocket messages
 */

import { gameState } from './gameState.js';
import { ui } from './uiController.js';

export function handleMessage(message) {
    switch (message.type) {
        case 'GAME_STATE':
            handleGameState(message);
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

function handleGameState(message) {
    console.log('Game state:', message.gameStatus);
    gameState.currentGameState = message.gameStatus;

    if (message.gameStatus === 'WAITING') {
        ui.updateStatus('Waiting for opponent...');
    }
}

function handleQuestion(message) {
    console.log('📝 QUESTION received:', message);

    // Show game area
    ui.showGame();

    // Update question
    ui.updateQuestion(message.questionText, message.questionNumber);

    // Start timer
    const remainingSeconds = Number(message.remainingSeconds);
    gameState.setGameEndTime(remainingSeconds);
    startTimer();
}

function handleScoreUpdate(message) {
    console.log('🎯 SCORE_UPDATE received:', message);

    // Update scores
    ui.updateScores(message.yourScore, message.opponentScore);

    // Show answer feedback if we just submitted
    if (message.wasCorrect !== undefined && message.wasCorrect !== null && gameState.currentAnswer !== '') {
        ui.showAnswerFeedback(message.wasCorrect);
    }
}

function handleGameOver(message) {
    console.log('Game over');

    gameState.stopTimer();
    ui.stopTimerAnimation();
    gameState.currentGameState = 'FINISHED';

    // Reset rematch flags
    gameState.rematchRequested = false;
    gameState.opponentWantsRematch = false;

    // Show result screen
    ui.showResult();
    ui.updateGameOver(message);
}

function handleRematch(message) {
    console.log('Rematch:', message);
    ui.updateRematchStatus(message.opponentAccepted, message.statusMessage);
}

function handleError(message) {
    console.error('Server error:', message.errorMessage);
    ui.showError(message.errorMessage);

    // If opponent left queue or game early, return to lobby after showing error
    if (message.errorMessage && (message.errorMessage.includes('left the queue') || message.errorMessage.includes('left the game'))) {
        setTimeout(() => {
            location.reload();
        }, 2000);
    }
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
