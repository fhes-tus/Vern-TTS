import { expect, it } from 'vitest';
import { paginate, pageForPosition } from './pagination';
it('splits long passages across real pages without dropping Unicode or changing passage anchors', async () => {
  const chunks = ['An opening sentence.', 'Café 🌿 世界 '.repeat(90), 'The final sentence.'];
  const pages = await paginate(
    chunks,
    100,
    150,
    16,
    (text) => Array.from(text).length * 8,
    () => false,
  );
  expect(pages.length).toBeGreaterThan(4);
  for (let position = 0; position < chunks.length; position++) {
    const parts = pages.flatMap((page) => page.parts).filter((part) => part.position === position);
    expect(parts.map((part) => part.text).join('')).toBe(chunks[position]);
    expect(parts.every((part) => chunks[position].slice(part.start, part.end) === part.text)).toBe(
      true,
    );
  }
  const page = pages.findIndex((page) => page.parts.some((part) => part.position === 1));
  expect(pageForPosition(pages, 1, page + 1)).toBe(page + 1);
  expect(
    await paginate(
      chunks,
      100,
      150,
      16,
      () => 5,
      () => true,
    ),
  ).toEqual([]);
});
