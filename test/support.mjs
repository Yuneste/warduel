// Shared test tools: an independent answer key, a scripted player and two running server instances.

import { randomUUID } from 'node:crypto';
import { WebSocket } from 'ws';
import { loadConfig } from '../lib/config.js';
import { createEngine } from '../lib/engine.js';
import { createGameServer } from '../lib/gameServer.js';
import { createMemoryLeaderboard } from '../lib/leaderboard.js';
import { createMemoryStore, createRedisStore } from '../lib/store.js';

// ---------- Solver: deliberately shares no code with the generator ----------

const SUPER = { '²': 2, '³': 3, '⁴': 4, '⁵': 5 };

export function value(text) {
  const s = text.trim();
  const t = s.split(' ');
  if (t.length === 3) {
    const [a, b] = [Number(t[0]), Number(t[2])];
    return { '+': a + b, '-': a - b, '×': a * b, '÷': a / b }[t[1]];
  }
  if (s.startsWith('√')) return Math.sqrt(Number(s.slice(1)));
  if (s.includes('/')) {
    const [p, q] = s.split('/');
    return Number(p) / Number(q);
  }
  if (SUPER[s.at(-1)]) return Number(s.slice(0, -1)) ** SUPER[s.at(-1)];
  return Number(s);
}

export function isTrueStatement(statement) {
  const [left, right] = statement.split(' = ');
  return Math.round(value(left)) === Number(right);
}

export function missing(equation) {
  const [left, right] = equation.split(' = ');
  const t = left.split(' ');
  const result = Number(right);
  const first = t[0] === '?';
  const known = Number(first ? t[2] : t[0]);
  return { '+': result - known, '-': first ? result + known : known - result, '×': result / known, '÷': first ? result * known : known / result }[t[1]];
}

export function solve(format, questionText, options) {
  if (format === 'TRUE_FALSE') return isTrueStatement(questionText) ? 1 : 0;
  if (format === 'CHOICE') return value(options[0]) > value(options[1]) ? 0 : 1;
  return questionText.includes('?') ? missing(questionText) : Math.round(value(questionText));
}

export const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

// ---------- A scripted game client ----------

export class TestPlayer {
  constructor(socket) {
    this.socket = socket;
    this.received = [];
    this.returned = new Set(); // indexes already handed out by next()
    this.waiters = new Set();
    socket.on('message', data => {
      this.received.push(JSON.parse(data.toString()));
      for (const wake of this.waiters) wake();
    });
  }

  static connect(port, headers) {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(`ws://localhost:${port}/api/game`, { headers });
      const player = new TestPlayer(socket);
      socket.once('open', () => resolve(player));
      socket.once('error', reject);
    });
  }

  send(message) {
    this.socket.send(JSON.stringify(message));
  }

  /** Enters public matchmaking (no name: the server picks "Player 1"/"Player 2") */
  join(playerName) {
    this.send({ type: 'JOIN_GAME', ...(playerName ? { playerName } : {}) });
  }

  answer(answer) {
    this.send({ type: 'ANSWER', answer });
  }

  /** Answers the most recent question correctly (any challenge type) and waits for the score update */
  answerCurrentCorrectly() {
    this.answer(TestPlayer.correctAnswer(this.last('QUESTION')));
    return this.next('SCORE_UPDATE', m => m.wasCorrect !== null && m.wasCorrect !== undefined);
  }

  static correctAnswer(q) {
    return solve(q.format ?? 'NUMBER', q.questionText, q.options ?? []);
  }

  /** Waits for the next not-yet-returned message of this type (already received ones count) */
  next(type, filter = () => true, timeoutMs = 10_000) {
    return new Promise((resolve, reject) => {
      const check = () => {
        for (let i = 0; i < this.received.length; i++) {
          const m = this.received[i];
          if (!this.returned.has(i) && m.type === type && filter(m)) {
            this.returned.add(i);
            finish();
            resolve(m);
            return;
          }
        }
      };
      const timer = setTimeout(() => {
        finish();
        reject(new Error(`No ${type} within ${timeoutMs}ms; received: ${JSON.stringify(this.received.map(m => m.type))}`));
      }, timeoutMs);
      const finish = () => {
        clearTimeout(timer);
        this.waiters.delete(check);
      };
      this.waiters.add(check);
      check();
    });
  }

  last(type) {
    const all = this.all(type);
    if (all.length === 0) throw new Error(`No ${type} received`);
    return all.at(-1);
  }

  all(type) {
    return this.received.filter(m => m.type === type);
  }

  /** Message types in arrival order */
  types() {
    return this.received.map(m => m.type);
  }

  close() {
    if (this.socket.readyState === WebSocket.OPEN) this.socket.close();
  }
}

// ---------- Two server instances sharing one store ----------

/**
 * Starts two independent servers. Players connect to them in turn, so the two players of a game are
 * on different instances, as can happen on Vercel. TEST_REDIS_URL runs everything on real Redis.
 */
export async function startCluster(overrides = {}) {
  const config = loadConfig({}, overrides);
  const redisUrl = process.env.TEST_REDIS_URL;
  const store = redisUrl ? createRedisStore(redisUrl, `test:${randomUUID()}:`) : createMemoryStore();
  const leaderboard = createMemoryLeaderboard();

  const servers = await Promise.all([0, 1].map(() => new Promise(resolve => {
    const engine = createEngine({ store, config, leaderboard });
    const server = createGameServer({ store, engine, allowedOrigins: ['https://allowed.example'] });
    server.listen(0, () => resolve(server));
  })));
  const ports = servers.map(server => server.address().port);

  let turn = 0;
  const players = [];

  return {
    config,
    leaderboard,
    ports,
    /** Connects without joining; alternates between the instances */
    async connectOnly() {
      const player = await TestPlayer.connect(ports[turn++ % ports.length]);
      players.push(player);
      return player;
    },
    /** Connects and joins public matchmaking */
    async connect(name) {
      const player = await this.connectOnly();
      player.join(name);
      await sleep(redisUrl ? 400 : 150); // keep join order deterministic (first to join becomes Player 1)
      return player;
    },
    async disconnectEveryone() {
      players.splice(0).forEach(player => player.close());
      await sleep(redisUrl ? 800 : 300); // let the servers process the disconnects before the next test
    },
    async stop() {
      await this.disconnectEveryone();
      await Promise.all(servers.map(server => new Promise(resolve => {
        server.closeAllConnections?.();
        server.close(resolve);
      })));
    },
  };
}
