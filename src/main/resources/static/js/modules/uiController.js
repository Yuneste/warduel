/**
 * UI Controller
 * Handles all DOM updates and visual feedback
 */

import { elements } from './domElements.js';
import { gameState } from './gameState.js';

export const ui = {
    // Show/Hide elements
    showElement(el) {
        if (el) el.style.display = 'block';
    },

    hideElement(el) {
        if (el) el.style.display = 'none';
    },

    // Update status text
    updateStatus(text) {
        console.log('Status:', text);
        if (elements.statusText) {
            elements.statusText.textContent = text;
        }
    },

    // Show error message
    showError(text) {
        console.error('Error:', text);
        this.updateStatus('❌ ' + text);
    },

    // Show feedback message
    showFeedback(text, isCorrect) {
        elements.feedbackArea.textContent = text;
        elements.feedbackArea.className = 'feedback ' + (isCorrect ? 'correct' : 'incorrect');
        this.showElement(elements.feedbackArea);

        setTimeout(() => {
            this.hideElement(elements.feedbackArea);
        }, 2000);
    },

    // Update to lobby screen
    showLobby() {
        this.showElement(elements.lobbyArea);
        this.hideElement(elements.statusText);
        this.hideElement(elements.waitingArea);
        this.hideElement(elements.gameArea);
        this.hideElement(elements.resultArea);

        // Reset scores
        if (elements.yourScoreSpan) elements.yourScoreSpan.textContent = '0';
        if (elements.opponentScoreSpan) elements.opponentScoreSpan.textContent = '0';
        if (elements.timer) elements.timer.textContent = '60';
    },

    // Update to waiting screen
    showWaiting() {
        this.hideElement(elements.lobbyArea);
        this.showElement(elements.statusText);
        this.showElement(elements.waitingArea);
        this.updateStatus('Searching for opponent...');
    },

    // Update to game screen
    showGame() {
        this.hideElement(elements.waitingArea);
        this.showElement(elements.gameArea);
        this.hideElement(elements.resultArea);
    },

    // Update to result screen
    showResult() {
        this.hideElement(elements.waitingArea);
        this.hideElement(elements.gameArea);
        this.showElement(elements.resultArea);
    },

    // Update question
    updateQuestion(questionText, questionNumber) {
        elements.questionNumber.textContent = `Question ${questionNumber}/20`;
        elements.questionText.textContent = questionText;

        // Reset Answer
        gameState.currentAnswer = '';
        elements.answerDisplay.textContent = '—';

        // Hide Feedback
        this.hideElement(elements.feedbackArea);
    },

    // Update scores
    updateScores(yourScore, opponentScore) {
        elements.yourScoreSpan.textContent = yourScore.toString();
        elements.opponentScoreSpan.textContent = opponentScore.toString();
    },

    // Trigger answer feedback animation
    showAnswerFeedback(wasCorrect) {
        const answerDisplay = elements.answerDisplay;
        answerDisplay.classList.remove('blink-red', 'blink-green');

        if (wasCorrect) {
            answerDisplay.classList.add('blink-green');
        } else {
            answerDisplay.classList.add('blink-red');
        }

        setTimeout(() => {
            answerDisplay.classList.remove('blink-red', 'blink-green');
        }, 600);
    },

    // Update timer display
    updateTimer(seconds) {
        elements.timer.textContent = seconds.toString();

        // Add breathing effect when 5 seconds or less
        if (seconds <= 5 && seconds > 0) {
            elements.timer.classList.add('breathing');
        } else {
            elements.timer.classList.remove('breathing');
        }
    },

    // Stop timer animation
    stopTimerAnimation() {
        if (elements.timer) {
            elements.timer.classList.remove('breathing');
        }
    },

    // Update answer display
    updateAnswerDisplay(answer) {
        elements.answerDisplay.textContent = answer || '—';
    },

    // Update game over screen
    updateGameOver(message) {
        // Scores
        elements.yourScore.textContent = message.yourScore.toString();
        elements.opponentScore.textContent = message.opponentScore.toString();

        // Result Message
        let resultText = '';
        if (message.draw) {
            resultText = 'Draw';
            elements.resultMessage.className = 'result-message draw';
        } else if (message.youWon) {
            resultText = 'Victory!';
            elements.resultMessage.className = 'result-message win';
        } else {
            resultText = 'Defeat';
            elements.resultMessage.className = 'result-message lose';
        }

        // Add disconnect message if present
        if (message.disconnectMessage) {
            resultText += '\n' + message.disconnectMessage;
        }

        elements.resultMessage.textContent = resultText;

        // Rematch Button
        elements.rematchButton.disabled = false;
        elements.rematchButton.textContent = 'Rematch';
        elements.rematchStatus.textContent = '';
    },

    // Update rematch status
    updateRematchStatus(opponentAccepted, statusMessage) {
        if (opponentAccepted) {
            elements.rematchStatus.textContent = 'Rematch starting!';
        } else {
            elements.rematchStatus.textContent = statusMessage || 'Waiting for opponent...';
        }
    },

    // Update rematch button for waiting
    setRematchWaiting() {
        elements.rematchButton.disabled = true;
        elements.rematchButton.textContent = 'Waiting for opponent...';
        elements.rematchStatus.textContent = 'Waiting for opponent...';
    }
};
