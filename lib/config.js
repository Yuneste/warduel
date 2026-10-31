// Game settings. Every value can be overridden with an environment variable, for example
// GAME_DURATION_SECONDS=30. The page shows these numbers too (see scripts/build.mjs).

const DEFAULTS = {
  durationSeconds: 60,
  questionsPerGame: 20,
  winScore: 20,
  minNumber: 1,
  maxNumber: 20,
  multiplicationMin: 1,
  multiplicationMax: 10,
  divisionMin: 1,
  divisionMax: 10,
  challengeTimeoutSeconds: 10,
  publicChallengeType: '',
};

const envName = key => `GAME_${key.replace(/[A-Z]/g, c => `_${c}`).toUpperCase()}`;

export function loadConfig(env = process.env, overrides = {}) {
  const config = { ...DEFAULTS };
  for (const [key, fallback] of Object.entries(DEFAULTS)) {
    const raw = env[envName(key)];
    if (raw === undefined || raw === '') continue;
    config[key] = typeof fallback === 'number' ? Number(raw) : raw;
  }
  return { ...config, ...overrides };
}
