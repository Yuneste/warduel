/**
 * DOM Element References
 * Centralized access to all DOM elements
 */

export const elements = {
    // Status
    statusText: document.getElementById('status-text'),

    // Areas
    lobbyArea: document.getElementById('lobby-area'),
    waitingArea: document.getElementById('waiting-area'),
    gameArea: document.getElementById('game-area'),
    resultArea: document.getElementById('result-area'),

    // Game elements
    questionText: document.getElementById('question-text'),
    questionNumber: document.getElementById('question-number'),
    timer: document.getElementById('timer'),
    yourScoreSpan: document.getElementById('your-score'),
    opponentScoreSpan: document.getElementById('opponent-score'),

    // Answer
    answerDisplay: document.getElementById('answer-display'),

    // Feedback
    feedbackArea: document.getElementById('feedback-area'),

    // Result
    resultMessage: document.getElementById('result-message'),
    yourScore: document.getElementById('your-final-score'),
    opponentScore: document.getElementById('opponent-final-score'),
    rematchButton: document.getElementById('rematch-button'),
    rematchStatus: document.getElementById('rematch-status'),

    // Buttons
    playButton: document.getElementById('play-button'),
    leaveWaitingButton: document.getElementById('leave-waiting-button'),
    forfeitButton: document.getElementById('forfeit-button'),

    // Animations
    pointingHand: document.querySelector('.pointing-hand')
};
