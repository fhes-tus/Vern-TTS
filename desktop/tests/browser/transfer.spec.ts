import { expect, test, type Page } from '@playwright/test';
import { defaultSettings } from '../../src/types';

async function transferShell(page: Page) {
  await page.addInitScript(
    ({ prefs }) => {
      const state = {
        mode: 'android',
        slow: false,
        merged: false,
        exported: 0,
        deleted: false,
        accepted: [] as Record<string, unknown>[],
        discarded: [] as string[],
        restoreSettings: null as boolean | null,
      };
      Reflect.set(window, '__transfer', state);
      Reflect.set(window, 'isTauri', true);
      const preview = {
        token: 'restore-token',
        source: 'Android',
        books: 1,
        annotations: 1,
        highlights: 1,
        flashcards: 1,
        quizzes: 1,
        notes: 1,
        notebooks: 1,
        sessions: 1,
        vocabulary: 0,
        pronunciationRules: 0,
        sourceFiles: 1,
        readingLists: 1,
        noteRevisions: 1,
        noteMedia: 1,
        retainedAndroidBackups: 1,
        warnings: [
          'Imported sessions keep their recorded dates and durations. Historical sentence links are unavailable; sessions open the book at its current position.',
        ],
      };
      Reflect.set(window, '__TAURI_INTERNALS__', {
        metadata: { currentWindow: { label: 'main' }, currentWebview: { label: 'main' } },
        invoke: async (command: string, args: Record<string, unknown> = {}) => {
          if (command === 'get_settings') return structuredClone(prefs);
          if (command === 'record_reading_activity' || command === 'cancel_speech') return;
          if (command === 'get_source_info') return null;
          if (command === 'list_android_imports')
            return state.merged && !state.deleted
              ? [{ id: 'retained-source', createdAt: 1000, readings: 1, assets: 2 }]
              : [];
          if (command.startsWith('list_')) return [];
          if (command === 'inspect_backup')
            return state.mode === 'desktop'
              ? {
                  kind: 'desktop',
                  preview: {
                    ...preview,
                    source: 'Desktop',
                    retainedAndroidBackups: 0,
                    warnings: [],
                  },
                }
              : { kind: 'android', token: 'restore-token', documents: 1 };
          if (command === 'android_backup_text') {
            if (state.slow) await new Promise((resolve) => setTimeout(resolve, 700));
            return {
              text: 'First. Second.',
              pageCount: 1,
              sentenceIndexVersion: 2,
              expectedChunks: state.mode === 'bad-index' ? 99 : 2,
            };
          }
          if (command === 'accept_android_text') {
            state.accepted.push(structuredClone(args));
            return;
          }
          if (command === 'finish_android_backup') return structuredClone(preview);
          if (command === 'discard_backup') {
            state.discarded.push(args.token as string);
            return;
          }
          if (command === 'restore_backup') {
            state.merged = true;
            state.restoreSettings = args.restoreSettings as boolean;
            return;
          }
          if (command === 'export_android_import') {
            state.exported++;
            return true;
          }
          if (command === 'delete_android_import') {
            state.deleted = true;
            return;
          }
          throw new Error(`Unexpected command: ${command}`);
        },
      });
    },
    { prefs: defaultSettings },
  );
  await page.goto('/');
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await page
    .getByRole('navigation', { name: 'Settings categories' })
    .getByRole('button', { name: 'Sync & backup center' })
    .click();
}

test('Android backup uses the real indexing worker, previews limits, merges and exports retained source', async ({
  page,
}) => {
  await transferShell(page);
  await page.getByRole('button', { name: 'Restore backup…' }).click();
  const dialog = page.getByRole('dialog', { name: 'Restore Android backup' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText(/Historical sentence links are unavailable/)).toBeVisible();
  await expect(dialog.getByRole('checkbox')).not.toBeChecked();
  await expect(dialog.locator('.restore-choice')).toHaveCSS('display', 'flex');
  await expect(dialog.locator('.restore-choice')).toHaveCSS('flex-direction', 'row');
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({
    path: '.artifacts/msi-validation-20261102/android-restore-preview.png',
    fullPage: true,
    animations: 'disabled',
  });
  expect(
    await page.evaluate(() => Reflect.get(window, '__transfer').accepted[0].converted),
  ).toMatchObject({
    chunks: ['First.', 'Second.'],
    sourceToChunk: [0, 1],
    sourcePages: [1, 1],
    pageStarts: [0],
    sourceCount: 2,
  });
  await dialog.getByRole('button', { name: 'Merge backup into library' }).click();
  await expect(page.getByText('Backup merged into your library.')).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, '__transfer').restoreSettings)).toBe(false);
  await page.getByLabel('Original Android backup').selectOption('retained-source');
  await page.getByRole('button', { name: 'Export original backup' }).click();
  await expect(page.getByText('Original Android backup saved.')).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, '__transfer').exported)).toBe(1);
  await page.screenshot({
    path: '.artifacts/msi-validation-20261102/android-retained-export.png',
    fullPage: true,
  });
  await page.getByRole('button', { name: 'Delete retained backup…' }).click();
  const deleting = page.getByRole('dialog', { name: 'Delete retained Android backup' });
  await expect(deleting).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, '__transfer').deleted)).toBe(false);
  await deleting.getByRole('button', { name: 'Delete retained backup', exact: true }).click();
  await expect(page.getByText('Retained Android backup deleted.')).toBeVisible();
  await expect(page.getByLabel('Original Android backup')).toHaveCount(0);
});

test('cancellation and sentence-index errors release native staging without a merge', async ({
  page,
}) => {
  await transferShell(page);
  await page.evaluate(() => {
    Reflect.get(window, '__transfer').slow = true;
  });
  await page.getByRole('button', { name: 'Restore backup…' }).click();
  await page.getByRole('button', { name: 'Cancel conversion' }).click();
  await expect(page.getByText('Backup conversion cancelled.', { exact: true })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__transfer').discarded.length))
    .toBe(1);
  expect(await page.evaluate(() => Reflect.get(window, '__transfer').accepted.length)).toBe(0);
  await page.evaluate(() => {
    const state = Reflect.get(window, '__transfer');
    state.slow = false;
    state.mode = 'bad-index';
  });
  await page.getByRole('button', { name: 'Restore backup…' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__transfer').discarded.length))
    .toBe(2);
  expect(await page.evaluate(() => Reflect.get(window, '__transfer').merged)).toBe(false);
});

test('desktop backups bypass Android conversion and cancelling the preview releases its token', async ({
  page,
}) => {
  await transferShell(page);
  await page.evaluate(() => {
    Reflect.get(window, '__transfer').mode = 'desktop';
  });
  await page.getByRole('button', { name: 'Restore backup…' }).click();
  const dialog = page.getByRole('dialog', { name: 'Restore backup', exact: true });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__transfer').discarded.length))
    .toBeGreaterThan(0);
  expect(await page.evaluate(() => Reflect.get(window, '__transfer').accepted.length)).toBe(0);
});
