// The WebSocket side of the game: one Connection per player. It enforces the per-connection rules
// (rate limit, join timeout, heartbeat), passes messages to the engine, and keeps the player's copy
// of the game timers (see engine.js for why they live here).

import { randomUUID } from 'node:crypto';
import http from 'node:http';
import { WebSocketServer, WebSocket } from 'ws';
import { hashToken } from './leaderboard.js';

const MAX_MESSAGES_PER_SECOND = 10;
const JOIN_TIMEOUT_MS = 15_000;
const SILENCE_LIMIT_MS = 10_000;
const SILENCE_CHECK_MS = 3_000;
const QUEUE_REFRESH_MS = 10_000;
const MAX_PAYLOAD_BYTES = 4 * 1024;
const POLICY_VIOLATION = 1008;
const GOING_AWAY = 1001;

class Connection {
  constructor(socket, { store, engine, background }) {
    this.socket = socket;
    this.store = store;
    this.engine = engine;
    this.background = background;
    this.id = randomUUID();
    this.joined = false;
    this.running = false;
    this.lastMessageAt = Date.now();
    this.windowStart = Date.now();
    this.messageCount = 0;
    this.timers = new Set();
    this.unsubscribe = null;

    // Messages are handled one at a time and only after the subscription is in place,
    // so nothing published to this player can be missed
    this.queue = store.subscribe(this.id, message => this.deliver(message)).then(off => {
      this.unsubscribe = off;
    });

    this.joinTimer = setTimeout(() => {
      if (!this.joined) socket.close(POLICY_VIOLATION, 'No JOIN_GAME');
    }, JOIN_TIMEOUT_MS);
    this.silenceCheck = setInterval(() => {
      if (this.running && Date.now() - this.lastMessageAt > SILENCE_LIMIT_MS) socket.close(GOING_AWAY, 'Silent');
    }, SILENCE_CHECK_MS);
    this.queueRefresh = null;

    socket.on('message', data => this.receive(data));
    socket.on('close', () => this.closed());
    socket.on('error', error => console.warn('Socket error:', error.message));
  }

  send(message) {
    if (this.socket.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(message));
  }

  /** A message published to this player. Types starting with _ are for this connection only. */
  deliver(message) {
    if (message.type === '_TIMER') return this.scheduleTimer(message);
    if (message.type === '_CANCEL_TIMERS') return this.cancelTimers();

    if (message.type === 'GAME_STATE') {
      this.stopRefreshingQueue();
      if (message.gameStatus === 'RUNNING') {
        this.running = true;
        this.lastMessageAt = Date.now(); // the countdown sends nothing that would count as activity
      }
    }
    if (message.type === 'GAME_OVER') this.running = false;
    this.send(message);
  }

  after(at, fn) {
    const timer = setTimeout(() => {
      this.timers.delete(timer);
      fn();
    }, Math.max(0, at - Date.now()));
    this.timers.add(timer);
  }

  run(work) {
    this.background(work().catch(error => console.error('Timer failed:', error)));
  }

  scheduleTimer(timer) {
    const { engine } = this;
    if (timer.kind === 'start') {
      // The countdown is only a display, so each connection shows it by itself
      for (let n = timer.countdown ?? 0; n >= 1; n--) {
        this.after(timer.at - n * 1000, () => this.send({ type: 'COUNTDOWN', countdown: n, message: timer.tip }));
      }
      this.after(timer.at, () => this.run(() => engine.startRound(timer.gameId)));
    } else if (timer.kind === 'end') {
      this.after(timer.at, () => this.run(() => engine.roundTimeUp(timer.gameId, timer.round)));
    } else if (timer.kind === 'challenge') {
      this.after(timer.at, () => this.run(() => engine.challengeTimedOut(timer.gameId, timer.challengeId)));
    }
  }

  cancelTimers() {
    for (const timer of this.timers) clearTimeout(timer);
    this.timers.clear();
  }

  stopRefreshingQueue() {
    clearInterval(this.queueRefresh);
    this.queueRefresh = null;
  }

  receive(data) {
    this.lastMessageAt = Date.now();

    if (Date.now() - this.windowStart > 1000) {
      this.windowStart = Date.now();
      this.messageCount = 0;
    }
    if (++this.messageCount > MAX_MESSAGES_PER_SECOND) {
      this.send({ type: 'ERROR', errorMessage: 'Too many messages - slow down!' });
      return;
    }

    let message;
    try {
      message = JSON.parse(data.toString());
    } catch {
      this.send({ type: 'ERROR', errorMessage: 'Error processing message' });
      return;
    }
    if (message?.type === 'HEARTBEAT') return;

    this.queue = this.queue.then(() => this.handle(message)).catch(error => {
      console.error('Error handling message:', error);
      this.send({ type: 'ERROR', errorMessage: 'Error processing message' });
    });
  }

  async handle(message) {
    const { engine, id } = this;
    switch (message?.type) {
      case 'JOIN_GAME':
        return this.join(message);
      case 'ANSWER':
        return engine.answer(id, message.answer);
      case 'REMATCH':
        return engine.rematch(id);
      case 'FORFEIT':
        return engine.forfeit(id);
      case 'CHALLENGE_RESPONSE':
        return engine.respondToChallenge(id, message.accept === true);
      case 'LEAVE':
        // On Vercel a closed socket takes seconds to reach the function, longer than the countdown.
        // The closed() path stays as the fallback for crashes and lost connections.
        return engine.leave(id);
      default:
        console.warn('Unknown message type:', message?.type);
    }
  }

  async join(message) {
    const { engine, id } = this;
    // The raw token stops here: only its hash is kept with the game
    const info = { id, name: message.playerName, tokenHash: hashToken(message.playerToken) };
    this.joined = true;

    if (message.createRoom) {
      await engine.createRoom(info, message.challengeType);
    } else if (typeof message.roomCode === 'string' && message.roomCode.trim() !== '') {
      await engine.challenge(info, message.roomCode);
    } else {
      const { waiting } = await engine.joinPublic(info);
      if (waiting && !this.queueRefresh) {
        this.queueRefresh = setInterval(() => {
          engine.refreshQueue().catch(error => console.warn('Queue refresh failed:', error.message));
        }, QUEUE_REFRESH_MS);
      }
    }
  }

  closed() {
    clearTimeout(this.joinTimer);
    clearInterval(this.silenceCheck);
    this.stopRefreshingQueue();
    this.cancelTimers();

    // waitUntil: the platform may otherwise freeze the instance as soon as the last socket is gone
    this.background(this.queue
      .then(() => this.engine.leave(this.id))
      .catch(error => console.error('Error while leaving:', error))
      .finally(() => this.unsubscribe?.()));
  }
}

/** Browsers always send Origin. Only pages served from this same site may open a game connection. */
function originAllowed(request, extraOrigins) {
  const origin = request.headers.origin;
  if (!origin) return true; // not a browser
  if (extraOrigins.includes(origin)) return true;
  try {
    const host = request.headers['x-forwarded-host'] ?? request.headers.host;
    return new URL(origin).host === host;
  } catch {
    return false;
  }
}

/** @returns an HTTP server handling the game's WebSocket upgrade; the caller decides how it runs */
export function createGameServer({ store, engine, background = promise => promise, allowedOrigins = [] }) {
  const server = http.createServer((request, response) => {
    response.writeHead(426, { 'Content-Type': 'text/plain' });
    response.end('This endpoint only speaks WebSocket.');
  });

  const sockets = new WebSocketServer({
    server,
    maxPayload: MAX_PAYLOAD_BYTES,
    verifyClient: ({ req }) => originAllowed(req, allowedOrigins),
  });
  sockets.on('connection', socket => {
    new Connection(socket, { store, engine, background });
  });

  return server;
}
