/**
 * Effects
 * Chalk dust and streak call-outs. Skipped entirely when the player asks for reduced motion.
 */

const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const layer = document.getElementById('effects-layer');

const COLORS = {
    chalk: ['#eef0e8', '#eef0e8', '#d9ddd2'],
    win: ['#f2d45c', '#eef0e8', '#f3a1b5', '#a8e6a1'],
    good: ['#a8e6a1', '#eef0e8']
};

function originOf(element) {
    const box = element.getBoundingClientRect();
    const board = layer.getBoundingClientRect();
    return { x: box.left + box.width / 2 - board.left, y: box.top + box.height / 2 - board.top, w: box.width };
}

export const effects = {
    /** A small puff of chalk dust coming off an element */
    dust(element, { count = 12, palette = 'good', spread = 1 } = {}) {
        if (reducedMotion.matches || !element) return;
        const { x, y, w } = originOf(element);
        const colors = COLORS[palette];
        for (let i = 0; i < count; i++) {
            const speck = document.createElement('span');
            speck.className = 'dust';
            const angle = Math.random() * Math.PI * 2;
            const distance = (24 + Math.random() * 46) * spread;
            speck.style.left = `${x + (Math.random() - 0.5) * w * 0.6}px`;
            speck.style.top = `${y}px`;
            speck.style.background = colors[i % colors.length];
            speck.style.setProperty('--dx', `${Math.cos(angle) * distance}px`);
            speck.style.setProperty('--dy', `${Math.sin(angle) * distance * 0.6 + 30 * spread}px`);
            speck.style.setProperty('--size', `${3 + Math.random() * 4}px`);
            speck.style.animationDelay = `${Math.random() * 60}ms`;
            speck.addEventListener('animationend', () => speck.remove());
            layer.appendChild(speck);
        }
    },

    /** Bigger burst for a win */
    celebrate(element) {
        this.dust(element, { count: 40, palette: 'win', spread: 2.6 });
    },

    /** Short chalk text just below an element, e.g. "5 in a row" under the answer line */
    callout(element, text) {
        if (!element) return;
        const label = document.createElement('span');
        label.className = 'callout chalk';
        label.textContent = text;
        const box = element.getBoundingClientRect();
        const board = layer.getBoundingClientRect();
        label.style.left = `${box.left + box.width / 2 - board.left}px`;
        label.style.top = `${box.bottom - board.top + 34}px`;
        label.addEventListener('animationend', () => label.remove());
        layer.appendChild(label);
        if (reducedMotion.matches) setTimeout(() => label.remove(), 1200);
    }
};
