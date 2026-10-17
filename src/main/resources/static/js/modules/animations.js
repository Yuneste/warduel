/**
 * Animations
 * Small physical feedback helpers shared by the UI
 */

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

export const animations = {
    // Short vibration on phones that support it
    vibrate(pattern = 10) {
        if ('vibrate' in navigator && !reducedMotion.matches) {
            navigator.vibrate(pattern);
        }
    },

    // Add haptic feedback to element
    addHapticFeedback(element) {
        element.addEventListener('touchstart', () => this.vibrate(), { passive: true });
    }
};
