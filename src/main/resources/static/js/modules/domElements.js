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

    // Lobby
    playerName: byId('player-name'),
    inviteBanner: byId('invite-banner'),
    joinInviteButton: byId('join-invite-button'),
    friendButton: byId('friend-button'),
    leaderboardButton: byId('leaderboard-button'),
    leaderboardArea: byId('leaderboard-area'),
    leaderboardStatus: byId('leaderboard-status'),
    leaderboardList: byId('leaderboard-list'),
    leaderboardYou: byId('leaderboard-you'),
    leaderboardBackButton: byId('leaderboard-back-button'),
    friendOptions: byId('friend-options'),
    createRoomButton: byId('create-room-button'),
    friendBackButton: byId('friend-back-button'),

    // Queue, private room and countdown
    queueState: byId('queue-state'),
    roomState: byId('room-state'),
    roomLink: byId('room-link'),
    roomCode: byId('room-code'),
    copyLinkButton: byId('copy-link-button'),
    shareLinkButton: byId('share-link-button'),
    leaveRoomButton: byId('leave-room-button'),
    matchupYou: byId('matchup-you'),
    matchupThem: byId('matchup-them'),
    countdownState: byId('countdown-state'),
    countdownMode: byId('countdown-mode'),
    countdownNumber: byId('countdown-number'),
    countdownTip: byId('countdown-tip'),

    // Game
    hudYouName: byId('hud-you-name'),
    hudThemName: byId('hud-them-name'),
    questionText: byId('question-text'),
    questionNumber: byId('question-number'),
    questionPrompt: byId('question-prompt'),
    choicePad: byId('choice-pad'),
    choiceLeft: byId('choice-left'),
    choiceRight: byId('choice-right'),
    numberPad: byId('number-pad'),
    timer: byId('timer'),
    yourTally: byId('your-tally'),
    opponentTally: byId('opponent-tally'),
    answerDisplay: byId('answer-display'),
    feedbackArea: byId('feedback-area'),
    keys: document.querySelectorAll('.key'),

    // Result
    resultMessage: byId('result-message'),
    resultReason: byId('result-reason'),
    finalYouName: byId('final-you-name'),
    finalThemName: byId('final-them-name'),
    stats: byId('stats'),
    statsSummary: byId('stats-summary'),
    answerStrip: byId('answer-strip'),
    answerList: byId('answer-list'),
    opponentSummary: byId('opponent-summary'),
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
