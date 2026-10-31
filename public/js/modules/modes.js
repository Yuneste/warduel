/**
 * Challenge types: how each one is named and explained to the player
 */

export const MODES = {
    CLASSIC: {
        name: 'Classic',
        rule: 'Work out each problem and type the answer.'
    },
    TRUE_FALSE: {
        name: 'True or false',
        rule: 'Is the equation right? Tap True or False.',
        keys: 'Keys: T / F or ← →'
    },
    MISSING_NUMBER: {
        name: 'Missing number',
        rule: 'Find the number hidden behind the question mark.'
    },
    COMPARE: {
        name: 'Which is bigger?',
        rule: 'Tap the side with the bigger value.',
        keys: 'Keys: ← →'
    }
};

export const modeInfo = type => MODES[type] || MODES.CLASSIC;
