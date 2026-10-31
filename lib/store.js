// Shared state and messaging between server instances.
//
// On Vercel the two players of a game can be connected to different function instances, so nothing
// about a game may live only in one instance's memory. The Redis store holds the state and carries
// messages between instances over pub/sub. The memory store has the same interface for local
// development and tests.

import { randomUUID } from 'node:crypto';
import { EventEmitter } from 'node:events';
import Redis from 'ioredis';

const LOCK_TTL_MS = 5000;
const LOCK_WAIT_MS = 8000;
const IDLE_DISCONNECT_MS = 5000;
const RELEASE_LOCK = `if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0`;

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

export function createMemoryStore() {
  const data = new Map();
  const locks = new Map();
  const bus = new EventEmitter();
  bus.setMaxListeners(0);

  const alive = key => {
    const entry = data.get(key);
    if (entry && entry.expiresAt !== null && entry.expiresAt <= Date.now()) {
      data.delete(key);
      return undefined;
    }
    return entry;
  };

  return {
    async get(key) {
      const entry = alive(key);
      return entry ? JSON.parse(entry.value) : null;
    },
    async set(key, value, ttlMs) {
      data.set(key, { value: JSON.stringify(value), expiresAt: Date.now() + ttlMs });
    },
    async setIfAbsent(key, value, ttlMs) {
      if (alive(key)) return false;
      await this.set(key, value, ttlMs);
      return true;
    },
    async del(key) {
      data.delete(key);
    },
    async expire(key, ttlMs) {
      const entry = alive(key);
      if (entry) entry.expiresAt = Date.now() + ttlMs;
    },
    async withLock(key, fn) {
      const previous = locks.get(key) ?? Promise.resolve();
      const run = previous.then(fn, fn);
      const tail = run.catch(() => {});
      locks.set(key, tail);
      try {
        return await run;
      } finally {
        if (locks.get(key) === tail) locks.delete(key);
      }
    },
    async publish(playerId, message) {
      const text = JSON.stringify(message);
      setImmediate(() => bus.emit(playerId, text));
    },
    async subscribe(playerId, handler) {
      const listener = text => handler(JSON.parse(text));
      bus.on(playerId, listener);
      return async () => bus.off(playerId, listener);
    },
  };
}

export function createRedisStore(url, prefix = '') {
  let commands = null;
  let subscriber = null;
  let idleTimer = null;
  const handlers = new Map();

  const connect = () => new Redis(url, { maxRetriesPerRequest: 3, enableReadyCheck: false });

  function client() {
    commands ??= connect().on('error', error => console.error('Redis error:', error.message));
    return commands;
  }

  function subscriptions() {
    if (!subscriber) {
      subscriber = connect().on('error', error => console.error('Redis subscriber error:', error.message));
      subscriber.on('message', (channel, text) => {
        handlers.get(channel)?.(JSON.parse(text));
      });
    }
    return subscriber;
  }

  // Drop the connections once this instance has no players left, so an idle instance holds nothing open
  function disconnectWhenIdle() {
    clearTimeout(idleTimer);
    if (handlers.size > 0) return;
    idleTimer = setTimeout(() => {
      if (handlers.size > 0) return;
      subscriber?.disconnect();
      commands?.disconnect();
      subscriber = null;
      commands = null;
    }, IDLE_DISCONNECT_MS);
  }

  const channel = playerId => `${prefix}mathwars:player:${playerId}`;
  const named = key => `${prefix}${key}`;

  return {
    async get(key) {
      const text = await client().get(named(key));
      return text === null ? null : JSON.parse(text);
    },
    async set(key, value, ttlMs) {
      await client().set(named(key), JSON.stringify(value), 'PX', ttlMs);
    },
    async setIfAbsent(key, value, ttlMs) {
      return (await client().set(named(key), JSON.stringify(value), 'PX', ttlMs, 'NX')) === 'OK';
    },
    async del(key) {
      await client().del(named(key));
    },
    async expire(key, ttlMs) {
      await client().pexpire(named(key), ttlMs);
    },
    async withLock(key, fn) {
      const lockKey = named(`lock:${key}`);
      const token = randomUUID();
      const deadline = Date.now() + LOCK_WAIT_MS;
      while ((await client().set(lockKey, token, 'PX', LOCK_TTL_MS, 'NX')) !== 'OK') {
        if (Date.now() > deadline) throw new Error(`Timed out waiting for ${lockKey}`);
        await sleep(10 + Math.random() * 20);
      }
      try {
        return await fn();
      } finally {
        await client().eval(RELEASE_LOCK, 1, lockKey, token);
      }
    },
    async publish(playerId, message) {
      await client().publish(channel(playerId), JSON.stringify(message));
    },
    async subscribe(playerId, handler) {
      clearTimeout(idleTimer);
      handlers.set(channel(playerId), handler);
      await subscriptions().subscribe(channel(playerId));
      return async () => {
        handlers.delete(channel(playerId));
        await subscriber?.unsubscribe(channel(playerId)).catch(() => {});
        disconnectWhenIdle();
      };
    },
  };
}

/** Upstash and other providers hand the connection string out under different names */
export function redisUrlFrom(env = process.env) {
  return env.REDIS_URL || env.KV_URL || env.UPSTASH_REDIS_URL || null;
}

let shared = null;

export function sharedStore(env = process.env) {
  if (!shared) {
    const url = redisUrlFrom(env);
    shared = url ? createRedisStore(url) : createMemoryStore();
  }
  return shared;
}
