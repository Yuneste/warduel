/**
 * Input Handler
 * Number pad and keyboard input, answer submission
 */

import { gameState } from './gameState.js';
import { elements } from './domElements.js';
import { ui } from './uiController.js';
import { websocket } from './websocket.js';
import { animations } from './animations.js';

const MAX_DIGITS = 7;

export const inputHandler = {
    setup() {
        elements.keys.forEach(button => {
            animations.addHapticFeedback(button);
            button.addEventListener('click', () => this.handleKey(button.dataset.key));
        });

        document.addEventListener('keydown', event => {
            if (elements.gameArea.hidden || event.ctrlKey || event.metaKey || event.altKey) return;
            const key = this.normalizeKey(event.key);
            if (!key) return;
            // Enter on a focused button (e.g. tabbed to "7") activates that button as usual
            if (key === 'Enter' && event.target.closest('button')) return;
            event.preventDefault();
            // Typing on the keyboard shouldn't leave a focus ring on the last clicked pad key
            if (document.activeElement && document.activeElement.classList.contains('key')) {
                document.activeElement.blur();
            }
            ui.flashKey(key === 'Escape' ? 'Backspace' : key);
            this.handleKey(key);
        });
    },

    // Map physical keys to pad keys (numpad and minus variants included)
    normalizeKey(key) {
        if (/^[0-9]$/.test(key)) return key;
        if (key === '-' || key === 'Subtract') return '-';
        if (key === 'Backspace' || key === 'Delete') return 'Backspace';
        if (key === 'Escape') return 'Escape';
        if (key === 'Enter') return 'Enter';
        return null;
    },

    handleKey(key) {
        if (gameState.awaitingResult) return; // wait for the previous answer's result
        if (key === 'Enter') this.submitAnswer();
        else if (key === 'Backspace') this.deleteDigit();
        else if (key === 'Escape') this.setAnswer('');
        else if (key === '-') this.toggleNegative();
        else this.addDigit(key);
    },

    setAnswer(value) {
        gameState.currentAnswer = value;
        ui.updateAnswerDisplay(value);
    },

    addDigit(digit) {
        const answer = gameState.currentAnswer;
        if (answer.replace('-', '').length >= MAX_DIGITS) return;
        // No leading zeros: "0" followed by a digit becomes that digit
        const next = /^-?0$/.test(answer) ? answer.slice(0, -1) + digit : answer + digit;
        this.setAnswer(next);
    },

    deleteDigit() {
        const next = gameState.currentAnswer.slice(0, -1);
        this.setAnswer(next === '-' ? '' : next);
    },

    toggleNegative() {
        const answer = gameState.currentAnswer;
        this.setAnswer(answer.startsWith('-') ? answer.slice(1) : '-' + answer);
    },

    submitAnswer() {
        const answer = parseInt(gameState.currentAnswer, 10);
        if (Number.isNaN(answer)) {
            ui.showFeedback('Type a number first');
            return;
        }

        gameState.awaitingResult = true;
        websocket.send({ type: 'ANSWER', answer });
    }
};
