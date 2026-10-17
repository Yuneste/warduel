/**
 * UI Controller
 * Handles all DOM updates and visual feedback (no game logic)
 */

import { elements } from './domElements.js';
import { gameState } from './gameState.js';

const SCREENS = ['lobbyArea', 'waitingArea', 'gameArea', 'resultArea'];
const URGENT_SECONDS = 5;
const SVG_NS = 'http://www.w3.org/2000/svg';

let toastTimeout = null;

// Small, repeatable wobble so tally strokes look hand-drawn but don't jump between renders
const wobble = (i, amount = 1.2) => (((i * 37) % 7) - 3) * amount / 3;

/** Builds the strokes of one tally group: up to four verticals, the fifth crosses them */
function tallyGroup(strokes, firstIndex, newIndex) {
    const svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('viewBox', '0 0 30 32');
    svg.setAttribute('aria-hidden', 'true');
    for (let s = 0; s < strokes; s++) {
        const i = firstIndex + s;
        const path = document.createElementNS(SVG_NS, 'path');
        const d = s < 4
            ? `M${4 + s * 7 + wobble(i)} ${2 + wobble(i + 1)} L${4 + s * 7 - wobble(i + 2)} ${30 + wobble(i + 3)}`
            : `M${-1 + wobble(i)} ${25 + wobble(i + 1)} L${30 + wobble(i + 2)} ${6 + wobble(i + 3)}`;
        path.setAttribute('d', d);
        if (i === newIndex) path.classList.add('is-new');
        svg.appendChild(path);
    }
    return svg;
}

function renderTally(container, score, label) {
    if (!container) return;
    const previous = Number(container.dataset.count || 0);
    const newIndex = score > previous ? score - 1 : -1;
    container.replaceChildren();
    for (let first = 0; first < score; first += 5) {
        container.appendChild(tallyGroup(Math.min(5, score - first), first, newIndex));
    }
    container.dataset.count = String(score);
    if (label) container.setAttribute('aria-label', `${label}: ${score}`);
}

export const ui = {
    // Show exactly one screen
    showScreen(name) {
        SCREENS.forEach(key => {
            if (elements[key]) elements[key].hidden = key !== name;
        });
    },

    // Message shown on top of any screen
    showToast(text, durationMs = 4000) {
        clearTimeout(toastTimeout);
        elements.toast.textContent = text;
        elements.toast.hidden = false;
        if (durationMs > 0) {
            toastTimeout = setTimeout(() => this.hideToast(), durationMs);
        }
    },

    hideToast() {
        elements.toast.hidden = true;
    },

    showError(text) {
        console.error('Error:', text);
        this.showToast(text);
    },

    // Short hint under the answer line
    showFeedback(text) {
        elements.feedbackArea.textContent = text;
        clearTimeout(this.feedbackTimeout);
        this.feedbackTimeout = setTimeout(() => { elements.feedbackArea.textContent = ''; }, 2000);
    },

    showLobby() {
        this.showScreen('lobbyArea');
        elements.playButton.disabled = false;
        this.resetGameBoard();
    },

    showWaiting() {
        this.showScreen('waitingArea');
        elements.queueState.hidden = false;
        elements.countdownState.hidden = true;
    },

    showCountdown(seconds, tipText) {
        this.showScreen('waitingArea');
        elements.queueState.hidden = true;
        elements.countdownState.hidden = false;
        // Re-insert the number so its "written in chalk" animation plays for every step
        const number = elements.countdownNumber;
        number.textContent = String(seconds);
        number.style.animation = 'none';
        void number.offsetWidth;
        number.style.animation = '';
        if (tipText) elements.countdownTip.textContent = tipText;
    },

    showGame() {
        this.showScreen('gameArea');
    },

    showResult() {
        this.showScreen('resultArea');
    },

    resetGameBoard() {
        renderTally(elements.yourTally, 0, 'Your score');
        renderTally(elements.opponentTally, 0, 'Opponent score');
        elements.timer.textContent = String(gameState.config.durationSeconds);
        elements.timer.classList.remove('is-urgent');
        this.resetForfeitButton();
    },

    updateQuestion(questionText, questionNumber) {
        elements.questionNumber.textContent = `Question ${questionNumber} of ${gameState.config.questionsPerGame}`;
        elements.questionText.textContent = questionText;
        gameState.currentAnswer = '';
        gameState.awaitingResult = false;
        this.updateAnswerDisplay('');
    },

    updateScores(yourScore, opponentScore) {
        renderTally(elements.yourTally, yourScore, 'Your score');
        renderTally(elements.opponentTally, opponentScore, 'Opponent score');
    },

    // Mark the submitted answer as right or wrong until the next question arrives
    showAnswerFeedback(wasCorrect) {
        const answer = elements.answerDisplay;
        answer.classList.remove('is-correct', 'is-wrong');
        void answer.offsetWidth; // restart the animation for repeated wrong answers
        answer.classList.add(wasCorrect ? 'is-correct' : 'is-wrong');
    },

    updateTimer(seconds) {
        elements.timer.textContent = String(seconds);
        elements.timer.classList.toggle('is-urgent', seconds > 0 && seconds <= URGENT_SECONDS);
    },

    stopTimerAnimation() {
        elements.timer.classList.remove('is-urgent');
    },

    updateAnswerDisplay(answer) {
        const display = elements.answerDisplay;
        display.classList.remove('is-correct', 'is-wrong');
        display.textContent = answer;
    },

    // Light up the on-screen key that matches a physical key press
    flashKey(key) {
        const button = [...elements.keys].find(k => k.dataset.key === key);
        if (!button) return;
        button.classList.add('is-pressed');
        setTimeout(() => button.classList.remove('is-pressed'), 120);
    },

    armForfeitButton() {
        elements.forfeitButton.classList.add('is-armed');
        elements.forfeitButton.textContent = 'Tap again to give up';
    },

    resetForfeitButton() {
        elements.forfeitButton.classList.remove('is-armed');
        elements.forfeitButton.textContent = 'Give up';
    },

    updateGameOver(message) {
        const title = elements.resultMessage;
        title.classList.remove('win');
        if (message.draw) {
            title.textContent = 'Draw';
        } else if (message.youWon) {
            title.textContent = 'You win';
            title.classList.add('win');
        } else {
            title.textContent = 'You lose';
        }

        elements.resultReason.hidden = !message.disconnectMessage;
        elements.resultReason.textContent = message.disconnectMessage || '';

        elements.yourFinalScore.textContent = String(message.yourScore);
        elements.opponentFinalScore.textContent = String(message.opponentScore);
        elements.yourFinalTally.dataset.count = '0';
        elements.opponentFinalTally.dataset.count = '0';
        renderTally(elements.yourFinalTally, message.yourScore);
        renderTally(elements.opponentFinalTally, message.opponentScore);

        elements.rematchButton.disabled = false;
        elements.rematchButton.textContent = 'Rematch';
        elements.rematchStatus.textContent = '';
    },

    updateRematchStatus(opponentAccepted, statusMessage) {
        elements.rematchStatus.textContent = opponentAccepted
            ? 'Rematch starting'
            : (statusMessage || 'Waiting for your opponent');
    },

    setRematchWaiting() {
        elements.rematchButton.disabled = true;
        elements.rematchButton.textContent = 'Rematch asked';
        elements.rematchStatus.textContent = 'Waiting for your opponent to accept';
    },

    disableRematch() {
        elements.rematchButton.disabled = true;
        elements.rematchButton.textContent = 'Rematch';
        elements.rematchStatus.textContent = 'Your opponent left. Find a new opponent to play again.';
    }
};
