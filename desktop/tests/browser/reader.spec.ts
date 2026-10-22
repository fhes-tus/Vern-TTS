import { expect, test, type Page } from '@playwright/test';
import { zipSync, strToU8 } from 'fflate';
import { readFile } from 'node:fs/promises';
import { defaultSettings, type Book, type Settings } from '../../src/types';
import { parseBook } from '../../src/import';

function fixtureBook(): Book {
  return {
    id: 'reader-fixture',
    title: 'Reader interaction fixture',
    author: 'Vern',
    format: 'TXT',
    position: 0,
    favorite: false,
    updatedAt: 1,
    chunks: Array.from(
      { length: 100 },
      (_, n) =>
        `Passage ${n + 1}. Alice watched the sunlight move across the quiet garden. ${'These words remain selectable while a page turns. '.repeat(5)}`,
    ),
  };
}
type Original = { name: string; bytes: number[]; starts: number[]; format: string };
async function openReader(page: Page, book = fixtureBook(), original: Original | null = null) {
  await page.addInitScript(
    ({ book, prefs, original }) => {
      const state = {
        book,
        prefs,
        notes: [] as { id: string; bookId: string; position: number; text: string; kind: string }[],
        fullscreen: false,
        sourcePage: 0,
      };
      Reflect.set(window, 'isTauri', true);
      Reflect.set(window, '__readerState', state);
      Reflect.set(window, '__TAURI_INTERNALS__', {
        metadata: { currentWindow: { label: 'main' }, currentWebview: { label: 'main' } },
        invoke: async (command: string, args: Record<string, unknown> = {}) => {
          if (command === 'list_books') return [structuredClone(state.book)];
          if (command === 'get_settings') return structuredClone(state.prefs);
          if (command === 'save_settings') {
            state.prefs = structuredClone(args.settings as Settings);
            return;
          }
          if (command === 'list_voices') return [];
          if (
            [
              'list_all_annotations',
              'list_flashcards',
              'list_personal_notes',
              'list_notebooks',
            ].includes(command)
          )
            return [];
          if (command === 'save_progress') {
            state.book.position = args.position as number;
            return;
          }
          if (command === 'list_annotations') return structuredClone(state.notes);
          if (command === 'save_annotation') {
            state.notes.push(args.annotation as (typeof state.notes)[number]);
            return;
          }
          if (command === 'get_source_info')
            return original
              ? {
                  name: original.name,
                  format: original.format,
                  pageStarts: original.starts,
                  page: state.sourcePage,
                  textAvailable: true,
                }
              : null;
          if (command === 'get_source_file')
            return original ? { name: original.name, bytes: original.bytes } : null;
          if (command === 'save_source_index') {
            if (original) original.starts = args.pages as number[];
            return;
          }
          if (command === 'save_source_page') {
            state.sourcePage = args.page as number;
            return;
          }
          if (command === 'plugin:window|set_fullscreen') {
            state.fullscreen = args.value as boolean;
            return;
          }
          if (command === 'cancel_speech') return;
          throw new Error(`Unexpected reader command: ${command}`);
        },
      });
    },
    { book, prefs: { ...defaultSettings, lastBook: book.id }, original },
  );
  await page.goto('/');
  await page.getByRole('button', { name: 'Continue reading', exact: true }).first().click();
  await expect(page.locator('.flow-page-body p').first()).toBeVisible();
  await expect(page.getByText('Laying out pages…')).not.toBeVisible();
}

test('single and two-page layouts turn by controls, keyboard, wheel and drag without losing text selection', async ({
  page,
}) => {
  await openReader(page);
  const spread = page.locator('.reader-spread');
  await expect(spread).toHaveAttribute('data-layout', 'single');
  await expect(spread).toHaveAttribute('data-page-start', '1');
  await page.getByRole('button', { name: 'Next page', exact: true }).click();
  await expect(spread).toHaveAttribute('data-page-start', '2');
  await page.locator('.reader-viewport').focus();
  await page.keyboard.press('PageDown');
  await expect(spread).toHaveAttribute('data-page-start', '3');
  await page.keyboard.press('PageUp');
  await expect(spread).toHaveAttribute('data-page-start', '2');
  await page.getByRole('combobox', { name: 'Page layout' }).selectOption('double');
  await expect(spread).toHaveAttribute('data-layout', 'double');
  await expect(page.locator('.flow-paper')).toHaveCount(2);
  await expect(page.getByText('Laying out pages…')).not.toBeVisible();
  await page.locator('.reader-viewport').focus();
  await page.keyboard.press('Home');
  await page.getByRole('button', { name: 'Next page', exact: true }).click();
  await expect(spread).toHaveAttribute('data-page-start', '3');
  await page.locator('.reader-viewport').dispatchEvent('wheel', { deltaX: 100, deltaY: 0 });
  await expect(spread).toHaveAttribute('data-page-start', '5');
  const box = await page.locator('.reader-viewport').boundingBox();
  if (!box) throw new Error('No reader viewport');
  await page.keyboard.down('Alt');
  await page.mouse.move(box.x + box.width * 0.65, box.y + box.height * 0.5);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.3, box.y + box.height * 0.5, { steps: 8 });
  await page.mouse.up();
  await page.keyboard.up('Alt');
  await expect(spread).toHaveAttribute('data-page-start', '7');
  const text = page.locator('.flow-page-body p').first();
  await text.dblclick({ position: { x: 35, y: 15 } });
  await expect(page.getByRole('toolbar', { name: 'Selected text actions' })).toBeVisible();
  const selected = await page.evaluate(() => window.getSelection()?.toString());
  expect(selected?.trim().length).toBeGreaterThan(0);
  await page.getByRole('button', { name: 'Add to note', exact: true }).click();
  await expect(page.getByLabel('Leave a thought')).toHaveValue(selected!.trim());
  await page.getByRole('button', { name: 'Save note', exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__readerState').notes.length))
    .toBe(1);
});

test('keyboard text selection is preserved and fullscreen fills the viewport with app chrome hidden', async ({
  page,
}) => {
  await openReader(page);
  await page.locator('.reader-viewport').focus();
  await page.keyboard.press('Shift+ArrowRight');
  await expect(page.getByRole('toolbar', { name: 'Selected text actions' })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Fullscreen reading', exact: true }).click();
  await expect(page.locator('.app-shell')).toHaveClass(/reader-focus/);
  await expect(page.locator('.sidebar')).not.toBeVisible();
  await expect(page.locator('.playback-bar')).not.toBeVisible();
  await expect(page.locator('.notebook')).not.toBeVisible();
  const bounds = await page.locator('.reader-surface').boundingBox();
  expect(bounds?.width).toBe(1360);
  expect(bounds?.height).toBe(900);
  await expect(page.locator('.reader-surface')).not.toHaveClass(/controls-visible/);
  await page.screenshot({ path: '.artifacts/reader-fullscreen.png' });
  await page.locator('.reader-viewport').focus();
  await page.keyboard.press('Escape');
  await expect(page.locator('.sidebar')).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__readerState').fullscreen))
    .toBe(false);
});

function smallPdf() {
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Resources << /Font << /F1 7 0 R >> >> /Contents 4 0 R >>',
    '',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Resources << /Font << /F1 7 0 R >> >> /Contents 6 0 R >>',
    '',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  for (const [index, text] of [
    [3, 'Original first page words.'],
    [5, 'Original second page words.'],
  ] as const) {
    const stream = `BT /F1 16 Tf 35 540 Td (${text}) Tj ET`;
    objects[index] = `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`;
  }
  let pdf = '%PDF-1.4\n';
  const offsets = [0];
  objects.forEach((object, index) => {
    offsets.push(pdf.length);
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets.slice(1)) pdf += `${String(offset).padStart(10, '0')} 00000 n \n`;
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return Array.from(new TextEncoder().encode(pdf));
}

test('original PDF renders real canvases and selectable text, turns pages, zooms and shows a two-page spread', async ({
  page,
}) => {
  const book = {
    ...fixtureBook(),
    format: 'PDF',
    chunks: ['Original first page words.', 'Original second page words.'],
  };
  await openReader(page, book, {
    name: 'two-pages.pdf',
    format: 'PDF',
    bytes: smallPdf(),
    starts: [0, 1],
  });
  await page.getByRole('combobox', { name: 'Reader view' }).selectOption('original');
  await expect(page.locator('.pdf-page')).toHaveAttribute('data-rendered-page', '0', {
    timeout: 15000,
  });
  await expect(page.locator('.textLayer')).toContainText('Original first page words.');
  await page.locator('.reader-viewport').focus();
  await page.keyboard.press('Shift+ArrowRight');
  await expect(page.getByRole('toolbar', { name: 'Selected text actions' })).toBeVisible();
  expect(await page.evaluate(() => window.getSelection()?.toString())).toBe('O');
  await page.getByRole('button', { name: 'Clear selection', exact: true }).click();
  expect(
    await page
      .locator('.pdf-page canvas')
      .evaluate((canvas: HTMLCanvasElement) => canvas.width * canvas.height),
  ).toBeGreaterThan(10000);
  await page.getByRole('button', { name: 'Next page', exact: true }).click();
  await expect(page.locator('.pdf-page')).toHaveAttribute('data-rendered-page', '1');
  await expect(page.locator('.textLayer')).toContainText('Original second page words.');
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__readerState').sourcePage))
    .toBe(1);
  await page.getByRole('combobox', { name: 'Page layout' }).selectOption('double');
  await expect(page.locator('.pdf-page')).toHaveCount(2);
  await expect(page.locator('.pdf-page').nth(0)).toHaveAttribute('data-rendered-page', '0');
  await expect(page.locator('.pdf-page').nth(1)).toHaveAttribute('data-rendered-page', '1');
  const before = await page.locator('.pdf-page').first().boundingBox();
  await page.getByRole('combobox', { name: 'Document zoom' }).selectOption('1.5');
  await expect
    .poll(async () => (await page.locator('.pdf-page').first().boundingBox())?.width ?? 0)
    .toBeGreaterThan(before!.width * 1.4);
  await page.screenshot({ path: '.artifacts/reader-original-pdf.png' });
});

test('source DOCX renders its original paragraphs and explicit page breaks through the document worker', async ({
  page,
}) => {
  const docx = zipSync({
    'word/document.xml': strToU8(
      '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Original Word heading.</w:t></w:r></w:p><w:p><w:r><w:br w:type="page"/></w:r></w:p><w:p><w:r><w:t>Original second Word page.</w:t></w:r></w:p></w:body></w:document>',
    ),
  });
  const parsed = await parseBook('source.docx', docx);
  await openReader(
    page,
    { ...fixtureBook(), ...parsed },
    {
      name: 'source.docx',
      format: 'DOCX',
      bytes: Array.from(docx),
      starts: parsed.sourcePageStarts ?? [0],
    },
  );
  await page.getByRole('combobox', { name: 'Reader view' }).selectOption('original');
  await expect(page.locator('.source-sheet')).toContainText('Original Word heading.');
  await page.getByRole('button', { name: 'Next page', exact: true }).click();
  await expect(page.locator('.source-sheet')).toContainText('Original second Word page.');
});

test('real EPUB source sections render in original view', async ({ page }) => {
  const bytes = new Uint8Array(await readFile('fixtures/alice-in-wonderland.epub'));
  const parsed = await parseBook('Alice.epub', bytes);
  await openReader(
    page,
    { ...fixtureBook(), ...parsed },
    {
      name: 'Alice.epub',
      format: 'EPUB',
      bytes: Array.from(bytes),
      starts: parsed.sourcePageStarts ?? [0],
    },
  );
  await page.getByRole('combobox', { name: 'Reader view' }).selectOption('original');
  await expect(page.locator('.source-sheet')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Next page', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Next page', exact: true }).click();
  await expect(page.locator('.reader-spread')).toHaveAttribute('data-page-start', '2');
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('PPTX original view follows the presentation order and supports two source slides', async ({
  page,
}) => {
  const slide = (text: string) =>
    `<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:spPr><a:xfrm><a:off x="300000" y="300000"/><a:ext cx="7000000" cy="1000000"/></a:xfrm></p:spPr><p:txBody><a:p><a:r><a:t>${text}</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>`;
  const bytes = zipSync({
    'ppt/presentation.xml': strToU8(
      '<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId id="2" r:id="two"/><p:sldId id="1" r:id="one"/></p:sldIdLst></p:presentation>',
    ),
    'ppt/_rels/presentation.xml.rels': strToU8(
      '<Relationships><Relationship Id="one" Target="slides/slide1.xml"/><Relationship Id="two" Target="slides/slide2.xml"/></Relationships>',
    ),
    'ppt/slides/slide1.xml': strToU8(slide('The final slide.')),
    'ppt/slides/slide2.xml': strToU8(slide('The opening slide.')),
  });
  const parsed = await parseBook('deck.pptx', bytes);
  await openReader(
    page,
    { ...fixtureBook(), ...parsed },
    {
      name: 'deck.pptx',
      format: 'PPTX',
      bytes: Array.from(bytes),
      starts: parsed.sourcePageStarts ?? [0, 1],
    },
  );
  await page.getByRole('combobox', { name: 'Reader view' }).selectOption('original');
  await expect(page.locator('.source-sheet')).toContainText('The opening slide.');
  await page.getByRole('combobox', { name: 'Page layout' }).selectOption('double');
  await expect(page.locator('.source-sheet')).toHaveCount(2);
  await expect(page.locator('.source-sheet').nth(1)).toContainText('The final slide.');
});

test('reduced motion disables reader page animation', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await openReader(page);
  await page.getByRole('button', { name: 'Next page', exact: true }).click();
  await expect
    .poll(() =>
      page.locator('.reader-spread').evaluate((element) => element.getAnimations().length),
    )
    .toBe(0);
});

test('touch swipe handlers turn forwards and backwards in single and two-page layouts', async ({
  page,
}) => {
  await openReader(page);
  const viewport = page.locator('.reader-viewport');
  const spread = page.locator('.reader-spread');
  for (const layout of ['single', 'double']) {
    await page.getByRole('combobox', { name: 'Page layout' }).selectOption(layout);
    await expect(page.getByText('Laying out pages…')).not.toBeVisible();
    await viewport.focus();
    await page.keyboard.press('Home');
    await expect(spread).toHaveAttribute('data-page-start', '1');
    const swipe = async (from: number, to: number) => {
      await viewport.dispatchEvent('pointerdown', {
        pointerType: 'touch',
        pointerId: 9,
        clientX: from,
        clientY: 300,
      });
      await viewport.dispatchEvent('pointerup', {
        pointerType: 'touch',
        pointerId: 9,
        clientX: to,
        clientY: 305,
      });
    };
    await swipe(600, 350);
    await expect(spread).toHaveAttribute('data-page-start', layout === 'single' ? '2' : '3');
    await swipe(350, 600);
    await expect(spread).toHaveAttribute('data-page-start', '1');
  }
});
