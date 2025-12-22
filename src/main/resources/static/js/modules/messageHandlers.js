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

function handleGameState(message) {
    console.log('Game state:', message.gameStatus);
    gameState.currentGameState = message.gameStatus;

    if (message.gameStatus === 'WAITING') {
        ui.updateStatus('Waiting for opponent...');
    }
}

function handleCountdown(message) {
    console.log('⏱️ COUNTDOWN received:', message.countdown);

    // Calculate progress from 0% to 100% over 3 countdown steps
    const totalSteps = 3;
    const progress = ((totalSteps - message.countdown) / totalSteps) * 100;

    // Use centralized UI method
    ui.showCountdown(message.message, progress);
}

function handleQuestion(message) {
    console.log('📝 QUESTION received:', message);

    // Reset scores to 0-0 when first question arrives (new game/rematch)
    if (message.questionNumber === 1) {
        ui.updateScores(0, 0);
    }

    // Complete the progress bar to 100% before showing game
    const progressFill = document.getElementById('countdown-progress-fill');
    if (progressFill) {
        progressFill.style.width = '100%';
    }

    // Small delay to show completion, then show game area
    setTimeout(() => {
        // Show game area
        ui.showGame();

        // Update question
        ui.updateQuestion(message.questionText, message.questionNumber);

        // Start timer
        const remainingSeconds = Number(message.remainingSeconds);
        gameState.setGameEndTime(remainingSeconds);
        startTimer();
    }, 200);
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
    console.log('🏁 GAME_OVER received:', message);
    console.log('You won:', message.youWon);
    console.log('Scores:', message.yourScore, 'vs', message.opponentScore);

    gameState.stopTimer();
    ui.stopTimerAnimation();
    gameState.currentGameState = 'FINISHED';
    gameState.justShowedResult = true;  // Prevent "Connection lost!" error

    // Reset rematch flags
    gameState.rematchRequested = false;
    gameState.opponentWantsRematch = false;

    // Reset forfeit flag
    gameState.isForfeiting = false;

    // Show result screen
    ui.showResult();
    ui.updateGameOver(message);

    console.log('Result screen should now be visible');
}

function handleRematch(message) {
    console.log('Rematch:', message);
    ui.updateRematchStatus(message.opponentAccepted, message.statusMessage);
}

function handleError(message) {
    console.error('Server error:', message.errorMessage);
    ui.showError(message.errorMessage);

    // If opponent left queue or game early, return to lobby after showing error
    // BUT: Don't reload if game is already finished (GAME_OVER already received)
    if (message.errorMessage &&
        (message.errorMessage.includes('left the queue') || message.errorMessage.includes('left the game')) &&
        gameState.currentGameState !== 'FINISHED') {
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
