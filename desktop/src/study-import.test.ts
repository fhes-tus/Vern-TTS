import { describe, expect, it } from 'vitest';
import { parseCards, parseQuiz } from './study-import';
describe('pasted study replies', () => {
  it('accepts JSON wrappers, tables and multiline card markers', () => {
    expect(
      parseCards(
        'Here are cards:\n```json\n{"cards":[{"term":"Atom","definition":"A unit of matter"}]}\n```',
      ),
    ).toEqual([{ front: 'Atom', back: 'A unit of matter' }]);
    expect(parseCards('| Term | Definition |\n| --- | --- |\n| Atom | A unit |')).toHaveLength(1);
    expect(parseCards('Q: Atom?\nA: A unit\nof matter.\n\nQ: Recall?\nA: Retrieval')).toEqual([
      { front: 'Atom?', back: 'A unit of matter.' },
      { front: 'Recall?', back: 'Retrieval' },
    ]);
  });
  it('resolves full choice text before letter labels', () => {
    const q = parseQuiz(
      'Q: Element?\nA) Gold\nB) Water\nC) Carbon\nAnswer: Carbon\nExplanation: An element.',
    );
    expect(q[0].answer).toBe('Carbon');
    expect(q[0].explanation).toBe('An element.');
    expect(() => parseCards('Q: Element?\nA) Carbon\nB) Water\nAnswer: A')).toThrow('quiz');
    expect(() => parseQuiz('Q: Element?\nA) Carbon\nB) Water\nAnswer: Carbon dioxide')).toThrow(
      'No complete',
    );
  });
  it('accepts answerIndex and multiple questions and rejects ambiguous choices', () => {
    expect(
      parseQuiz(
        JSON.stringify({
          questions: [{ question: 'Element?', options: ['Carbon', 'Water'], answerIndex: 0 }],
        }),
      )[0].answer,
    ).toBe('Carbon');
    expect(
      parseQuiz(
        'Q: Element?\nA) Carbon\nB) Water\nAnswer: A\n\nQ: Compound?\nA) Carbon\nB) Water\nAnswer: B',
      ),
    ).toHaveLength(2);
    expect(() =>
      parseQuiz('[{"question":"Element?","options":["Carbon","carbon"],"answer":"Carbon"}]'),
    ).toThrow();
    expect(() => parseCards('x'.repeat(500001))).toThrow('500,000');
  });
});
