// Unit tests for the question generator (checked against the independent solver) and nicknames.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { loadConfig } from '../lib/config.js';
import { MAX_NAME_LENGTH, sanitizeName } from '../lib/names.js';
import { CHALLENGE_TYPES, createQuestionGenerator, formatAnswer, parseChallengeType } from '../lib/questions.js';
import { solve, value } from './support.mjs';

const GAMES = 200;
const generator = (overrides = {}) => createQuestionGenerator(loadConfig({}, overrides));

function manyQuestions(type) {
  const generate = generator();
  return Array.from({ length: GAMES }, () => generate(type, 20)).flat();
}

describe('questions', () => {
  for (const type of CHALLENGE_TYPES) {
    it(`${type}: every answer key is right`, () => {
      for (const q of manyQuestions(type)) {
        assert.equal(q.correctAnswer, solve(q.format, q.questionText, q.options), q.questionText);
      }
    });

    it(`${type}: no problem repeats within a game`, () => {
      const generate = generator();
      for (let game = 0; game < 50; game++) {
        const texts = generate(type, 20).map(q => q.questionText);
        assert.equal(new Set(texts).size, texts.length, texts.join(', '));
      }
    });
  }

  it('generates the requested number of questions', () => {
    const generate = generator();
    assert.equal(generate('CLASSIC', 20).length, 20);
    assert.equal(generate('CLASSIC', 0).length, 0);
  });

  it('uses all four operations', () => {
    const seen = new Set(manyQuestions('CLASSIC').map(q => q.operationType));
    assert.deepEqual([...seen].sort(), ['ADD', 'DIVIDE', 'MULTIPLY', 'SUBTRACT']);
  });

  it('never goes negative in subtraction, and division always comes out even', () => {
    for (const q of manyQuestions('CLASSIC')) {
      const [a, , b] = q.questionText.split(' ').map(Number);
      if (q.operationType === 'SUBTRACT') assert.ok(q.correctAnswer >= 0, q.questionText);
      if (q.operationType === 'DIVIDE') assert.equal(a % b, 0, q.questionText);
    }
  });

  it('respects the configured ranges', () => {
    const generate = generator({ minNumber: 5, maxNumber: 6, multiplicationMin: 2, multiplicationMax: 3 });
    for (const q of generate('CLASSIC', 500)) {
      const [a, , b] = q.questionText.split(' ').map(Number);
      if (q.operationType === 'ADD' || q.operationType === 'SUBTRACT') {
        assert.ok([a, b].every(n => n >= 5 && n <= 6), q.questionText);
      }
      if (q.operationType === 'MULTIPLY') assert.ok([a, b].every(n => n >= 2 && n <= 3), q.questionText);
    }
  });

  it('keeps true-or-false balanced and wrong results close', () => {
    const questions = manyQuestions('TRUE_FALSE');
    const trueCount = questions.filter(q => q.correctAnswer === 1).length;
    assert.ok(trueCount >= questions.length * 0.35 && trueCount <= questions.length * 0.65);

    for (const q of questions) {
      assert.deepEqual(q.options, ['True', 'False']);
      if (q.correctAnswer === 1) continue;
      const [left, right] = q.questionText.split(' = ');
      const real = Math.round(value(left));
      const swapped = [...String(real)].reverse().join('') === right;
      assert.ok(Math.abs(real - Number(right)) <= 10 || swapped, q.questionText);
    }
  });

  it('hides exactly one positive operand in missing-number', () => {
    for (const q of manyQuestions('MISSING_NUMBER')) {
      assert.equal(q.questionText.split('?').length - 1, 1);
      assert.ok(q.correctAnswer > 0, q.questionText);
    }
  });

  it('never offers equal sides in compare, and both sides can win', () => {
    const questions = manyQuestions('COMPARE');
    for (const q of questions) {
      assert.equal(q.format, 'CHOICE');
      assert.notEqual(value(q.options[0]), value(q.options[1]), q.questionText);
    }
    const answers = new Set(questions.map(q => q.correctAnswer));
    assert.deepEqual([...answers].sort(), [0, 1]);
    const texts = questions.map(q => q.questionText);
    // the hard third brings fractions, powers and roots
    assert.ok(texts.some(t => t.includes('/')));
    assert.ok(texts.some(t => t.includes('²') || t.includes('³')));
    assert.ok(!texts.some(t => t.includes('⁴') || t.includes('⁵')));
    assert.ok(texts.some(t => t.includes('√')));
  });

  it('gets harder through the game', () => {
    const generate = generator({ minNumber: 1, maxNumber: 30 });
    const operands = q => {
      const t = q.questionText.split(' ');
      return t[1] === '+' || t[1] === '-' ? [Number(t[0]), Number(t[2])] : [];
    };
    const early = [];
    const late = [];
    for (let game = 0; game < GAMES; game++) {
      const questions = generate('CLASSIC', 21);
      questions.slice(0, 7).forEach(q => early.push(...operands(q)));
      questions.slice(14).forEach(q => late.push(...operands(q)));
    }
    // First third: lowest third of the range (1..11); last third: the whole range
    assert.ok(early.every(n => n >= 1 && n <= 11));
    assert.ok(late.some(n => n > 20));
  });

  it('describes answers in words', () => {
    const tf = { format: 'TRUE_FALSE', options: ['True', 'False'] };
    const choice = { format: 'CHOICE', options: ['3/4', '0.7'] };
    assert.equal(formatAnswer(tf, 1), 'True');
    assert.equal(formatAnswer(tf, 0), 'False');
    assert.equal(formatAnswer(choice, 1), '0.7');
    assert.equal(formatAnswer(choice, 7), '?');
    assert.equal(formatAnswer({ format: 'NUMBER', options: [] }, -3), '-3');
  });

  it('parses challenge types, with unknown values meaning random', () => {
    assert.equal(parseChallengeType(' compare '), 'COMPARE');
    assert.equal(parseChallengeType('nope'), null);
    assert.equal(parseChallengeType(null), null);
  });
});

describe('nicknames', () => {
  const cases = [
    ['Yuko', 'Yuko'],
    ['  Kai   the  Great ', 'Kai the Great'],
    ['Zoë-Marie', 'Zoë-Marie'],
    ['李小龙', '李小龙'],
    ['<b>bold</b>', 'bboldb'],
    ['a\tb\n\nc', 'a b c'],
  ];
  for (const [raw, expected] of cases) {
    it(`cleans ${JSON.stringify(raw)}`, () => assert.equal(sanitizeName(raw), expected));
  }

  it('turns empty or unusable names into null', () => {
    assert.equal(sanitizeName(null), null);
    assert.equal(sanitizeName('   '), null);
    assert.equal(sanitizeName('<>{}'), null);
  });

  it('cuts long names to sixteen characters', () => {
    assert.equal(sanitizeName('abcdefghijklmnopqrstuvwxyz').length, MAX_NAME_LENGTH);
    assert.equal(sanitizeName('😀'.repeat(3) + 'x'.repeat(20)), 'x'.repeat(16));
    assert.equal(sanitizeName('李'.repeat(20)), '李'.repeat(16));
  });
});
