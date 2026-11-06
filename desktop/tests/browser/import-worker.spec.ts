import { expect, test, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';

async function importFixture(page: Page, name: string, filename: string) {
  const bytes = await readFile(filename);
  // Transfer the real file as bytes rather than millions of JSON numbers over
  // the browser control protocol. The same import worker still parses it.
  await page.route('**/__worker_fixture__', (route) =>
    route.fulfill({ body: bytes, contentType: 'application/octet-stream' }),
  );
  return page.evaluate(async (fileName) => {
    const response = await fetch('/__worker_fixture__');
    if (!response.ok) throw new Error('Could not load the import fixture.');
    const data = new Uint8Array(await response.arrayBuffer());
    const moduleUrl = '/src/import-client.ts';
    const { importInWorker } = await import(/* @vite-ignore */ moduleUrl);
    return importInWorker(fileName, data);
  }, name);
}

test('cover rendering handles embedded PDF images inside a worker without DOM canvases', async ({
  page,
}) => {
  const pixels = 'FF000000FF000000FFFFFFFF>';
  const drawing = 'q 300 0 0 450 50 50 cm /Im0 Do Q';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>',
    `<< /Length ${drawing.length} >>\nstream\n${drawing}\nendstream`,
    `<< /Type /XObject /Subtype /Image /Width 2 /Height 2 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /ASCIIHexDecode /Length ${pixels.length} >>\nstream\n${pixels}\nendstream`,
  ];
  let source = '%PDF-1.4\n';
  const offsets = [0];
  objects.forEach((object, n) => {
    offsets.push(source.length);
    source += `${n + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = source.length;
  source += `xref\n0 6\n0000000000 65535 f \n${offsets
    .slice(1)
    .map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`)
    .join('')}trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  await page.goto('/');
  const result = await page.evaluate(
    async (bytes) => {
      const path = '/src/import-client.ts';
      const { extractCoverInWorker } = await import(/* @vite-ignore */ path);
      return extractCoverInWorker('image-cover.pdf', Uint8Array.from(bytes));
    },
    Array.from(new TextEncoder().encode(source)),
  );
  expect(result.cover).toMatch(/^data:image\/jpeg;base64,/);
});
test('imports a real public-domain book through the browser worker', async ({ page }) => {
  await page.goto('/');
  const book = await importFixture(
    page,
    'Alice in Wonderland.txt',
    'fixtures/alice-in-wonderland.txt',
  );
  expect(book.chunks.length).toBeGreaterThan(1000);
  expect(book.chunks.some((s: string) => s.includes('Alice'))).toBe(true);
});
test('extracts the repository’s real Sherlock PDF through nested PDF workers', async ({ page }) => {
  test.setTimeout(180_000);
  await page.goto('/');
  const book = await importFixture(page, 'Sherlock.pdf', '../sherlock.pdf');
  expect(book.format).toBe('PDF');
  expect(book.chunks.length).toBeGreaterThan(1000);
  expect(book.chunks.some((s: string) => s.includes('Holmes'))).toBe(true);
  expect(book.cover).toMatch(/^data:image\/jpeg;base64,/);
});
test('imports the real Gutenberg EPUB metadata and spine text', async ({ page }) => {
  await page.goto('/');
  const book = await importFixture(page, 'Alice.epub', 'fixtures/alice-in-wonderland.epub');
  expect(book.format).toBe('EPUB');
  expect(book.title).toContain('Alice');
  expect(book.author).toContain('Carroll');
  expect(book.chunks.length).toBeGreaterThan(1000);
  expect(book.cover).toMatch(/^data:image\/jpeg;base64,/);
});
