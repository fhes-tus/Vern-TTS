import { describe, expect, it } from 'vitest';
import { indexAndroidText, type AndroidIndexInput } from './android-text-index';
const index = (text: string, options: Partial<AndroidIndexInput> = {}) =>
  indexAndroidText({
    text,
    pageCount: 0,
    sentenceIndexVersion: 2,
    expectedChunks: 0,
    ...options,
  });
describe('Android backup sentence anchors', () => {
  it('retains abbreviations, decimals and quoted attribution as in Android', () => {
    const result = index(
      'Dr. Watson arrived. “Stay here!” she said. The cost is 3.14. U.S.A. is an abbreviation.',
    );
    expect(result.chunks).toEqual([
      'Dr. Watson arrived.',
      '“Stay here!” she said.',
      'The cost is 3.14.',
      'U.S.A. is an abbreviation.',
    ]);
    expect(result.sourceToChunk).toEqual([0, 1, 2, 3]);
  });
  it('retains explicit page markers and the source index for each passage', () => {
    const result = index(
      '[[VERITAS_PAGE:1]]\nFirst page.\n[[VERITAS_PAGE:2]]\nSecond page.\nThird sentence.',
      { pageCount: 2, expectedChunks: 3 },
    );
    expect(result.chunks).toEqual(['First page.', 'Second page.', 'Third sentence.']);
    expect(result.pageStarts).toEqual([0, 1]);
    expect(result.sourcePages).toEqual([1, 2, 2]);
  });
  it('keeps TOC rows, numbered lists and literary speaker labels intact', () => {
    const result = index(
      'Contents\nCHAPTER I ..... 7\n1. An item\n2. Another\nChris. We will go.\nHope. I will follow.',
    );
    expect(result.chunks).toEqual([
      'Contents',
      'CHAPTER I ..... 7',
      '1. An item',
      '2. Another',
      'Chris. We will go.',
      'Hope. I will follow.',
    ]);
  });
  it('maps an Android passage to multiple bounded native UTF-8 chunks', () => {
    const text = '語'.repeat(3000) + '。',
      result = index(text);
    expect(result.sourceCount).toBe(2);
    expect(result.sourceToChunk).toEqual([0, 2]);
    expect(result.pageStarts).toEqual([0, 2]);
    expect(result.chunks.join('')).toBe(text);
    expect(result.chunks.every((chunk) => new TextEncoder().encode(chunk).length <= 4096)).toBe(
      true,
    );
  });
  it('preserves complete surrogate pairs at estimated page boundaries', () => {
    const text = 'Ж'.repeat(2201) + '😀'.repeat(1500),
      result = index(text);
    expect(result.chunks.join('')).toBe(text);
    expect(result.chunks.every((chunk) => !/[\ud800-\udfff]/u.test(chunk))).toBe(true);
  });
  it('refuses unknown indices and pathological input instead of guessing positions', () => {
    expect(() => index('A single sentence.', { expectedChunks: 99 })).toThrow(
      /positions were not guessed/,
    );
    expect(() => index('Text.', { sentenceIndexVersion: 3 })).toThrow(/unsupported/);
    expect(() => index('.'.repeat(5000))).toThrow(/punctuation/);
  });
});
