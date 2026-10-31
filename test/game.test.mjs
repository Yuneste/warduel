// Plays real matches over WebSocket against two server instances, with each pair of players split
// across them. Rounds are shortened to 5 seconds and the win score to 3 so the suite stays fast; the
// 3-second countdown and 1-second rematch delay are the real ones.

import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { after, afterEach, before, describe, it } from 'node:test';
import { WebSocket } from 'ws';
import { sleep, startCluster, TestPlayer } from './support.mjs';

const DURATION = 5;
const SHORT = 5_000;
const LONG = (3 + DURATION + 3) * 1000;
const hasAnswer = m => m.wasCorrect !== null && m.wasCorrect !== undefined;

describe('a duel across two server instances', () => {
  let cluster;

  before(async () => {
    cluster = await startCluster({ durationSeconds: DURATION, winScore: 3, challengeTimeoutSeconds: 2 });
  });
  afterEach(() => cluster.disconnectEveryone());
  after(() => cluster.stop());

  const connect = name => cluster.connect(name);
  const connectOnly = () => cluster.connectOnly();

  const openRoom = async (host, playerName) => {
    host.send({ type: 'JOIN_GAME', playerName, createRoom: true });
    return (await host.next('ROOM_CREATED')).roomCode;
  };

  /** Host opens a private game of the given challenge type and a friend joins it */
  const privateGame = async challengeType => {
    const host = await connectOnly();
    host.send({ type: 'JOIN_GAME', createRoom: true, challengeType });
    const { roomCode } = await host.next('ROOM_CREATED');
    const friend = await connectOnly();
    friend.send({ type: 'JOIN_GAME', roomCode });
    await host.next('CHALLENGE');
    host.send({ type: 'CHALLENGE_RESPONSE', accept: true });
    return [host, friend];
  };

  it('counts down, then ends by the timer', async () => {
    const a = await connect();
    const b = await connect();

    for (const n of [3, 2, 1]) await a.next('COUNTDOWN', m => m.countdown === n, SHORT);
    const state = await a.next('GAME_STATE', m => m.gameStatus === 'RUNNING', SHORT);
    assert.equal(state.yourName, 'Player 1');
    assert.equal(state.opponentName, 'Player 2');

    const q1 = await a.next('QUESTION');
    assert.equal(q1.questionNumber, 1);
    assert.equal(q1.remainingSeconds, DURATION);
    await b.next('QUESTION');

    const score = await a.answerCurrentCorrectly();
    assert.equal(score.wasCorrect, true);
    assert.equal(score.yourScore, 1);
    assert.equal((await b.next('SCORE_UPDATE')).opponentScore, 1);

    // The clock started after the countdown, so the next question still has (almost) the full round left
    const q2 = await a.next('QUESTION');
    assert.ok(q2.remainingSeconds >= DURATION - 1);

    b.answer(-999);
    await b.next('SCORE_UPDATE', m => m.yourScore === 0 && m.wasCorrect === false, SHORT);

    const overA = await a.next('GAME_OVER', () => true, LONG);
    const overB = await b.next('GAME_OVER', () => true, SHORT);
    assert.equal(overA.youWon, true);
    assert.equal(overA.yourScore, 1);
    assert.equal(overB.youWon, false);
    assert.equal(overB.opponentScore, 1);
    await sleep(300);
    assert.equal(a.all('GAME_OVER').length, 1, 'both instances fire the timer, only one ends the game');
  });

  it('rejects answers during the countdown', async () => {
    const a = await connect();
    await connect();

    await a.next('COUNTDOWN');
    a.answer(1);

    assert.match((await a.next('ERROR')).errorMessage, /not active/);
    await a.next('QUESTION', () => true, SHORT);
    assert.deepEqual(a.all('SCORE_UPDATE'), []);
  });

  it('cancels the match when someone leaves during the countdown', async () => {
    const a = await connect();
    const b = await connect();
    await a.next('COUNTDOWN');

    b.close();

    assert.equal((await a.next('ERROR')).errorMessage, 'Opponent left the queue');
    await sleep(3500); // past the moment the game would have started
    assert.deepEqual(a.all('GAME_OVER'), [], 'no free win');
    assert.deepEqual(a.all('QUESTION'), [], 'game never starts');
  });

  it('cancels the countdown as soon as a player announces leaving, before the socket closes', async () => {
    const a = await connect();
    const b = await connect();
    await a.next('COUNTDOWN');

    b.send({ type: 'LEAVE' }); // the socket stays open, as if its close were still on the way

    assert.equal((await a.next('ERROR')).errorMessage, 'Opponent left the queue');
    b.close();
    await sleep(3500);
    assert.deepEqual(a.all('QUESTION'), [], 'game never starts');
    assert.equal(a.all('ERROR').length, 1, 'the later socket close changes nothing');
  });

  it('gives the win at once when a player announces leaving mid-game', async () => {
    const a = await connect();
    const b = await connect();
    await a.next('QUESTION', () => true, SHORT);

    b.send({ type: 'LEAVE' });

    assert.equal((await a.next('GAME_OVER')).disconnectMessage, 'Player 2 disconnected');
    b.close();
    await sleep(500);
    assert.equal(a.all('GAME_OVER').length, 1);
  });

  it('ends at the win score, and the old timer cannot end the rematch', async () => {
    const a = await connect();
    const b = await connect();
    let firstRoundStart = 0;

    for (let i = 1; i <= 3; i++) {
      await a.next('QUESTION', m => m.questionNumber === i, SHORT);
      if (i === 1) firstRoundStart = Date.now();
      await a.answerCurrentCorrectly();
    }
    const won = await a.next('GAME_OVER');
    assert.equal(won.youWon, true);
    assert.equal(won.yourScore, 3);
    await b.next('GAME_OVER');

    a.send({ type: 'REMATCH', requestRematch: true });
    b.send({ type: 'REMATCH', requestRematch: true });
    const rematchQuestion = await a.next('QUESTION', m => m.questionNumber === 1, SHORT);
    assert.equal(rematchQuestion.remainingSeconds, DURATION);
    assert.equal(a.all('COUNTDOWN').length, 3, 'a rematch has no countdown of its own');
    await b.next('GAME_STATE', m => m.gameStatus === 'RUNNING', SHORT);

    for (const player of [a, b]) {
      const accepted = player.all('REMATCH').map(m => m.opponentAccepted);
      assert.equal(accepted.at(-1), true);
      assert.equal(accepted.indexOf(true), accepted.length - 1, "'waiting' must never follow 'starting'");
    }

    // Wait until just after the first round's timer would have fired
    await sleep(Math.max(0, DURATION * 1000 + 500 - (Date.now() - firstRoundStart)));
    assert.equal(a.all('GAME_OVER').length, 1, 'old timer must not end the rematch');

    const rematchOver = await a.next('GAME_OVER', () => true, (DURATION + 2) * 1000);
    assert.equal(rematchOver.draw, true);
    await sleep(500);
    assert.equal(a.all('GAME_OVER').length, 2);
  });

  it('gives the opponent the win with real scores on a forfeit', async () => {
    const a = await connect();
    const b = await connect();
    await a.next('QUESTION', () => true, SHORT);
    await b.next('QUESTION', () => true, SHORT);
    await a.answerCurrentCorrectly();
    await b.answerCurrentCorrectly();

    b.send({ type: 'FORFEIT' });

    const winner = await a.next('GAME_OVER');
    const loser = await b.next('GAME_OVER');
    assert.equal(winner.youWon, true);
    assert.equal(winner.disconnectMessage, 'Player 2 forfeited');
    assert.equal(winner.yourScore, 1);
    assert.equal(winner.opponentScore, 1);
    assert.equal(loser.youWon, false);
    assert.equal(loser.yourScore, 1);
  });

  it('gives the opponent the win on a disconnect', async () => {
    const a = await connect();
    const b = await connect();
    await a.next('QUESTION', () => true, SHORT);

    b.close();

    const over = await a.next('GAME_OVER');
    assert.equal(over.youWon, true);
    assert.equal(over.disconnectMessage, 'Player 2 disconnected');
    assert.equal((await a.next('ERROR')).errorMessage, 'Opponent left', 'rematch no longer possible');
  });

  it('refuses a rematch once the opponent left', async () => {
    const a = await connect();
    const b = await connect();
    await a.next('QUESTION', () => true, SHORT);
    b.send({ type: 'FORFEIT' });
    await a.next('GAME_OVER');

    b.close();
    assert.equal((await a.next('ERROR')).errorMessage, 'Opponent left');

    a.send({ type: 'REMATCH', requestRematch: true });
    assert.equal((await a.next('ERROR')).errorMessage, 'Opponent left');
    assert.deepEqual(a.all('REMATCH'), []);
  });

  it('rate limits floods of messages', async () => {
    const a = await connect();
    for (let i = 0; i < 15; i++) a.send({ type: 'HEARTBEAT' });

    assert.match((await a.next('ERROR')).errorMessage, /Too many messages/);
  });

  it('counts each answer once when many arrive at the same time', async () => {
    const [a] = await privateGame('CLASSIC');
    await a.next('QUESTION', () => true, SHORT);
    for (let i = 0; i < 5; i++) a.answer(-999);

    await a.next('QUESTION', m => m.questionNumber === 6, SHORT);
    const updates = a.all('SCORE_UPDATE').filter(hasAnswer);
    assert.equal(updates.length, 5);
    assert.deepEqual(a.all('QUESTION').map(q => q.questionNumber), [1, 2, 3, 4, 5, 6]);
  });

  it('rejects connections from foreign pages but allows its own', async () => {
    const [port] = cluster.ports;
    const attempt = origin => new Promise(resolve => {
      const socket = new WebSocket(`ws://localhost:${port}/api/game`, { headers: { Origin: origin } });
      socket.once('open', () => {
        socket.close();
        resolve('open');
      });
      socket.once('unexpected-response', (request, response) => resolve(response.statusCode));
      socket.once('error', () => {});
    });
    assert.equal(await attempt('https://evil.example'), 401);
    assert.equal(await attempt(`http://localhost:${port}`), 'open');
    assert.equal(await attempt('https://allowed.example'), 'open');
  });

  // ---------- Challenges ----------

  it('makes the host accept a challenge from an invite link', async () => {
    const host = await connectOnly();
    const code = await openRoom(host, 'Yuko');
    const friend = await connectOnly();
    friend.send({ type: 'JOIN_GAME', playerName: 'Kai', roomCode: code });

    const challenge = await host.next('CHALLENGE');
    assert.equal(challenge.fromName, 'Kai');
    assert.equal(challenge.timeoutSeconds, 2);
    const sent = await friend.next('CHALLENGE_STATUS');
    assert.equal(sent.status, 'SENT');
    assert.equal(sent.name, 'Yuko');
    await sleep(300);
    assert.deepEqual(friend.all('COUNTDOWN'), [], 'nothing starts before the host accepts');

    host.send({ type: 'CHALLENGE_RESPONSE', accept: true });
    assert.equal((await host.next('GAME_STATE')).opponentName, 'Kai');
    await friend.next('COUNTDOWN');
  });

  it('keeps the link working after a decline', async () => {
    const host = await connectOnly();
    const code = await openRoom(host, 'Yuko');
    const friend = await connectOnly();
    friend.send({ type: 'JOIN_GAME', playerName: 'Kai', roomCode: code });
    await host.next('CHALLENGE');

    host.send({ type: 'CHALLENGE_RESPONSE', accept: false });
    const declined = await friend.next('CHALLENGE_STATUS', m => m.status !== 'SENT', SHORT);
    assert.equal(declined.status, 'DECLINED');
    friend.close();

    const again = await connectOnly();
    again.send({ type: 'JOIN_GAME', playerName: 'Kai', roomCode: code });
    assert.equal((await host.next('CHALLENGE')).fromName, 'Kai', 'second try reaches the host');
  });

  it('times out an unanswered challenge for both', async () => {
    const host = await connectOnly();
    const code = await openRoom(host, 'Yuko');
    const friend = await connectOnly();
    friend.send({ type: 'JOIN_GAME', playerName: 'Kai', roomCode: code });
    await host.next('CHALLENGE');

    const toFriend = await friend.next('CHALLENGE_STATUS', m => m.status === 'TIMED_OUT', SHORT);
    assert.equal(toFriend.name, 'Yuko');
    assert.equal((await host.next('CHALLENGE_STATUS')).status, 'TIMED_OUT');
    await sleep(300);
    assert.equal(host.all('CHALLENGE_STATUS').length, 1, 'both instances fire the timer, one announces it');

    host.send({ type: 'CHALLENGE_RESPONSE', accept: true }); // too late
    await sleep(300);
    assert.deepEqual(friend.all('COUNTDOWN'), []);
  });

  it('tells a second challenger the host is busy', async () => {
    const host = await connectOnly();
    const code = await openRoom(host, 'Yuko');
    const first = await connectOnly();
    first.send({ type: 'JOIN_GAME', roomCode: code });
    await host.next('CHALLENGE');

    const second = await connectOnly();
    second.send({ type: 'JOIN_GAME', roomCode: code });
    assert.equal((await second.next('CHALLENGE_STATUS')).status, 'BUSY');
  });

  it('handles a challenger or host leaving mid-challenge', async () => {
    const host = await connectOnly();
    const code = await openRoom(host, 'Yuko');
    const friend = await connectOnly();
    friend.send({ type: 'JOIN_GAME', roomCode: code });
    await host.next('CHALLENGE');
    friend.close();
    assert.equal((await host.next('CHALLENGE_STATUS')).status, 'CANCELLED');

    const other = await connectOnly();
    other.send({ type: 'JOIN_GAME', roomCode: code });
    await host.next('CHALLENGE');
    host.close();
    const left = await other.next('CHALLENGE_STATUS', m => m.status === 'HOST_LEFT', SHORT);
    assert.equal(left.name, 'Yuko');
  });

  // ---------- Leaderboard ----------

  const youOnBoard = async (token, games) => {
    for (let i = 0; i < 40; i++) {
      const { you } = await cluster.leaderboard.board(token);
      if (you && you.games >= games) return you;
      await sleep(50);
    }
    return (await cluster.leaderboard.board(token)).you;
  };

  it('puts public wins and losses on the leaderboard', async () => {
    const winnerToken = randomUUID();
    const loserToken = randomUUID();
    const a = await connectOnly();
    a.send({ type: 'JOIN_GAME', playerName: 'Champ', playerToken: winnerToken });
    await sleep(400);
    const b = await connectOnly();
    b.send({ type: 'JOIN_GAME', playerName: 'Rival', playerToken: loserToken });

    for (let i = 1; i <= 3; i++) {
      await a.next('QUESTION', m => m.questionNumber === i, 6000);
      await a.answerCurrentCorrectly();
    }
    await a.next('GAME_OVER');

    const champ = await youOnBoard(winnerToken, 1);
    assert.equal(champ.name, 'Champ');
    assert.equal(champ.wins, 1);
    assert.ok(champ.rank > 0);
    assert.equal((await youOnBoard(loserToken, 1)).losses, 1);
    const board = await cluster.leaderboard.board(null);
    assert.ok(board.top.some(e => e.name === 'Champ'));
    assert.equal(board.you, null);
  });

  it('ignores private games and instant forfeits on the leaderboard', async () => {
    const hostToken = randomUUID();
    const host = await connectOnly();
    host.send({ type: 'JOIN_GAME', createRoom: true, challengeType: 'CLASSIC', playerToken: hostToken });
    const { roomCode } = await host.next('ROOM_CREATED');
    const friend = await connectOnly();
    friend.send({ type: 'JOIN_GAME', roomCode });
    await host.next('CHALLENGE');
    host.send({ type: 'CHALLENGE_RESPONSE', accept: true });
    for (let i = 1; i <= 3; i++) {
      await host.next('QUESTION', m => m.questionNumber === i, 6000);
      await host.answerCurrentCorrectly();
    }
    await host.next('GAME_OVER');

    const quitterToken = randomUUID();
    const stayerToken = randomUUID();
    const stayer = await connectOnly();
    stayer.send({ type: 'JOIN_GAME', playerToken: stayerToken });
    await sleep(400);
    const quitter = await connectOnly();
    quitter.send({ type: 'JOIN_GAME', playerToken: quitterToken });
    await quitter.next('QUESTION', () => true, 6000);
    quitter.send({ type: 'FORFEIT' });
    await stayer.next('GAME_OVER');

    await sleep(500);
    assert.equal((await cluster.leaderboard.board(hostToken)).you, null, 'private game');
    assert.equal((await cluster.leaderboard.board(stayerToken)).you, null, 'forfeit in the first seconds');
    assert.equal((await cluster.leaderboard.board(quitterToken)).you, null);
  });

  // ---------- Challenge types ----------

  it('asks statements in a true-or-false game', async () => {
    const [a, b] = await privateGame('TRUE_FALSE');
    assert.equal((await a.next('GAME_STATE')).challengeType, 'TRUE_FALSE');

    const q = await a.next('QUESTION', () => true, SHORT);
    assert.equal(q.format, 'TRUE_FALSE');
    assert.match(q.questionText, /^\d+ [-+×÷] \d+ = \d+$/);
    assert.equal((await a.answerCurrentCorrectly()).wasCorrect, true);

    const q2 = await a.next('QUESTION', m => m.questionNumber === 2, SHORT);
    const wrong = 1 - TestPlayer.correctAnswer(q2);
    a.answer(wrong);
    await a.next('SCORE_UPDATE', m => m.wasCorrect === false, SHORT);
    b.send({ type: 'FORFEIT' });

    const { answers } = (await a.next('GAME_OVER')).yourStats;
    assert.equal(answers[1].answer, wrong === 1 ? 'True' : 'False');
  });

  it('offers two options in a compare game', async () => {
    const [a] = await privateGame('COMPARE');
    const q = await a.next('QUESTION', () => true, SHORT);

    assert.equal(q.format, 'CHOICE');
    assert.equal(q.options.length, 2);
    assert.equal((await a.answerCurrentCorrectly()).wasCorrect, true);
  });

  it('hides an operand in a missing-number game', async () => {
    const [a] = await privateGame('MISSING_NUMBER');
    const q = await a.next('QUESTION', () => true, SHORT);

    assert.equal(q.format, 'NUMBER');
    assert.ok(q.questionText.includes('?'));
    assert.equal((await a.answerCurrentCorrectly()).wasCorrect, true);
  });

  it('announces the challenge type of public games', async () => {
    const a = await connect();
    await connect();

    assert.ok(['CLASSIC', 'TRUE_FALSE', 'MISSING_NUMBER', 'COMPARE'].includes((await a.next('GAME_STATE')).challengeType));
  });

  // ---------- Nicknames, private games, stats ----------

  it('introduces nicknames before the countdown', async () => {
    const a = await connect('Yuko');
    const b = await connect('Kai <script>');

    const intro = await a.next('GAME_STATE');
    assert.equal(intro.gameStatus, 'READY');
    assert.equal(intro.yourName, 'Yuko');
    assert.equal(intro.opponentName, 'Kai script', 'unsafe characters removed');
    await a.next('COUNTDOWN');
    assert.ok(a.types().indexOf('GAME_STATE') < a.types().indexOf('COUNTDOWN'), 'intro comes before the countdown');

    await a.next('QUESTION', () => true, SHORT);
    b.close();
    assert.equal((await a.next('GAME_OVER')).disconnectMessage, 'Kai script disconnected');
  });

  it('keeps identical nicknames distinguishable', async () => {
    const a = await connect('Kai');
    await connect('kai');

    assert.equal((await a.next('GAME_STATE')).opponentName, 'kai (2)');
  });

  it('joins private games only through their code', async () => {
    const host = await connectOnly();
    host.send({ type: 'JOIN_GAME', playerName: 'Host', createRoom: true });
    const { roomCode } = await host.next('ROOM_CREATED');
    assert.match(roomCode, /^[A-Z2-9]{6}$/);

    const stranger = await connect('Stranger'); // public matchmaking must not pair with the private game
    const friend = await connectOnly();
    friend.send({ type: 'JOIN_GAME', playerName: 'Friend', roomCode: roomCode.toLowerCase() });
    await host.next('CHALLENGE');
    host.send({ type: 'CHALLENGE_RESPONSE', accept: true });

    assert.equal((await host.next('GAME_STATE')).opponentName, 'Friend');
    await friend.next('COUNTDOWN');
    await sleep(500);
    assert.deepEqual(stranger.all('COUNTDOWN'), [], 'stranger keeps waiting');

    const late = await connectOnly();
    late.send({ type: 'JOIN_GAME', roomCode });
    assert.equal((await late.next('ERROR')).errorMessage, 'Room not found', 'a code works once');
  });

  it('rejects unknown room codes', async () => {
    const a = await connectOnly();
    a.send({ type: 'JOIN_GAME', roomCode: 'NOPE42' });

    assert.equal((await a.next('ERROR')).errorMessage, 'Room not found');
  });

  it('closes a private game when the host leaves', async () => {
    const host = await connectOnly();
    host.send({ type: 'JOIN_GAME', createRoom: true });
    const { roomCode } = await host.next('ROOM_CREATED');
    host.close();
    await sleep(500);

    const friend = await connectOnly();
    friend.send({ type: 'JOIN_GAME', roomCode });
    assert.equal((await friend.next('ERROR')).errorMessage, 'Room not found');
  });

  it('carries answer stats in GAME_OVER', async () => {
    const [a, b] = await privateGame('CLASSIC');
    await a.next('QUESTION', () => true, SHORT);
    await a.answerCurrentCorrectly();
    await a.next('QUESTION', m => m.questionNumber === 2, SHORT);
    a.answer(-999);
    await a.next('SCORE_UPDATE', m => m.wasCorrect === false, SHORT);
    b.send({ type: 'FORFEIT' });

    const stats = (await a.next('GAME_OVER')).yourStats;
    assert.equal(stats.answered, 2);
    assert.equal(stats.correct, 1);
    assert.ok(stats.averageMillis >= 0);
    assert.equal(stats.answers.length, 2);
    assert.equal(stats.answers[0].correct, true);
    assert.equal(stats.answers[1].answer, '-999');
    assert.ok(stats.answers[1].question.length > 0);

    const { opponentStats } = await b.next('GAME_OVER');
    assert.equal(opponentStats.correct, 1);
    assert.equal('answers' in opponentStats, false, "the opponent's answers stay private");
  });
});
