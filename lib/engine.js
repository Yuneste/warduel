// The game rules and state machine: WAITING -> READY -> RUNNING -> FINISHED (a rematch goes FINISHED -> READY).
//
// Each game is one JSON document in the store. Every change happens under that game's lock, and
// messages are published while the lock is held, so both players see events in the same order no
// matter which server instance handled them.
//
// Timers can't live on the server as a whole, because any instance can disappear when its players
// leave. Instead each player's connection keeps its own copy of the game's timers (messages of type
// _TIMER) and calls back into the engine when one fires. The first call does the work; the state
// check makes the second one a no-op. So a game finishes as long as one of its players is connected.

import { randomBytes, randomUUID } from 'node:crypto';
import { sanitizeName } from './names.js';
import { createQuestionGenerator, formatAnswer, parseChallengeType, randomChallengeType } from './questions.js';

const GAME_TTL_MS = 60 * 60 * 1000;
const QUEUE_TTL_MS = 30 * 1000; // the waiting player's connection keeps refreshing this
const COUNTDOWN_SECONDS = 3;
const REMATCH_DELAY_MS = 1000;
const MAX_ANSWER_VALUE = 1_000_000;
// A forfeit or disconnect only counts for the leaderboard once the game ran this long
// (stops farming wins with two of your own browser tabs and an instant forfeit)
const MIN_MS_FOR_EARLY_END_TO_COUNT = 10_000;

const ROOM_CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; // no 0/O, 1/I/L
const ROOM_CODE_LENGTH = 6;

const gameKey = id => `game:${id}`;
const playerKey = id => `player:${id}`;
const pendingKey = id => `pending:${id}`;
const roomKey = code => `room:${code}`;
const QUEUE_KEY = 'queue';

function newRoomCode() {
  const bytes = randomBytes(ROOM_CODE_LENGTH);
  return Array.from(bytes, b => ROOM_CODE_ALPHABET[b % ROOM_CODE_ALPHABET.length]).join('');
}

const newPlayer = ({ id, name, tokenHash }) => ({
  id,
  name: sanitizeName(name),
  tokenHash: tokenHash ?? null,
  score: 0,
  index: 0,
  questions: [],
  log: [],
  shownAt: 0,
});

function resetRound(player) {
  player.score = 0;
  player.index = 0;
  player.log = [];
}

function statsOf(player, withAnswers) {
  const log = player?.log ?? [];
  const correct = log.filter(a => a.correct);
  const stats = {
    answered: log.length,
    correct: correct.length,
  };
  if (log.length > 0) stats.averageMillis = Math.round(log.reduce((sum, a) => sum + a.millis, 0) / log.length);
  if (correct.length > 0) stats.fastestMillis = Math.min(...correct.map(a => a.millis));
  if (withAnswers) stats.answers = log;
  return stats;
}

const outcomeOf = (score, other) => (score > other ? 'WIN' : score < other ? 'LOSS' : 'DRAW');
const challengerName = player => player.name || 'A friend';

export function createEngine({ store, config, leaderboard, background = promise => promise }) {
  const generateQuestions = createQuestionGenerator(config);
  const hasWinScore = config.winScore > 0;

  const tips = [
    'Solve math problems faster than your opponent!',
    'Type your answer and press Enter to submit',
    `First to answer ${hasWinScore ? config.winScore : config.questionsPerGame} questions correctly wins!`,
  ];

  /**
   * Loads a game, lets fn change it under the lock, saves it and publishes the messages fn queued
   * in `out`, in order.
   */
  async function update(gameId, fn) {
    const records = [];
    const result = await store.withLock(gameKey(gameId), async () => {
      const game = await store.get(gameKey(gameId));
      if (!game) return undefined;
      const out = [];
      const value = await fn(game, out, records);
      if (game.players.every(p => p === null) && !game.pendingChallenger) {
        await store.del(gameKey(gameId));
      } else {
        await store.set(gameKey(gameId), game, GAME_TTL_MS);
      }
      for (const [playerId, message] of out) {
        await store.publish(playerId, message);
      }
      return value;
    });
    for (const [tokenHash, name, outcome] of records) {
      background(leaderboard.record(tokenHash, name, outcome).catch(error => {
        console.error(`Could not record ${outcome} for ${name} on the leaderboard:`, error.message);
      }));
    }
    return result;
  }

  // ---------- Helpers working on a loaded game ----------

  function newGame(challengeType, roomCode = null) {
    return {
      id: randomUUID(),
      status: 'WAITING',
      round: 0,
      roomCode,
      challengeType,
      durationSeconds: config.durationSeconds,
      startedAt: null,
      endAt: null,
      players: [null, null],
      pendingChallenger: null,
      rematch: [false, false],
    };
  }

  function addPlayer(game, player) {
    if (game.players[0] === null) {
      player.name ||= 'Player 1';
      game.players[0] = player;
      return;
    }
    player.name ||= 'Player 2';
    // Two players with the same nickname would be indistinguishable on screen
    if (player.name.toLowerCase() === game.players[0].name.toLowerCase()) player.name += ' (2)';
    game.players[1] = player;
    game.status = 'READY';
  }

  function prepare(game) {
    game.durationSeconds = config.durationSeconds;
    for (const player of game.players) {
      player.questions = generateQuestions(game.challengeType, config.questionsPerGame);
    }
  }

  const gameState = (status, you, opponent, game, seconds) => ({
    type: 'GAME_STATE',
    gameStatus: status,
    yourName: you.name,
    opponentName: opponent.name,
    remainingSeconds: seconds,
    challengeType: game.challengeType,
  });

  function questionMessage(player, remainingSeconds) {
    const q = player.questions[player.index];
    player.shownAt = Date.now();
    return {
      type: 'QUESTION',
      questionText: q.questionText,
      questionNumber: player.index + 1,
      remainingSeconds,
      format: q.format,
      options: q.options,
    };
  }

  function beginCountdown(game, out) {
    const [a, b] = game.players;
    out.push([a.id, gameState('READY', a, b, game, game.durationSeconds)]);
    out.push([b.id, gameState('READY', b, a, game, game.durationSeconds)]);
    const timer = {
      type: '_TIMER',
      kind: 'start',
      gameId: game.id,
      at: Date.now() + COUNTDOWN_SECONDS * 1000,
      countdown: COUNTDOWN_SECONDS,
      tip: tips[Math.floor(Math.random() * tips.length)],
    };
    out.push([a.id, timer], [b.id, timer]);
  }

  /** Only the first caller gets true, so a game is announced once however it ends */
  function finish(game, out) {
    if (game.status === 'FINISHED') return false;
    game.status = 'FINISHED';
    for (const player of game.players) {
      if (player) out.push([player.id, { type: '_CANCEL_TIMERS' }]);
    }
    return true;
  }

  function gameOver(you, opponent, youWon, draw, winnerName, disconnectMessage = null) {
    return {
      type: 'GAME_OVER',
      yourScore: you.score,
      opponentScore: opponent?.score ?? 0,
      youWon,
      draw,
      winnerName,
      disconnectMessage,
      yourStats: statsOf(you, true),
      opponentStats: statsOf(opponent, false),
    };
  }

  function endByScore(game, out, records) {
    if (!finish(game, out)) return;
    const [a, b] = game.players;
    const draw = Boolean(a && b) && a.score === b.score;
    let winnerName = 'Unknown';
    if (a && b) winnerName = draw ? 'Draw' : a.score > b.score ? a.name : b.name;

    // Decided by score, not by name: two players may pick the same nickname
    if (a) out.push([a.id, gameOver(a, b, !draw && a.score > (b?.score ?? 0), draw, winnerName)]);
    if (b) out.push([b.id, gameOver(b, a, !draw && b.score > (a?.score ?? 0), draw, winnerName)]);

    if (a && b) {
      recordResult(game, a, outcomeOf(a.score, b.score), records);
      recordResult(game, b, outcomeOf(b.score, a.score), records);
    }
  }

  /** The winner gets a win with `reason`, the loser (if still connected) a loss */
  function endByDefault(game, out, winner, loser, reason) {
    if (winner) out.push([winner.id, gameOver(winner, loser, true, false, winner.name, reason)]);
    if (loser) out.push([loser.id, gameOver(loser, winner, false, false, winner?.name ?? 'Opponent')]);
  }

  /** Only public games count */
  function recordResult(game, player, outcome, records) {
    if (game.roomCode === null && player.tokenHash) records.push([player.tokenHash, player.name, outcome]);
  }

  function recordEarlyEnd(game, winner, loser, records) {
    if (game.startedAt === null || Date.now() - game.startedAt < MIN_MS_FOR_EARLY_END_TO_COUNT) return;
    if (winner) recordResult(game, winner, 'WIN', records);
    if (loser) recordResult(game, loser, 'LOSS', records);
  }

  const seatOf = (game, playerId) => game.players.findIndex(p => p?.id === playerId);

  const remainingSeconds = game => Math.max(0, Math.floor((game.endAt - Date.now()) / 1000));

  // ---------- Joining ----------

  /** Public matchmaking: fill the waiting game, or become the waiting game */
  async function joinPublic(info) {
    if (await store.get(playerKey(info.id))) return { waiting: false };
    const player = newPlayer(info);

    return store.withLock(QUEUE_KEY, async () => {
      const waitingId = await store.get(QUEUE_KEY);
      if (waitingId) {
        const matched = await update(waitingId, (game, out) => {
          if (game.status !== 'WAITING' || game.players[0] === null || game.players[1] !== null) return false;
          addPlayer(game, player);
          prepare(game);
          beginCountdown(game, out);
          return true;
        });
        if (matched) {
          await store.set(playerKey(player.id), waitingId, GAME_TTL_MS);
          await store.del(QUEUE_KEY);
          return { waiting: false };
        }
      }

      // Public games get a random challenge type so the queue never splits by preference
      const game = newGame(parseChallengeType(config.publicChallengeType) ?? randomChallengeType());
      addPlayer(game, player);
      await store.set(gameKey(game.id), game, GAME_TTL_MS);
      await store.set(playerKey(player.id), game.id, GAME_TTL_MS);
      await store.set(QUEUE_KEY, game.id, QUEUE_TTL_MS);
      return { waiting: true };
    });
  }

  /** The player waiting in the queue is still here */
  const refreshQueue = () => store.expire(QUEUE_KEY, QUEUE_TTL_MS);

  /** Opens a private game that only someone with the room code can challenge */
  async function createRoom(info, challengeType) {
    if (await store.get(playerKey(info.id))) return null;
    let code;
    const game = newGame(null);
    do {
      code = newRoomCode();
    } while (!(await store.setIfAbsent(roomKey(code), game.id, GAME_TTL_MS)));

    game.roomCode = code;
    game.challengeType = parseChallengeType(challengeType) ?? randomChallengeType();
    addPlayer(game, newPlayer(info));
    await store.set(gameKey(game.id), game, GAME_TTL_MS);
    await store.set(playerKey(info.id), game.id, GAME_TTL_MS);
    await store.publish(info.id, { type: 'ROOM_CREATED', roomCode: code });
    return code;
  }

  // ---------- Challenges through invite links ----------

  const roomNotFound = id => store.publish(id, { type: 'ERROR', errorMessage: 'Room not found' });

  /** Someone opened an invite link: they wait as the room's pending challenger until the host answers */
  async function challenge(info, roomCode) {
    const code = String(roomCode ?? '').trim().toUpperCase();
    const gameId = code ? await store.get(roomKey(code)) : null;
    const taken = (await store.get(playerKey(info.id))) || (await store.get(pendingKey(info.id)));
    if (!gameId || taken) return roomNotFound(info.id);

    const handled = await update(gameId, async (game, out) => {
      if (game.status !== 'WAITING' || game.players[0] === null || game.players[1] !== null) return false;
      const host = game.players[0];
      if (game.pendingChallenger) {
        out.push([info.id, { type: 'CHALLENGE_STATUS', status: 'BUSY', name: host.name, timeoutSeconds: null }]);
        return true;
      }
      const challenger = newPlayer(info);
      const timeout = config.challengeTimeoutSeconds;
      const challengeId = randomUUID();
      game.pendingChallenger = { ...challenger, challengeId };
      await store.set(pendingKey(info.id), game.id, GAME_TTL_MS);
      out.push([host.id, { type: 'CHALLENGE', fromName: challengerName(challenger), timeoutSeconds: timeout }]);
      out.push([info.id, { type: 'CHALLENGE_STATUS', status: 'SENT', name: host.name, timeoutSeconds: timeout }]);
      const timer = { type: '_TIMER', kind: 'challenge', gameId: game.id, challengeId, at: Date.now() + timeout * 1000 };
      out.push([host.id, timer], [info.id, timer]);
      return true;
    });
    if (!handled) await roomNotFound(info.id);
  }

  async function challengeTimedOut(gameId, challengeId) {
    await update(gameId, async (game, out) => {
      const challenger = game.pendingChallenger;
      if (challenger?.challengeId !== challengeId) return;
      game.pendingChallenger = null;
      await store.del(pendingKey(challenger.id));
      const host = game.players[0];
      out.push([challenger.id, { type: 'CHALLENGE_STATUS', status: 'TIMED_OUT', name: host?.name ?? null, timeoutSeconds: null }]);
      if (host) out.push([host.id, { type: 'CHALLENGE_STATUS', status: 'TIMED_OUT', name: challengerName(challenger), timeoutSeconds: null }]);
    });
  }

  async function respondToChallenge(hostId, accept) {
    const gameId = await store.get(playerKey(hostId));
    if (!gameId) return;
    await update(gameId, async (game, out) => {
      const challenger = game.pendingChallenger;
      // Too late: already timed out or cancelled
      if (!challenger || game.status !== 'WAITING' || seatOf(game, hostId) !== 0) return;
      game.pendingChallenger = null;
      await store.del(pendingKey(challenger.id));
      if (!accept) {
        out.push([challenger.id, { type: 'CHALLENGE_STATUS', status: 'DECLINED', name: game.players[0].name, timeoutSeconds: null }]);
        return;
      }
      delete challenger.challengeId;
      addPlayer(game, challenger);
      await store.set(playerKey(challenger.id), game.id, GAME_TTL_MS);
      await store.del(roomKey(game.roomCode)); // a room code works for one game
      prepare(game);
      beginCountdown(game, out);
    });
  }

  // ---------- Playing ----------

  /** Timer: the countdown or the rematch delay is over */
  async function startRound(gameId) {
    await update(gameId, (game, out) => {
      const [a, b] = game.players;
      if (game.status !== 'READY' || !a || !b) return;
      game.status = 'RUNNING';
      game.round += 1;
      game.startedAt = Date.now();
      game.endAt = game.startedAt + game.durationSeconds * 1000;
      resetRound(a);
      resetRound(b);

      // Both get the FULL duration, so the clocks agree
      const seconds = game.durationSeconds;
      out.push([a.id, gameState('RUNNING', a, b, game, seconds)]);
      out.push([b.id, gameState('RUNNING', b, a, game, seconds)]);
      out.push([a.id, questionMessage(a, seconds)]);
      out.push([b.id, questionMessage(b, seconds)]);
      const timer = { type: '_TIMER', kind: 'end', gameId: game.id, round: game.round, at: game.endAt };
      out.push([a.id, timer], [b.id, timer]);
    });
  }

  /** Timer: the round's time is up. A stale timer from an earlier round does nothing. */
  async function roundTimeUp(gameId, round) {
    await update(gameId, (game, out, records) => {
      if (game.status === 'RUNNING' && game.round === round) endByScore(game, out, records);
    });
  }

  async function answer(playerId, value) {
    const reject = errorMessage => store.publish(playerId, { type: 'ERROR', errorMessage });
    if (!Number.isInteger(value)) return reject('Error processing message');
    if (Math.abs(value) > MAX_ANSWER_VALUE) return reject('Answer out of valid range');

    const gameId = await store.get(playerKey(playerId));
    if (!gameId) return reject('Game not found or not active');

    // The question index only moves under this lock, so each question can be answered only once
    const found = await update(gameId, (game, out, records) => {
      const seat = seatOf(game, playerId);
      if (game.status !== 'RUNNING' || seat < 0) {
        out.push([playerId, { type: 'ERROR', errorMessage: 'Game not found or not active' }]);
        return true;
      }
      if (Date.now() > game.endAt) {
        out.push([playerId, { type: 'ERROR', errorMessage: 'Time has expired' }]);
        return true;
      }

      const player = game.players[seat];
      const opponent = game.players[1 - seat];
      const question = player.questions[player.index];
      if (!question) return true;

      const correct = question.correctAnswer === value;
      if (correct) player.score += 1;
      player.log.push({
        number: player.index + 1,
        question: question.questionText,
        answer: formatAnswer(question, value),
        correct,
        millis: Date.now() - player.shownAt,
      });

      out.push([player.id, { type: 'SCORE_UPDATE', yourScore: player.score, opponentScore: opponent?.score ?? 0, wasCorrect: correct }]);
      if (opponent) {
        // null: the opponent didn't answer
        out.push([opponent.id, { type: 'SCORE_UPDATE', yourScore: opponent.score, opponentScore: player.score, wasCorrect: null }]);
      }

      if (hasWinScore && player.score >= config.winScore) {
        endByScore(game, out, records);
        return true;
      }

      player.index += 1;
      if (player.questions[player.index]) out.push([player.id, questionMessage(player, remainingSeconds(game))]);
      return true;
    });
    if (!found) await reject('Game not found or not active');
  }

  async function forfeit(playerId) {
    const gameId = await store.get(playerKey(playerId));
    if (!gameId) return;
    await update(gameId, (game, out, records) => {
      const seat = seatOf(game, playerId);
      if (game.status !== 'RUNNING' || seat < 0) return;
      finish(game, out);
      const quitter = game.players[seat];
      const opponent = game.players[1 - seat];
      endByDefault(game, out, opponent, quitter, `${quitter.name} forfeited`);
      recordEarlyEnd(game, opponent, quitter, records);
    });
  }

  async function rematch(playerId) {
    const opponentLeft = () => store.publish(playerId, { type: 'ERROR', errorMessage: 'Opponent left' });
    const gameId = await store.get(playerKey(playerId));
    if (!gameId) return opponentLeft();

    const found = await update(gameId, (game, out) => {
      const seat = seatOf(game, playerId);
      if (seat < 0 || !game.players[1 - seat]) {
        out.push([playerId, { type: 'ERROR', errorMessage: 'Opponent left' }]);
        return true;
      }
      if (game.status !== 'FINISHED') return true;

      game.rematch[seat] = true;
      if (!game.rematch[1 - seat]) {
        out.push([playerId, { type: 'REMATCH', requestRematch: true, opponentAccepted: false, statusMessage: 'Waiting for opponent...' }]);
        return true;
      }

      // Both want it: same challenge type, new questions, no countdown
      game.status = 'READY';
      game.rematch = [false, false];
      game.startedAt = null;
      game.endAt = null;
      prepare(game);
      const [a, b] = game.players;
      const starting = { type: 'REMATCH', requestRematch: true, opponentAccepted: true, statusMessage: 'Rematch starting!' };
      const timer = { type: '_TIMER', kind: 'start', gameId: game.id, at: Date.now() + REMATCH_DELAY_MS };
      out.push([a.id, starting], [b.id, starting], [a.id, timer], [b.id, timer]);
      return true;
    });
    if (!found) await opponentLeft();
  }

  // ---------- Leaving ----------

  async function leave(playerId) {
    // A challenger who leaves while waiting: tell the host and close the popup
    const challengedId = await store.get(pendingKey(playerId));
    if (challengedId) {
      await store.del(pendingKey(playerId));
      await update(challengedId, (game, out) => {
        if (game.pendingChallenger?.id !== playerId) return;
        game.pendingChallenger = null;
        const host = game.players[0];
        if (host) out.push([host.id, { type: 'CHALLENGE_STATUS', status: 'CANCELLED', name: null, timeoutSeconds: null }]);
      });
      return;
    }

    const gameId = await store.get(playerKey(playerId));
    if (!gameId) return;
    await store.del(playerKey(playerId));

    await update(gameId, async (game, out, records) => {
      const seat = seatOf(game, playerId);
      if (seat < 0) return;
      const leaver = game.players[seat];
      const opponent = game.players[1 - seat];

      // A private game whose host left can no longer be joined; a waiting challenger is let go
      if (game.roomCode !== null) await store.del(roomKey(game.roomCode));
      const waiting = game.pendingChallenger;
      if (waiting) {
        game.pendingChallenger = null;
        await store.del(pendingKey(waiting.id));
        out.push([waiting.id, { type: 'CHALLENGE_STATUS', status: 'HOST_LEFT', name: leaver.name, timeoutSeconds: null }]);
      }

      const status = game.status;
      const pendingRematch = game.round > 0;
      game.players[seat] = null;

      if (status === 'FINISHED') {
        // Player left the result screen: disable the opponent's rematch button
        if (opponent) out.push([opponent.id, { type: 'ERROR', errorMessage: 'Opponent left' }]);
      } else if (status === 'RUNNING') {
        finish(game, out);
        recordEarlyEnd(game, opponent, leaver, records);
        if (opponent) {
          endByDefault(game, out, opponent, null, `${leaver.name} disconnected`);
          // A rematch is impossible now; lets the client disable its button
          out.push([opponent.id, { type: 'ERROR', errorMessage: 'Opponent left' }]);
        }
      } else {
        // Countdown, queue or pending rematch: cancel the match
        finish(game, out);
        if (opponent) {
          out.push([opponent.id, { type: 'ERROR', errorMessage: pendingRematch ? 'Opponent left' : 'Opponent left the queue' }]);
        }
      }
    });
  }

  return {
    joinPublic,
    refreshQueue,
    createRoom,
    challenge,
    challengeTimedOut,
    respondToChallenge,
    startRound,
    roundTimeUp,
    answer,
    forfeit,
    rematch,
    leave,
  };
}
