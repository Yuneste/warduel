/**
 * WarDuel - Game Client
 * WebSocket-basiertes Multiplayer Quiz-Spiel
 */

// ==================== GLOBALE VARIABLEN ====================

let socket = null;
let isConnected = false;
let currentGameState = 'CONNECTING';

// Timer
let timerInterval = null;
let gameEndTime = null;

// Rematch State
let rematchRequested = false;
let opponentWantsRematch = false;

// Current Answer
let currentAnswer = '';

// ==================== DOM ELEMENTE ====================

const elements = {
    // Status
    statusText: document.getElementById('status-text'),

    // Bereiche
    lobbyArea: document.getElementById('lobby-area'),
    waitingArea: document.getElementById('waiting-area'),
    gameArea: document.getElementById('game-area'),
    resultArea: document.getElementById('result-area'),

    // Spiel
    questionText: document.getElementById('question-text'),
    questionNumber: document.getElementById('question-number'),
    timer: document.getElementById('timer'),
    yourScoreSpan: document.getElementById('your-score'),
    opponentScoreSpan: document.getElementById('opponent-score'),

    // Answer Display
    answerDisplay: document.getElementById('answer-display'),

    // Feedback
    feedbackArea: document.getElementById('feedback-area'),

    // Ergebnis
    resultMessage: document.getElementById('result-message'),
    yourScore: document.getElementById('your-final-score'),
    opponentScore: document.getElementById('opponent-final-score'),
    rematchButton: document.getElementById('rematch-button'),
    rematchStatus: document.getElementById('rematch-status')
};

// ==================== INITIALISIERUNG ====================

window.addEventListener('DOMContentLoaded', () => {
    console.log('WarDuel starting...');
    setupNumberPad();
    setupPlayButton();
    setupLeaveButtons();
});

function setupPlayButton() {
    const playButton = document.getElementById('play-button');
    if (playButton) {
        addHapticFeedback(playButton);
        playButton.addEventListener('click', () => {
            console.log('Play button clicked');
            // Hide lobby, show status and waiting area
            hideElement(elements.lobbyArea);
            showElement(elements.statusText);
            showElement(elements.waitingArea);
            // Connect to server and join matchmaking
            connectToServer();
        });
    }
}

function setupLeaveButtons() {
    // Leave button in waiting area
    const leaveWaitingButton = document.getElementById('leave-waiting-button');
    if (leaveWaitingButton) {
        addHapticFeedback(leaveWaitingButton);
        leaveWaitingButton.addEventListener('click', () => {
            console.log('Leave waiting button clicked');
            leaveGame();
        });
    }

    // Forfeit button in game area
    const forfeitButton = document.getElementById('forfeit-button');
    if (forfeitButton) {
        addHapticFeedback(forfeitButton);
        forfeitButton.addEventListener('click', () => {
            console.log('Forfeit button clicked');
            leaveGame();
        });
    }
}

function addHapticFeedback(element) {
    element.addEventListener('touchstart', () => {
        if ('vibrate' in navigator) {
            navigator.vibrate(10);
        }
    }, { passive: true });
}

function leaveGame() {
    console.log('Leaving game...');

    // Stop timer
    stopTimer();

    // Close WebSocket connection (this triggers backend cleanup)
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.close();
    }

    // Reset state
    isConnected = false;
    currentGameState = 'CONNECTING';
    currentAnswer = '';
    rematchRequested = false;
    opponentWantsRematch = false;

    // Show lobby, hide everything else
    showElement(elements.lobbyArea);
    hideElement(elements.statusText);
    hideElement(elements.waitingArea);
    hideElement(elements.gameArea);
    hideElement(elements.resultArea);

    // Reset scores
    if (elements.yourScoreSpan) elements.yourScoreSpan.textContent = '0';
    if (elements.opponentScoreSpan) elements.opponentScoreSpan.textContent = '0';
    if (elements.timer) elements.timer.textContent = '60';
}

// ==================== WEBSOCKET VERBINDUNG ====================

function connectToServer() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/game`;

    console.log('Connecting to:', wsUrl);
    updateStatus('Verbinde mit Server...');

    try {
        socket = new WebSocket(wsUrl);

        socket.onopen = handleConnectionOpen;
        socket.onmessage = handleMessage;
        socket.onerror = handleError;
        socket.onclose = handleConnectionClose;
    } catch (error) {
        console.error('WebSocket error:', error);
        showError('Verbindungsfehler!');
    }
}

function handleConnectionOpen() {
    console.log('WebSocket connected');
    isConnected = true;
    updateStatus('Suche nach Gegner...');
    // Make sure waiting area is visible and lobby is hidden
    hideElement(elements.lobbyArea);
    showElement(elements.waitingArea);
}

function handleMessage(event) {
    try {
        const message = JSON.parse(event.data);
        console.log('Received:', message.type);

        switch(message.type) {
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
                handleErrorMessage(message);
                break;
            default:
                console.warn('Unknown message type:', message.type);
        }
    } catch (error) {
        console.error('Error parsing message:', error);
    }
}

function handleError(event) {
    console.error('WebSocket error:', event);
    showError('Verbindungsfehler!');
}

function handleConnectionClose(event) {
    console.log('WebSocket closed:', event);
    isConnected = false;

    if(currentGameState !== 'FINISHED') {
        showError('Verbindung verloren!');
    }
}

// ==================== MESSAGE HANDLER ====================

/**
 * @param {{gameStatus: string}} message
 */
function handleGameState(message) {
    console.log('Game state:', message.gameStatus);
    currentGameState = message.gameStatus;

    if(message.gameStatus === 'WAITING') {
        updateStatus('Warte auf Gegner...');
    }
}

/**
 * @param {{questionText: string, questionNumber: number, remainingSeconds: number}} message
 */
function handleQuestion(message) {
    console.log('New question:', message.questionNumber);

    // Zeige Game Area
    hideElement(elements.waitingArea);
    showElement(elements.gameArea);
    hideElement(elements.resultArea);

    // Update Question
    elements.questionNumber.textContent = `Frage ${message.questionNumber}/20`;
    elements.questionText.textContent = message.questionText;

    // Timer
    const remainingSeconds = Number(message.remainingSeconds);
    gameEndTime = Date.now() + (remainingSeconds * 1000);
    startTimer();

    // Reset Answer
    currentAnswer = '';
    elements.answerDisplay.textContent = '—';

    // Hide Feedback
    hideElement(elements.feedbackArea);
}

/**
 * @param {{yourScore: number, opponentScore: number, wasCorrect: boolean}} message
 */
function handleScoreUpdate(message) {
    console.log('Score update:', message.yourScore, '/', message.opponentScore);

    // Update Scores
    elements.yourScoreSpan.textContent = message.yourScore.toString();
    elements.opponentScoreSpan.textContent = message.opponentScore.toString();

    // Trigger answer display animation (only if we just submitted an answer)
    if(message.wasCorrect !== undefined && message.wasCorrect !== null && currentAnswer !== '') {
        const answerDisplay = elements.answerDisplay;
        answerDisplay.classList.remove('blink-red', 'blink-green');

        if(message.wasCorrect) {
            answerDisplay.classList.add('blink-green');
        } else {
            answerDisplay.classList.add('blink-red');
        }

        // Remove class after animation
        setTimeout(() => {
            answerDisplay.classList.remove('blink-red', 'blink-green');
        }, 600);
    }

    // Show Feedback (only if we just submitted an answer)
    if(message.wasCorrect !== undefined && message.wasCorrect !== null && currentAnswer !== '') {
        showFeedback(message.wasCorrect ? 'Richtig!' : 'Falsch!', message.wasCorrect);
    }
}

/**
 * @param {{yourScore: number, opponentScore: number, youWon: boolean, draw: boolean, winnerName: string}} message
 */
function handleGameOver(message) {
    console.log('Game over');

    stopTimer();
    currentGameState = 'FINISHED';

    // Reset Rematch Flags
    rematchRequested = false;
    opponentWantsRematch = false;

    // Zeige Result Area
    hideElement(elements.waitingArea);
    hideElement(elements.gameArea);
    showElement(elements.resultArea);

    // Scores
    elements.yourScore.textContent = message.yourScore.toString();
    elements.opponentScore.textContent = message.opponentScore.toString();

    // Result Message
    if(message.draw) {
        elements.resultMessage.textContent = 'Unentschieden';
        elements.resultMessage.className = 'result-message draw';
    } else if(message.youWon) {
        elements.resultMessage.textContent = 'Sieg!';
        elements.resultMessage.className = 'result-message win';
    } else {
        elements.resultMessage.textContent = 'Niederlage';
        elements.resultMessage.className = 'result-message lose';
    }

    // Rematch Button
    elements.rematchButton.disabled = false;
    elements.rematchButton.textContent = 'Rematch';
    elements.rematchStatus.textContent = '';
}

/**
 * @param {{opponentAccepted: boolean, statusMessage: string}} message
 */
function handleRematch(message) {
    console.log('Rematch:', message);

    if(message.opponentAccepted) {
        // Both want rematch - game restarts
        elements.rematchStatus.textContent = 'Rematch startet!';
    } else {
        // Waiting for opponent
        elements.rematchStatus.textContent = message.statusMessage || 'Warte auf Gegner...';
    }
}

/**
 * @param {{errorMessage: string}} message
 */
function handleErrorMessage(message) {
    console.error('Server error:', message.errorMessage);
    showError(message.errorMessage);
}

// ==================== TIMER ====================

function startTimer() {
    stopTimer();

    timerInterval = setInterval(() => {
        if(!gameEndTime) return;

        const remaining = Math.max(0, Math.floor((gameEndTime - Date.now()) / 1000));
        elements.timer.textContent = remaining.toString();

        // Add breathing effect when 5 seconds or less
        if(remaining <= 5 && remaining > 0) {
            elements.timer.classList.add('breathing');
        } else {
            elements.timer.classList.remove('breathing');
        }

        if(remaining === 0) {
            stopTimer();
        }
    }, 1000);
}

function stopTimer() {
    if(timerInterval) {
        clearInterval(timerInterval);
        timerInterval = null;
    }
    // Remove breathing effect
    if(elements.timer) {
        elements.timer.classList.remove('breathing');
    }
}

// ==================== NUMBER PAD ====================

function setupNumberPad() {
    const buttons = document.querySelectorAll('.number-btn');

    buttons.forEach(btn => {
        // Add haptic feedback on touch
        btn.addEventListener('touchstart', () => {
            if ('vibrate' in navigator) {
                navigator.vibrate(10);
            }
        }, { passive: true });

        btn.addEventListener('click', () => {
            const num = btn.dataset.num;

            if(num === 'C') {
                currentAnswer = '';
            } else if(num === '−') {
                if(currentAnswer.startsWith('-')) {
                    currentAnswer = currentAnswer.substring(1);
                } else {
                    currentAnswer = '-' + currentAnswer;
                }
            } else if(num === '✓') {
                submitAnswer();
                return;
            } else {
                // Limit to 10 digits
                if(currentAnswer.replace('-', '').length < 10) {
                    currentAnswer += num;
                }
            }

            elements.answerDisplay.textContent = currentAnswer || '—';
        });
    });
}

function submitAnswer() {
    if(!currentAnswer || currentAnswer === '—') {
        showFeedback('Bitte gib eine Antwort ein!', false);
        return;
    }

    const answer = parseInt(currentAnswer);

    if(isNaN(answer)) {
        showFeedback('Ungültige Zahl!', false);
        return;
    }

    // Send to server
    sendToServer({
        type: 'ANSWER',
        answer: answer
    });

    console.log('Sent answer:', answer);
}

// ==================== REMATCH ====================

function requestRematch() {
    if(!socket || socket.readyState !== WebSocket.OPEN) {
        showError('Keine Verbindung!');
        return;
    }

    if(rematchRequested) {
        console.log('Rematch already requested');
        return;
    }

    console.log('Requesting rematch...');
    rematchRequested = true;

    // UI Update
    elements.rematchButton.disabled = true;
    elements.rematchButton.textContent = 'Warte auf Gegner...';
    elements.rematchStatus.textContent = 'Warte auf Gegner...';

    // Send
    sendToServer({
        type: 'REMATCH',
        requestRematch: true,
        opponentAccepted: false,
        statusMessage: ''
    });
}

// ==================== UTILITIES ====================

function sendToServer(message) {
    if(socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(message));
    } else {
        console.error('Cannot send - not connected');
        showError('Keine Verbindung!');
    }
}

function updateStatus(text) {
    console.log('Status:', text);
    // Entferne die textContent Zeile wenn Element nicht existiert
    if(elements.statusText) {
        elements.statusText.textContent = text;
    }
}

function showFeedback(text, isCorrect) {
    elements.feedbackArea.textContent = text;
    elements.feedbackArea.className = 'feedback ' + (isCorrect ? 'correct' : 'incorrect');
    showElement(elements.feedbackArea);

    // Auto-hide after 2 seconds
    setTimeout(() => {
        hideElement(elements.feedbackArea);
    }, 2000);
}

function showError(text) {
    console.error('Error:', text);
    updateStatus('❌ ' + text);
}

function showElement(el) {
    if(el) el.style.display = 'block';
}

function hideElement(el) {
    if(el) el.style.display = 'none';
}

function handleMessage(event) {
    try {
        const message = JSON.parse(event.data);
        console.log('🔵 Received:', message.type, message);  // MEHR DETAILS

        switch(message.type) {
            case 'GAME_STATE':
                handleGameState(message);
                break;
            case 'QUESTION':
                console.log('📝 QUESTION received:', message);  // DEBUG
                handleQuestion(message);
                break;
            case 'SCORE_UPDATE':
                console.log('🎯 SCORE_UPDATE received:', message);  // DEBUG
                handleScoreUpdate(message);
                break;
            case 'GAME_OVER':
                handleGameOver(message);
                break;
            case 'REMATCH':
                handleRematch(message);
                break;
            case 'ERROR':
                handleErrorMessage(message);
                break;
            default:
                console.warn('Unknown message type:', message.type);
        }
    } catch (error) {
        console.error('Error parsing message:', error);
    }
}

// ==================== CLEANUP ====================

window.addEventListener('beforeunload', () => {
    stopTimer();
    if(socket) {
        socket.close();
    }
});