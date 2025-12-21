/**
 * Animations
 * Handles UI animations (pointing hand, etc.)
 */

import { elements } from './domElements.js';

export const animations = {
    // Start pointing hand animation (shows every 15 seconds)
    startPointingHand() {
        if (!elements.pointingHand) return;

        // Show pointing hand every 15 seconds
        setInterval(() => {
            this.showPointingHand();
        }, 15000);

        // Show once immediately after 2 seconds
        setTimeout(() => {
            this.showPointingHand();
        }, 2000);
    },

    // Show pointing hand animation
    showPointingHand() {
        // Only show if lobby is visible
        if (elements.lobbyArea && elements.lobbyArea.style.display !== 'none') {
            elements.pointingHand.classList.add('show');

            // Remove class after animation completes (2s)
            setTimeout(() => {
                elements.pointingHand.classList.remove('show');
            }, 2000);
        }
    },

    // Add haptic feedback to element
    addHapticFeedback(element) {
        element.addEventListener('touchstart', () => {
            if ('vibrate' in navigator) {
                navigator.vibrate(10);
            }
        }, { passive: true });
    }
};
