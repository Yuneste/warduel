// CI smoke test: two scripted players play one public game (the server runs with win score 1),
// so the winner's device token gets a leaderboard entry. Usage: node play-public-game.mjs <port> <token>
const [port = '8080', winnerToken] = process.argv.slice(2);
const url = `ws://localhost:${port}/game`;

const SUP = { '²': 2, '³': 3 };
function value(s) {
  s = s.trim();
  const t = s.split(' ');
  if (t.length === 3) return ({ '+': (a, b) => a + b, '-': (a, b) => a - b, '×': (a, b) => a * b, '÷': (a, b) => a / b })[t[1]](+t[0], +t[2]);
  if (s.startsWith('√')) return Math.sqrt(+s.slice(1));
  if (s.includes('/')) { const [p, q] = s.split('/'); return p / q; }
  if (SUP[s.at(-1)]) return Math.pow(+s.slice(0, -1), SUP[s.at(-1)]);
  return +s;
}
function answer(q) {
  if (q.format === 'TRUE_FALSE') { const [l, r] = q.questionText.split(' = '); return Math.round(value(l)) === +r ? 1 : 0; }
  if (q.format === 'CHOICE') return value(q.options[0]) > value(q.options[1]) ? 0 : 1;
  const text = q.questionText;
  if (text.includes('?')) {
    const [l, r] = text.split(' = '); const t = l.split(' '); const res = +r; const first = t[0] === '?'; const k = +(first ? t[2] : t[0]);
    return ({ '+': res - k, '-': first ? res + k : k - res, '×': res / k, '÷': first ? res * k : k / res })[t[1]];
  }
  return Math.round(value(text));
}

function player(name, token, play) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    const timer = setTimeout(() => reject(new Error(`${name}: no GAME_OVER within 30s`)), 30000);
    ws.onopen = () => ws.send(JSON.stringify({ type: 'JOIN_GAME', playerName: name, playerToken: token }));
    ws.onmessage = e => {
      const m = JSON.parse(e.data);
      if (m.type === 'QUESTION' && play) ws.send(JSON.stringify({ type: 'ANSWER', answer: answer(m) }));
      if (m.type === 'GAME_OVER') { clearTimeout(timer); ws.close(); resolve(m); }
    };
    ws.onerror = () => reject(new Error(`${name}: WebSocket error`));
  });
}

const winner = player('CI winner', winnerToken, true);
await new Promise(r => setTimeout(r, 300));
const loser = player('CI loser', crypto.randomUUID(), false);
const [w] = await Promise.all([winner, loser]);
if (!w.youWon) {
  console.error('Expected the scripted winner to win', w);
  process.exit(1);
}
console.log('Game played, winner recorded');
