/**
 * UI Controller
 * Handles all DOM updates and visual feedback (no game logic)
 */

import { elements } from './domElements.js';
import { gameState } from './gameState.js';
import { modeInfo } from './modes.js';

const SCREENS = ['lobbyArea', 'leaderboardArea', 'waitingArea', 'gameArea', 'resultArea'];
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

const seconds = ms => `${(ms / 1000).toFixed(1)} s`;

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
        const invited = Boolean(gameState.inviteCode);
        elements.inviteBanner.hidden = !invited;
        elements.joinInviteButton.hidden = !invited;
        elements.playButton.hidden = invited;
        elements.friendButton.textContent = invited ? 'Find a random opponent instead' : 'Play a friend instead';
        this.showFriendOptions(false);
        this.setLobbyBusy(false);
        this.setNames('You', 'Opponent');
        this.resetGameBoard();
    },

    // While connecting, the lobby buttons must not start a second connection
    setLobbyBusy(busy) {
        [elements.playButton, elements.joinInviteButton, elements.friendButton, elements.createRoomButton]
            .forEach(b => { b.disabled = busy; });
    },

    // "Play a friend": swap the lobby buttons for the challenge picker
    showFriendOptions(show) {
        elements.friendOptions.hidden = !show;
        elements.friendButton.hidden = show;
        elements.playButton.hidden = show || Boolean(gameState.inviteCode);
        if (show) document.querySelector('input[name="mode"]:checked').focus();
    },

    selectedMode() {
        return document.querySelector('input[name="mode"]:checked').value || null;
    },

    // Announce the challenge type during the countdown
    setChallenge(type) {
        gameState.challengeType = type || 'CLASSIC';
        const info = modeInfo(gameState.challengeType);
        elements.countdownMode.textContent = info.name;
        const showKeys = info.keys && window.matchMedia('(hover: hover)').matches;
        elements.countdownTip.textContent = showKeys ? `${info.rule} ${info.keys}` : info.rule;
    },

    // mode: 'queue' (public matchmaking) or 'room' (waiting for an invited friend)
    showLeaderboard() {
        this.showScreen('leaderboardArea');
        elements.leaderboardStatus.textContent = 'Loading…';
        elements.leaderboardList.replaceChildren();
        elements.leaderboardYou.hidden = true;
    },

    renderLeaderboard(board) {
        const you = board.you;
        elements.leaderboardStatus.textContent = board.top.length ? '' : 'No winners yet. Win a game to be the first.';
        elements.leaderboardList.replaceChildren(...board.top.map(entry => {
            const li = document.createElement('li');
            if (you && you.rank === entry.rank) li.classList.add('is-you');
            const rank = document.createElement('span');
            rank.className = 'rank';
            rank.textContent = String(entry.rank);
            const name = document.createElement('span');
            name.className = 'player';
            name.textContent = entry.name;
            const wins = document.createElement('span');
            wins.className = 'wins';
            wins.textContent = String(entry.wins);
            const label = document.createElement('small');
            label.textContent = entry.wins === 1 ? 'win' : 'wins';
            wins.append(label);
            li.append(rank, name, wins);
            return li;
        }));
        elements.leaderboardYou.hidden = false;
        elements.leaderboardYou.textContent = you
            ? `You: ${you.wins} ${you.wins === 1 ? 'win' : 'wins'} in ${you.games} ${you.games === 1 ? 'game' : 'games'}`
              + (you.wins > 0 ? `, rank ${you.rank}` : '')
            : 'Play a game against a random opponent to get on the board.';
    },

    showLeaderboardError() {
        elements.leaderboardStatus.textContent = "Couldn't load the leaderboard. Try again in a moment.";
    },

    showWaiting(mode = 'queue') {
        this.showScreen('waitingArea');
        elements.queueState.hidden = mode !== 'queue';
        elements.roomState.hidden = mode !== 'room';
        elements.countdownState.hidden = true;
        if (mode === 'room') {
            elements.roomLink.value = 'Creating your link…';
            elements.roomCode.textContent = '';
            elements.copyLinkButton.disabled = true;
        }
    },

    showRoomLink(code, url) {
        elements.roomLink.value = url;
        elements.roomCode.textContent = code;
        elements.copyLinkButton.disabled = false;
        elements.copyLinkButton.textContent = 'Copy link';
        elements.shareLinkButton.hidden = typeof navigator.share !== 'function';
    },

    confirmLinkCopied() {
        elements.copyLinkButton.textContent = 'Copied';
        setTimeout(() => { elements.copyLinkButton.textContent = 'Copy link'; }, 2000);
    },

    setNames(you, them) {
        gameState.yourName = you;
        gameState.opponentName = them;
        [elements.matchupYou, elements.hudYouName, elements.finalYouName].forEach(el => { el.textContent = you; });
        [elements.matchupThem, elements.hudThemName, elements.finalThemName].forEach(el => { el.textContent = them; });
    },

    showCountdown(seconds) {
        this.showScreen('waitingArea');
        elements.queueState.hidden = true;
        elements.countdownState.hidden = false;
        // Re-insert the number so its "written in chalk" animation plays for every step
        const number = elements.countdownNumber;
        number.textContent = String(seconds);
        number.style.animation = 'none';
        void number.offsetWidth;
        number.style.animation = '';
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

    updateQuestion(message) {
        const format = message.format || 'NUMBER';
        elements.questionNumber.textContent = `Question ${message.questionNumber} of ${gameState.config.questionsPerGame}`;
        elements.questionText.textContent = message.questionText;
        gameState.questionFormat = format;
        gameState.currentAnswer = '';
        gameState.awaitingResult = false;
        gameState.chosenButton = null;
        this.updateAnswerDisplay('');

        const isChoice = format !== 'NUMBER';
        elements.gameArea.classList.toggle('is-choice', isChoice);
        elements.numberPad.hidden = isChoice;
        elements.choicePad.hidden = !isChoice;
        elements.questionPrompt.hidden = !isChoice;
        if (format === 'TRUE_FALSE') {
            elements.questionPrompt.textContent = 'True or false?';
            this.setChoice(elements.choiceLeft, 'True', 1, 'tf-true', '✓');
            this.setChoice(elements.choiceRight, 'False', 0, 'tf-false', '✗');
        } else if (format === 'CHOICE') {
            elements.questionPrompt.textContent = 'Which is bigger?';
            this.setChoice(elements.choiceLeft, message.options[0], 0);
            this.setChoice(elements.choiceRight, message.options[1], 1);
        }
    },

    setChoice(button, label, value, variant = '', mark = '') {
        button.className = `choice chalk ${variant}`.trim();
        button.dataset.value = String(value);
        button.replaceChildren();
        if (mark) {
            const markEl = document.createElement('span');
            markEl.className = 'choice-mark';
            markEl.setAttribute('aria-hidden', 'true');
            markEl.textContent = mark;
            button.append(markEl);
        }
        button.append(label);
    },

    updateScores(yourScore, opponentScore) {
        renderTally(elements.yourTally, yourScore, 'Your score');
        renderTally(elements.opponentTally, opponentScore, 'Opponent score');
    },

    // Mark the submitted answer as right or wrong until the next question arrives
    showAnswerFeedback(wasCorrect) {
        if (gameState.questionFormat !== 'NUMBER' && gameState.chosenButton) {
            gameState.chosenButton.classList.add(wasCorrect ? 'is-correct' : 'is-wrong');
            return;
        }
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
        const button = key instanceof HTMLElement ? key : [...elements.keys].find(k => k.dataset.key === key);
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

        this.renderStats(message.yourStats, message.opponentStats);

        elements.rematchButton.disabled = false;
        elements.rematchButton.textContent = 'Rematch';
        elements.rematchStatus.textContent = '';
    },

    renderStats(yours, theirs) {
        elements.stats.hidden = !yours;
        if (!yours) return;

        const parts = [`${yours.correct} of ${yours.answered} right`];
        if (yours.averageMillis != null) parts.push(`${seconds(yours.averageMillis)} per answer`);
        if (yours.fastestMillis != null) parts.push(`fastest ${seconds(yours.fastestMillis)}`);
        elements.statsSummary.textContent = yours.answered ? parts.join(', ') : 'No answers this round';

        const answers = yours.answers || [];
        elements.answerStrip.replaceChildren(...answers.map(a => {
            const li = document.createElement('li');
            li.className = a.correct ? 'ok' : 'miss';
            li.textContent = a.correct ? '✓' : '✗';
            return li;
        }));
        elements.answerList.replaceChildren(...answers.map(a => {
            const li = document.createElement('li');
            const question = document.createElement('span');
            question.textContent = `${a.number}. ${a.question}`;
            const given = document.createElement('span');
            given.className = 'given';
            given.textContent = a.answer;
            const mark = document.createElement('span');
            mark.className = `mark ${a.correct ? 'ok' : 'miss'}`;
            mark.textContent = a.correct ? '✓' : '✗';
            mark.setAttribute('aria-label', a.correct ? 'correct' : 'wrong');
            const time = document.createElement('span');
            time.className = 'time';
            time.textContent = seconds(a.millis);
            li.append(question, given, mark, time);
            return li;
        }));
        elements.answerList.closest('details').hidden = answers.length === 0;
        elements.answerList.closest('details').open = false;

        elements.opponentSummary.textContent = theirs && theirs.answered > 0
            ? `${gameState.opponentName}: ${theirs.correct} of ${theirs.answered} right`
            : '';
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
