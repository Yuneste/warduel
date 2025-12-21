/**
 * Input Handler
 * Handles number pad input and answer submission
 */

import { gameState } from './gameState.js';
import { ui } from './uiController.js';
import { websocket } from './websocket.js';

export const inputHandler = {
    // Setup number pad event listeners
    setupNumberPad() {
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

                if (num === 'C') {
                    this.clearAnswer();
                } else if (num === '−') {
                    this.toggleNegative();
                } else if (num === '✓') {
                    this.submitAnswer();
                } else {
                    this.addDigit(num);
                }
            });
        });
    },

    // Add digit to answer
    addDigit(digit) {
        // Limit to 10 digits
        if (gameState.currentAnswer.replace('-', '').length < 10) {
            gameState.currentAnswer += digit;
            ui.updateAnswerDisplay(gameState.currentAnswer);
        }
    },

    // Clear answer
    clearAnswer() {
        gameState.currentAnswer = '';
        ui.updateAnswerDisplay('');
    },

    // Toggle negative sign
    toggleNegative() {
        if (gameState.currentAnswer.startsWith('-')) {
            gameState.currentAnswer = gameState.currentAnswer.substring(1);
        } else {
            gameState.currentAnswer = '-' + gameState.currentAnswer;
        }
        ui.updateAnswerDisplay(gameState.currentAnswer);
    },

    // Submit answer to server
    submitAnswer() {
        if (!gameState.currentAnswer || gameState.currentAnswer === '—') {
            ui.showFeedback('Please enter an answer!', false);
            return;
        }

        const answer = parseInt(gameState.currentAnswer);

        if (isNaN(answer)) {
            ui.showFeedback('Invalid number!', false);
            return;
        }

        // Send to server
        websocket.send({
            type: 'ANSWER',
            answer: answer
        });

        console.log('Sent answer:', answer);
    }
};
