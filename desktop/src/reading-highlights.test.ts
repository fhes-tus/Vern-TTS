import { describe, expect, it } from 'vitest';
import { highlightsAtPositions } from './reading-highlights';
import type { ReadingHighlight } from './types';

function mark(
  id: string,
  position: number,
  endPosition: number,
  updatedAt: number,
): ReadingHighlight {
  return {
    id,
    bookId: 'reading',
    position,
    endPosition,
    updatedAt,
    createdAt: 0,
    color: 'yellow',
    note: '',
  };
}

describe('reading highlight overlap', () => {
  it('returns to an earlier mark after the newest overlapping range ends', () => {
    const items = [mark('a', 0, 20, 1), mark('b', 1, 1, 5), mark('c', 2, 4, 3)];
    expect(
      [...highlightsAtPositions(items, [5, 1, 0, 3, 2, 4, 21, 5])].map(([p, item]) => [p, item.id]),
    ).toEqual([
      [0, 'a'],
      [1, 'b'],
      [2, 'c'],
      [3, 'c'],
      [4, 'c'],
      [5, 'a'],
    ]);
  });

  it('matches an independent overlap oracle across sparse positions and tied dates', () => {
    const items = Array.from({ length: 500 }, (_, i) => {
      const first = (i * 37) % 120;
      return mark(String(i).padStart(4, '0'), first, first + (i % 17), i % 23);
    });
    const positions = Array.from({ length: 80 }, (_, i) => i * 2);
    const actual = highlightsAtPositions(items, positions);
    for (const position of positions) {
      const expected = items
        .filter((item) => item.position <= position && item.endPosition >= position)
        .sort((a, b) => b.updatedAt - a.updatedAt || b.id.localeCompare(a.id))[0];
      expect(actual.get(position)).toEqual(expected);
    }
  });
});
