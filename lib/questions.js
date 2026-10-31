// Generates the math problems. Every challenge type is built on the same arithmetic core.
// Problems get harder through a game: the first third uses the lower part of the configured number
// ranges, the last third the full range. No problem repeats within one list.
//
// Every answer travels as an int: the number itself for NUMBER questions, 1 (true) / 0 (false) for
// TRUE_FALSE, and the index of the chosen option for CHOICE.

import { randomInt } from 'node:crypto';

export const CHALLENGE_TYPES = ['CLASSIC', 'TRUE_FALSE', 'MISSING_NUMBER', 'COMPARE'];

const TIERS = 3;
const MAX_ATTEMPTS = 30;
const OPERATIONS = ['ADD', 'SUBTRACT', 'MULTIPLY', 'DIVIDE'];
const SYMBOLS = { ADD: '+', SUBTRACT: '-', MULTIPLY: '×', DIVIDE: '÷' };
// Squares and cubes only: the chalk font has ² and ³ but no higher superscripts
const SUPERSCRIPT = { 2: '²', 3: '³' };

/** @returns a random int from [min, max) */
const between = (min, max) => randomInt(min, max);
const coin = () => randomInt(2) === 1;

export const randomChallengeType = () => CHALLENGE_TYPES[randomInt(CHALLENGE_TYPES.length)];

/** @returns the type, or null for unknown or missing values ("random") */
export function parseChallengeType(value) {
  if (typeof value !== 'string') return null;
  const type = value.trim().toUpperCase();
  return CHALLENGE_TYPES.includes(type) ? type : null;
}

/** The player's answer in words, for the post-game answer list ("42", "True", "3/4") */
export function formatAnswer(question, answer) {
  switch (question.format) {
    case 'TRUE_FALSE': return answer === 1 ? 'True' : answer === 0 ? 'False' : '?';
    case 'CHOICE': return question.options[answer] ?? '?';
    default: return String(answer);
  }
}

export function createQuestionGenerator(config) {
  /** A number from [min, max] that grows with the tier. After the warm-up, trivial 1s are skipped. */
  function inRange(min, max, tier) {
    const lo = tier > 0 && max >= 2 ? Math.max(min, 2) : min;
    let hi = min + Math.ceil(((max - min) * (tier + 1)) / TIERS);
    hi = Math.max(lo, Math.min(max, hi));
    return between(lo, hi + 1);
  }

  /** One arithmetic fact, e.g. 6 × 7 = 42 */
  function fact(op, tier) {
    const makeFact = (a, b, result) => ({ a, b, op, result, text: `${a} ${SYMBOLS[op]} ${b}` });
    switch (op) {
      case 'ADD': {
        const a = inRange(config.minNumber, config.maxNumber, tier);
        const b = inRange(config.minNumber, config.maxNumber, tier);
        return makeFact(a, b, a + b);
      }
      case 'SUBTRACT': {
        const x = inRange(config.minNumber, config.maxNumber, tier);
        const y = inRange(config.minNumber, config.maxNumber, tier);
        return makeFact(Math.max(x, y), Math.min(x, y), Math.abs(x - y));
      }
      case 'MULTIPLY': {
        const a = inRange(config.multiplicationMin, config.multiplicationMax, tier);
        const b = inRange(config.multiplicationMin, config.multiplicationMax, tier);
        return makeFact(a, b, a * b);
      }
      default: {
        // Pick the result first and work backwards, so division always comes out even
        const answer = inRange(config.divisionMin, config.divisionMax, tier);
        const divisor = inRange(config.divisionMin, config.divisionMax, tier);
        return makeFact(answer * divisor, divisor, answer);
      }
    }
  }

  const randomOperation = () => OPERATIONS[randomInt(OPERATIONS.length)];

  const classic = tier => {
    const f = fact(randomOperation(), tier);
    return { questionText: f.text, correctAnswer: f.result, operationType: f.op, format: 'NUMBER', options: [] };
  };

  /** A wrong result a player might believe: off by one or two, off by ten, or digits swapped */
  function plausibleWrong(result) {
    const candidates = [result + 1, result + 2, result - 1, result - 2, result + 10];
    if (result >= 10) {
      candidates.push(result - 10);
      const digits = String(result);
      const swapped = [...digits].reverse().join('');
      if (swapped !== digits && !swapped.startsWith('0')) candidates.push(Number(swapped));
    }
    const usable = candidates.filter(c => c >= 0 && c !== result);
    return usable[randomInt(usable.length)];
  }

  /** "7 × 8 = 54": the shown result is right about half of the time, otherwise close but wrong */
  const trueFalse = tier => {
    const f = fact(randomOperation(), tier);
    const isTrue = coin();
    const shown = isTrue ? f.result : plausibleWrong(f.result);
    return {
      questionText: `${f.text} = ${shown}`,
      correctAnswer: isTrue ? 1 : 0,
      operationType: f.op,
      format: 'TRUE_FALSE',
      options: ['True', 'False'],
    };
  };

  /** "? × 8 = 56": one operand is hidden */
  const missingNumber = tier => {
    const f = fact(randomOperation(), tier);
    const hideFirst = coin();
    const text = hideFirst
      ? `? ${SYMBOLS[f.op]} ${f.b} = ${f.result}`
      : `${f.a} ${SYMBOLS[f.op]} ? = ${f.result}`;
    return { questionText: text, correctAnswer: hideFirst ? f.a : f.b, operationType: f.op, format: 'NUMBER', options: [] };
  };

  // Compare sides are { text, value }
  function exprVsNumber(tier) {
    const f = fact(coin() ? 'MULTIPLY' : 'ADD', tier);
    const delta = between(1, 4) * (coin() ? 1 : -1);
    const other = Math.max(0, f.result + delta);
    return [{ text: f.text, value: f.result }, { text: String(other), value: other }];
  }

  function exprVsExpr(tier) {
    const a = fact(randomOperation(), tier);
    const b = fact(randomOperation(), tier);
    return [{ text: a.text, value: a.result }, { text: b.text, value: b.result }];
  }

  function fractionVsDecimal() {
    const denominators = [2, 3, 4, 5, 8, 10];
    const q = denominators[randomInt(denominators.length)];
    const p = between(1, q);
    const fraction = p / q;
    const offset = (between(3, 13) / 100) * (coin() ? 1 : -1);
    const decimal = Math.round(Math.min(0.99, Math.max(0.01, fraction + offset)) * 100) / 100;
    return [{ text: `${p}/${q}`, value: fraction }, { text: String(decimal), value: decimal }];
  }

  function powerVsPower() {
    const side = () => {
      const base = between(2, 10);
      const exponent = between(2, 4);
      return { text: `${base}${SUPERSCRIPT[exponent]}`, value: base ** exponent };
    };
    return [side(), side()];
  }

  function rootVsNumber() {
    const n = between(4, 13);
    const other = n + (coin() ? 1 : -1);
    return [{ text: `√${n * n}`, value: n }, { text: String(other), value: other }];
  }

  const hardPair = () => [fractionVsDecimal, powerVsPower, rootVsNumber][randomInt(3)]();

  /** "3/4 or 0.7": pick the bigger side. The warm-up compares a sum or product with a number. */
  function compare(tier) {
    let pair;
    do {
      pair = tier === 0 ? exprVsNumber(tier) : tier === 1 ? exprVsExpr(tier) : hardPair();
    } while (Math.abs(pair[0].value - pair[1].value) < 1e-9);

    const [left, right] = coin() ? pair : [pair[1], pair[0]]; // either side may be the bigger one
    return {
      questionText: `${left.text} or ${right.text}`,
      correctAnswer: left.value > right.value ? 0 : 1,
      operationType: null,
      format: 'CHOICE',
      options: [left.text, right.text],
    };
  }

  const makers = { CLASSIC: classic, TRUE_FALSE: trueFalse, MISSING_NUMBER: missingNumber, COMPARE: compare };

  return function generateQuestions(type, count) {
    const make = makers[type];
    const questions = [];
    const seen = new Set();
    for (let i = 0; i < count; i++) {
      const tier = Math.min(TIERS - 1, Math.floor((i * TIERS) / Math.max(count, 1)));
      let question = make(tier);
      // Retry duplicates; tiny configured ranges may make a repeat unavoidable
      for (let attempt = 0; attempt < MAX_ATTEMPTS && seen.has(question.questionText); attempt++) {
        question = make(tier);
      }
      seen.add(question.questionText);
      questions.push(question);
    }
    return questions;
  };
}
