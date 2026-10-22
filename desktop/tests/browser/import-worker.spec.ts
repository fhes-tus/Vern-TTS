import { expect, test } from '@playwright/test';
import { readFile } from 'node:fs/promises';
test('imports a real public-domain book through the browser worker', async ({ page }) => {
  await page.goto('/');
  const bytes = [...(await readFile('fixtures/alice-in-wonderland.txt'))];
  const book = await page.evaluate(async (data) => {
    const moduleUrl = '/src/import-client.ts';
    const { importInWorker } = await import(/* @vite-ignore */ moduleUrl);
    return importInWorker('Alice in Wonderland.txt', Uint8Array.from(data));
  }, bytes);
  expect(book.chunks.length).toBeGreaterThan(1000);
  expect(book.chunks.some((s: string) => s.includes('Alice'))).toBe(true);
});
test('extracts the repository’s real Sherlock PDF through nested PDF workers', async ({ page }) => {
  test.setTimeout(180_000);
  await page.goto('/');
  const bytes = [...(await readFile('../sherlock.pdf'))];
  const book = await page.evaluate(async (data) => {
    const moduleUrl = '/src/import-client.ts';
    const { importInWorker } = await import(/* @vite-ignore */ moduleUrl);
    return importInWorker('Sherlock.pdf', Uint8Array.from(data));
  }, bytes);
  expect(book.format).toBe('PDF');
  expect(book.chunks.length).toBeGreaterThan(1000);
  expect(book.chunks.some((s: string) => s.includes('Holmes'))).toBe(true);
});
test('imports the real Gutenberg EPUB metadata and spine text', async ({ page }) => {
  await page.goto('/');
  const bytes = [...(await readFile('fixtures/alice-in-wonderland.epub'))];
  const book = await page.evaluate(async (data) => {
    const moduleUrl = '/src/import-client.ts';
    const { importInWorker } = await import(/* @vite-ignore */ moduleUrl);
    return importInWorker('Alice.epub', Uint8Array.from(data));
  }, bytes);
  expect(book.format).toBe('EPUB');
  expect(book.title).toContain('Alice');
  expect(book.author).toContain('Carroll');
  expect(book.chunks.length).toBeGreaterThan(1000);
});
