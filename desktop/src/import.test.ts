import { describe, expect, it } from 'vitest';
import { strToU8, zipSync } from 'fflate';
import { chunksFromText, parseBook } from './import';

describe('real book imports', () => {
  it('preserves Unicode text and bounds unpunctuated chunks', async () => {
    const book = await parseBook(
      'Cafe.txt',
      new TextEncoder().encode('Café.\n\n' + 'word '.repeat(400)),
    );
    expect(book.chunks.join(' ')).toContain('Café.');
    expect(book.chunks.every((chunk) => chunk.length <= 600)).toBe(true);
    expect(
      book.chunks
        .join(' ')
        .split(/\s/)
        .filter((w) => w === 'word'),
    ).toHaveLength(400);
  });
  it('reads EPUB in spine order rather than ZIP or filename order, without scripts', async () => {
    const bytes = zipSync({
      'META-INF/container.xml': strToU8(
        '<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>',
      ),
      'OPS/book.opf': strToU8(
        '<package xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:title>A real EPUB</dc:title><dc:creator>Author</dc:creator></metadata><manifest><item id="a" href="a.xhtml"/><item id="z" href="z.xhtml"/></manifest><spine><itemref idref="z"/><itemref idref="a"/></spine></package>',
      ),
      'OPS/a.xhtml': strToU8('<html><body><p>Second chapter.</p></body></html>'),
      'OPS/z.xhtml': strToU8(
        '<html><head><title>Hidden metadata</title></head><body><script>Never speak this.</script><p>First chapter.</p></body></html>',
      ),
    });
    const book = await parseBook('test.epub', bytes);
    expect(book.title).toBe('A real EPUB');
    expect(book.chunks).toEqual(['First chapter.', 'Second chapter.']);
    expect(book.author).toBe('Author');
  });
  it('rejects empty and unsupported documents honestly', async () => {
    expect(() => chunksFromText('   ')).toThrow('No readable text');
    await expect(parseBook('file.ppt', new Uint8Array())).rejects.toThrow('not supported yet');
    await expect(parseBook('bad.txt', new Uint8Array([255]))).rejects.toThrow();
  });
  it('rejects archive traversal', async () => {
    await expect(
      parseBook('unsafe.epub', zipSync({ '../escape.xml': strToU8('bad') })),
    ).rejects.toThrow('Unsafe');
  });
});
