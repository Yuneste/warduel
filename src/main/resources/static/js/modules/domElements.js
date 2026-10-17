/**
 * DOM Element References
 * Centralized access to all DOM elements
 */

const byId = id => document.getElementById(id);

export const elements = {
    toast: byId('toast'),

    // Screens
    lobbyArea: byId('lobby-area'),
    waitingArea: byId('waiting-area'),
    gameArea: byId('game-area'),
    resultArea: byId('result-area'),

    // Queue and countdown
    queueState: byId('queue-state'),
    countdownState: byId('countdown-state'),
    countdownNumber: byId('countdown-number'),
    countdownTip: byId('countdown-tip'),

    // Game
    questionText: byId('question-text'),
    questionNumber: byId('question-number'),
    timer: byId('timer'),
    yourTally: byId('your-tally'),
    opponentTally: byId('opponent-tally'),
    answerDisplay: byId('answer-display'),
    feedbackArea: byId('feedback-area'),
    keys: document.querySelectorAll('.key'),

    // Result
    resultMessage: byId('result-message'),
    resultReason: byId('result-reason'),
    yourFinalScore: byId('your-final-score'),
    opponentFinalScore: byId('opponent-final-score'),
    yourFinalTally: byId('your-final-tally'),
    opponentFinalTally: byId('opponent-final-tally'),
    rematchButton: byId('rematch-button'),
    rematchStatus: byId('rematch-status'),

    // Buttons
    playButton: byId('play-button'),
    leaveWaitingButton: byId('leave-waiting-button'),
    forfeitButton: byId('forfeit-button'),
    newGameButton: byId('new-game-button'),
    menuButton: byId('menu-button')
};
