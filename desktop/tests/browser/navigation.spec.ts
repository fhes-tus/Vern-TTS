import { expect, test, type Page } from '@playwright/test';
import { writeFile } from 'node:fs/promises';
import classics from '../../src/data/classics.json' with { type: 'json' };
import {
  defaultSettings,
  type Book,
  type Settings,
  type Quiz,
  type QuizInput,
  type CardInput,
  type Flashcard,
  type PersonalNote,
  type NoteInput,
  type Notebook,
  type ReadingList,
  type ReadingListInput,
  type NoteRevision,
  type NoteMedia,
  type OpenedBook,
} from '../../src/types';

async function openShell(page: Page, initialBooks: Book[] = []) {
  await page.addInitScript(
    ({ classics, prefs, initialBooks }) => {
      const state = {
        books: initialBooks,
        prefs,
        installed: {} as Record<string, string>,
        failDownload: false,
        opened: '',
        referenceRequests: [] as { site: string; text: string }[],
        failReference: false,
        quizzes: [] as Quiz[],
        cards: [] as Flashcard[],
        notes: [] as PersonalNote[],
        notebooks: [] as Notebook[],
        lists: [] as ReadingList[],
        revisions: [] as NoteRevision[],
        media: [] as NoteMedia[],
        recent: [] as OpenedBook[],
        failHistory: false,
        failHistoryRemove: false,
        historyOpens: [] as string[],
      };
      Reflect.set(window, '__shell', state);
      Reflect.set(window, 'isTauri', true);
      Reflect.set(window, '__TAURI_INTERNALS__', {
        metadata: { currentWindow: { label: 'main' }, currentWebview: { label: 'main' } },
        invoke: async (command: string, args: Record<string, unknown> = {}) => {
          if (command === 'record_reading_activity') return;
          if (command === 'record_reading_open') {
            state.historyOpens.push(String(args.id));
            return;
          }
          if (command === 'list_opened_books') {
            if (state.failHistory) throw new Error('History is temporarily unavailable.');
            return structuredClone(state.recent);
          }
          if (command === 'remove_opened_book' || command === 'clear_opened_books') {
            if (state.failHistoryRemove) throw new Error('Could not remove history. Try again.');
            state.recent =
              command === 'clear_opened_books'
                ? []
                : state.recent.filter((item) => item.id !== args.id);
            return;
          }
          if (command === 'list_reading_lists') return structuredClone(state.lists);
          if (command === 'save_reading_list') {
            const input = args.input as ReadingListInput;
            const old = state.lists.find((list) => list.id === input.id);
            const list: ReadingList = {
              ...input,
              id: input.id ?? crypto.randomUUID(),
              createdAt: old?.createdAt ?? Date.now(),
              updatedAt: Date.now(),
              items: input.bookIds.map((bookId) => ({ bookId, addedAt: Date.now() })),
            };
            state.lists = [list, ...state.lists.filter((item) => item.id !== list.id)];
            return structuredClone(list);
          }
          if (command === 'delete_reading_list') {
            state.lists = state.lists.filter((list) => list.id !== args.id);
            return;
          }
          if (command === 'list_personal_notes') return structuredClone(state.notes);
          if (command === 'list_note_revisions')
            return structuredClone(state.revisions.filter((item) => item.noteId === args.id));
          if (command === 'list_note_media')
            return structuredClone(state.media.filter((item) => item.noteId === args.id));
          if (command === 'get_note_media')
            return 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==';
          if (command === 'attach_note_media') {
            const item: NoteMedia = {
              id: crypto.randomUUID(),
              noteId: args.id as string,
              name: 'Reference.png',
              mime: 'image/png',
              size: 69,
              sha256: 'a'.repeat(64),
              createdAt: Date.now(),
            };
            state.media.push(item);
            return structuredClone(item);
          }
          if (command === 'delete_note_media') {
            state.media = state.media.filter((item) => item.id !== args.id);
            return;
          }
          if (command === 'list_notebooks') return structuredClone(state.notebooks);
          if (command === 'save_notebook') {
            const item = {
              id: (args.id as string) || 'notebook-1',
              name: args.name as string,
              createdAt: 1,
            };
            state.notebooks = [item];
            return;
          }
          if (command === 'save_personal_note') {
            const input = args.input as NoteInput;
            const previous = state.notes.find((note) => note.id === input.id);
            if (previous && (previous.content !== input.content || previous.title !== input.title))
              state.revisions.unshift({
                id: crypto.randomUUID(),
                noteId: previous.id,
                note: structuredClone(previous),
              });
            const note = {
              ...input,
              id: input.id ?? 'note-1',
              createdAt: 1,
              updatedAt: 2,
              deletedAt: null,
            };
            state.notes = [note];
            return structuredClone(note);
          }
          if (command === 'trash_personal_note') {
            state.notes.find((n) => n.id === args.id)!.deletedAt = args.trashed ? 3 : null;
            return;
          }
          if (command === 'delete_personal_note') {
            state.notes = state.notes.filter((n) => n.id !== args.id);
            return;
          }
          if (command === 'delete_notebook') {
            state.notebooks = state.notebooks.filter((n) => n.id !== args.id);
            state.notes.forEach((n) => (n.labelIds = n.labelIds.filter((id) => id !== args.id)));
            return;
          }
          if (command === 'list_quizzes') return structuredClone(state.quizzes);
          if (command === 'list_flashcards') return structuredClone(state.cards);
          if (command === 'save_quiz') {
            const input = args.input as QuizInput;
            const quiz = {
              ...input,
              id: input.id ?? 'quiz-1',
              bestScore: null,
              lastScore: null,
              attempts: 0,
              createdAt: 1,
            };
            state.quizzes = [quiz];
            return structuredClone(quiz);
          }
          if (command === 'submit_quiz') {
            const quiz = state.quizzes.find((q) => q.id === args.id)!;
            const score = quiz.questions.filter(
              (q, i) => q.options[(args.answers as number[])[i]] === q.answer,
            ).length;
            quiz.lastScore = score;
            quiz.bestScore = Math.max(quiz.bestScore ?? 0, score);
            quiz.attempts++;
            return structuredClone(quiz);
          }
          if (command === 'delete_quiz') {
            state.quizzes = state.quizzes.filter((q) => q.id !== args.id);
            return;
          }
          if (command === 'import_flashcards') {
            state.cards = (args.cards as CardInput[]).map((c, i) => ({
              ...c,
              id: `card-${i}`,
              recall: '',
              dueAt: 0,
              intervalDays: 0,
              repetitions: 0,
              ease: 2.5,
            }));
            return state.cards.length;
          }
          if (command === 'list_books') return structuredClone(state.books);
          if (command === 'get_settings') return structuredClone(state.prefs);
          if (command === 'save_settings') {
            state.prefs = structuredClone(args.settings as Settings);
            return;
          }
          if (
            [
              'list_voices',
              'list_all_annotations',
              'list_flashcards',
              'list_annotations',
              'list_quizzes',
            ].includes(command)
          )
            return [];
          if (command === 'list_classics')
            return classics.map((book) => ({ ...book, bookId: state.installed[book.id] ?? null }));
          if (command === 'download_classic') {
            if (state.failDownload) {
              state.failDownload = false;
              throw new Error('The book server is unavailable. Try again.');
            }
            return {
              name: `${args.id}.txt`,
              bytes: Array.from(
                new TextEncoder().encode(
                  '*** START OF THE PROJECT GUTENBERG EBOOK ALICE ***\nAlice watched the rabbit disappear. A second passage.\n*** END OF THE PROJECT GUTENBERG EBOOK ALICE ***',
                ),
              ),
            };
          }
          if (command === 'save_classic') {
            const entry = classics.find((book) => book.id === args.id)!;
            const book = {
              ...(args.book as Book),
              id: entry.id,
              title: entry.title,
              author: entry.author,
              updatedAt: 1,
            };
            state.books = [book];
            state.installed[entry.id] = book.id;
            return structuredClone(book);
          }
          if (command === 'save_progress') {
            state.books[0].position = args.position as number;
            return;
          }
          if (command === 'get_source_info') return null;
          if (command === 'open_assistant') {
            state.opened = args.id as string;
            return;
          }
          if (command === 'open_reference') {
            if (state.failReference) throw new Error('Reference browser could not be opened.');
            state.referenceRequests.push({ site: String(args.site), text: String(args.text) });
            return;
          }
          if (command === 'cancel_speech' || command === 'cancel_classic_download') return;
          throw new Error(`Unexpected shell command ${command}`);
        },
      });
    },
    { classics, prefs: defaultSettings, initialBooks },
  );
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible();
}

test('reading history opens current progress, handles missing books and clears only recent entries', async ({
  page,
}) => {
  const book: Book = {
    id: 'a'.repeat(64),
    title: 'A history fixture with saved progress',
    author: 'Test library',
    format: 'TXT',
    chunks: ['First passage.', 'Current saved passage.'],
    position: 1,
    favorite: false,
    updatedAt: 1,
  };
  await openShell(page, [book]);
  await page.evaluate(({ id, title }) => {
    Reflect.get(window, '__shell').recent = [
      {
        id,
        bookId: id,
        title,
        format: 'TXT',
        openedAt: 1_793_574_000_000,
        position: 0,
        passages: 2,
      },
      {
        id: 'b'.repeat(64),
        bookId: null,
        title: 'A book removed from the Android library',
        format: 'EPUB',
        openedAt: 0,
        position: null,
        passages: 0,
      },
    ];
  }, book);
  await page.getByRole('button', { name: 'Library', exact: true }).click();
  await page.getByRole('button', { name: 'History', exact: true }).click();
  const rows = page.locator('.opened-book-list > li');
  await expect(rows).toHaveCount(2);
  await expect(rows.last()).toContainText('Not in your library');
  await expect(rows.last()).toContainText('Date unavailable');
  await expect(rows.last().getByRole('button', { name: 'Open book' })).toBeDisabled();
  const snapshot = await page.evaluate(() => {
    const html = document.documentElement.cloneNode(true) as HTMLElement;
    html.querySelectorAll('script, style, link').forEach((node) => node.remove());
    html.querySelectorAll('[hidden], [style]').forEach((node) => {
      if (node.hasAttribute('hidden') || (node as HTMLElement).style.display === 'none')
        node.remove();
    });
    html
      .querySelectorAll('img[src="/vern.png"]')
      .forEach((node) => node.setAttribute('src', '/public/vern.png'));
    const css = document.createElement('style');
    css.textContent = Array.from(document.styleSheets)
      .flatMap((sheet) => Array.from(sheet.cssRules, (rule) => rule.cssText))
      .join('\n')
      .replaceAll('/fonts/', '/public/fonts/');
    html.querySelector('head')!.append(css);
    return '<!doctype html>\n' + html.outerHTML;
  });
  await writeFile('.artifacts/msi-validation-20261102/opened-books-live.html', snapshot);
  for (const width of [1440, 900, 768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    await page.screenshot({
      path: `.artifacts/msi-validation-20261102/opened-books-${width}.png`,
      fullPage: true,
    });
  }
  await page.setViewportSize({ width: 1440, height: 900 });
  await rows.first().getByRole('button', { name: 'Open book' }).click();
  await expect(page.getByRole('region', { name: 'Reading room', exact: true })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__shell').historyOpens))
    .toEqual([book.id]);
  expect(await page.evaluate(() => Reflect.get(window, '__shell').books[0].position)).toBe(1);
  await page.keyboard.press('Alt+ArrowLeft');
  await expect(page.getByRole('heading', { name: 'Reading history', exact: true })).toBeVisible();
  await page.getByLabel('Search history').fill('nothing matches');
  await expect(page.getByText('No books match your search.')).toBeVisible();
  await page.getByLabel('Search history').fill('');
  await rows.last().getByRole('button', { name: 'Remove…', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(rows).toHaveCount(2);
  await rows.last().getByRole('button', { name: 'Remove…', exact: true }).click();
  await page.evaluate(() => {
    Reflect.get(window, '__shell').failHistoryRemove = true;
  });
  const dialog = page.getByRole('dialog', { name: 'Remove from history' });
  await dialog.getByRole('button', { name: 'Remove from history', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('Could not remove history');
  await page.evaluate(() => {
    Reflect.get(window, '__shell').failHistoryRemove = false;
  });
  await dialog.getByRole('button', { name: 'Remove from history', exact: true }).click();
  await expect(rows).toHaveCount(1);
  await page.getByRole('button', { name: 'Clear history…', exact: true }).click();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Clear history', exact: true })
    .click();
  await expect(page.getByText('Books you open or listen to appear here.')).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, '__shell').books[0].position)).toBe(1);
  expect(await page.evaluate(() => Reflect.get(window, '__shell').books.length)).toBe(1);
});

test('reading history reports load errors and retries without claiming an empty library', async ({
  page,
}) => {
  await openShell(page);
  await page.evaluate(() => {
    Reflect.get(window, '__shell').failHistory = true;
  });
  await page.getByRole('button', { name: 'Library', exact: true }).click();
  await page.getByRole('button', { name: 'History', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('History is temporarily unavailable');
  await expect(page.getByText('Books you open or listen to appear here.')).not.toBeVisible();
  await page.evaluate(() => {
    Reflect.get(window, '__shell').failHistory = false;
  });
  await page.getByRole('button', { name: 'Retry', exact: true }).click();
  await expect(page.getByRole('alert')).not.toBeVisible();
  await expect(page.getByText('Books you open or listen to appear here.')).toBeVisible();
});

test('reading lists arrange books, preserve navigation and delete only the list', async ({
  page,
}) => {
  const books: Book[] = ['Alpha', 'Beta'].map((title, index) => ({
    id: String(index),
    title,
    author: 'A Reader',
    format: 'TXT',
    chunks: ['One.', 'Two.'],
    position: 0,
    favorite: false,
    collection: '',
    updatedAt: 1,
  }));
  await openShell(page, books);
  await page.getByRole('button', { name: 'Library', exact: true }).click();
  await page.getByRole('button', { name: 'Reading lists', exact: true }).click();
  await page.getByRole('button', { name: 'New list', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'New reading list' });
  await dialog.getByLabel('List name', { exact: true }).fill('Weekend reads');
  await dialog.getByRole('checkbox', { name: 'Beta', exact: true }).check();
  await dialog.getByRole('checkbox', { name: 'Alpha', exact: true }).check();
  await dialog.getByRole('button', { name: 'Save list', exact: true }).click();
  await expect(page.locator('.list-books li').first()).toContainText('Beta');
  await page.getByRole('button', { name: 'Move Alpha up', exact: true }).click();
  await expect(page.locator('.list-books li').first()).toContainText('Alpha');
  await page.locator('.list-book-open').first().click();
  await expect(page.getByRole('region', { name: 'Reading room', exact: true })).toBeVisible();
  await page.keyboard.press('Alt+ArrowLeft');
  await expect(page.getByRole('heading', { name: 'Weekend reads', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Archive', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Restore list', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Restore list', exact: true }).click();
  await page.getByRole('button', { name: 'Remove Beta from list', exact: true }).click();
  await expect(page.locator('.list-books li')).toHaveCount(1);
  await page.screenshot({ path: '.artifacts/reading-lists-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 900, height: 650 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(900);
  await page.screenshot({ path: '.artifacts/reading-lists-900.png', fullPage: true });
  await page.getByRole('button', { name: 'Delete reading list', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Delete list', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Give your books a path' })).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, '__shell').books.length)).toBe(2);
});

test('settings categories expose the tools, save defaults and return to the entry screen', async ({
  page,
}) => {
  await openShell(page);
  await page.getByRole('button', { name: 'Notes', exact: true }).click();
  await page.getByRole('button', { name: 'Notes settings', exact: true }).click();
  const categories = page.getByRole('navigation', { name: 'Settings categories' });
  await expect(page.getByRole('heading', { name: 'Notes settings', exact: true })).toBeVisible();
  await page.getByLabel('New notes are checklists').check();
  await page.getByLabel('Paper template').selectOption('dots');
  await page.getByLabel('Default note layout').selectOption('list');
  await categories.getByRole('button', { name: 'PDF import tools', exact: true }).click();
  await page.getByLabel('First page').fill('2');
  await page.getByLabel('Last page', { exact: true }).fill('8');
  await page.screenshot({ path: '.artifacts/settings-pdf.png', fullPage: true });
  await categories.getByRole('button', { name: 'Accessibility', exact: true }).click();
  await page.getByLabel('Reduce transparency', { exact: false }).check();
  await expect(page.locator('html')).toHaveAttribute('data-reduce-transparency', 'true');
  await page.screenshot({ path: '.artifacts/settings-accessibility.png', fullPage: true });
  await categories.getByRole('button', { name: 'Pronunciation rules', exact: true }).click();
  await page.getByLabel('Written word or phrase').fill('Vern');
  await page.getByLabel('Say it as').fill('Vurn');
  await page.getByRole('button', { name: 'Save pronunciation', exact: true }).click();
  await categories.getByRole('button', { name: 'Sync & backup center', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Save desktop backup', exact: true }),
  ).toBeVisible();
  await categories.getByRole('button', { name: 'About', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Vern', exact: true })).toBeVisible();
  await categories.getByRole('button', { name: 'AI & study mode', exact: true }).click();
  await page.getByLabel('Preferred assistant').selectOption('claude');
  await page.screenshot({ path: '.artifacts/settings-desktop.png', fullPage: true });
  await page.getByRole('button', { name: 'Close settings', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Notes.', exact: true })).toBeVisible();
  await expect(page.locator('.personal-note-list')).toHaveCount(1);
  await page.getByRole('button', { name: 'New note', exact: true }).click();
  const editor = page.getByRole('dialog', { name: 'New note' });
  await expect(editor.getByLabel('Checklist (one item per line)')).toBeChecked();
  await expect(editor.getByLabel('Note text')).toHaveAttribute('data-paper', 'dots');
  await editor.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__shell').prefs.pdfImport.endPage))
    .toBe(8);
  await expect
    .poll(() =>
      page.evaluate(() => Reflect.get(window, '__shell').prefs.pronunciationRules[0].replaceWith),
    )
    .toBe('Vurn');
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await categories.getByRole('button', { name: 'AI & study mode', exact: true }).click();
  await page.getByRole('button', { name: 'Open AI handoff', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Claude', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.setViewportSize({ width: 900, height: 650 });
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await page.getByLabel('Settings category', { exact: true }).selectOption('settings-notes');
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(900);
  await expect(page.locator('.topbar')).toHaveCSS('height', '56px');
  await page.screenshot({ path: '.artifacts/settings-900.png', fullPage: true });
});

test('four primary destinations contain library, study, listening and settings tools', async ({
  page,
}) => {
  await openShell(page);
  const nav = page.getByRole('navigation', { name: 'Main navigation' });
  await expect(nav.getByRole('button')).toHaveText(['Home', 'Library', 'Study', 'Notes']);
  await page.getByRole('button', { name: 'Library', exact: true }).click();
  await expect(
    page.getByRole('navigation', { name: 'Library sections' }).getByRole('button'),
  ).toHaveText(['Added', 'Classics', 'Reading lists', 'History']);
  await page.getByRole('button', { name: 'Classics', exact: true }).click();
  await expect(page.locator('.classic-card')).toHaveCount(36);
  await expect
    .poll(() =>
      page
        .locator('.classic-art img')
        .first()
        .evaluate((img: HTMLImageElement) => img.naturalWidth),
    )
    .toBeGreaterThan(0);
  await page.screenshot({ path: '.artifacts/classics-desktop.png' });
  await page.getByRole('button', { name: 'Study', exact: true }).click();
  await page.getByRole('button', { name: 'Vocabulary & pronunciation', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Vocabulary notebook' })).toBeVisible();
  await page.getByRole('button', { name: 'AI handoff', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'AI handoff.' })).toBeVisible();
  await page.getByRole('button', { name: 'Home', exact: true }).click();
  await page.getByRole('button', { name: 'Listening queue', exact: true }).click();
  await page.getByRole('button', { name: 'Voice Studio', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Voice Studio.' })).toBeVisible();
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await expect(page.getByRole('group', { name: 'Theme colour' })).toBeVisible();
  await page.getByRole('button', { name: 'Close settings', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Voice Studio.' })).toBeVisible();
  await page.getByRole('button', { name: 'Home', exact: true }).click();
  await page.setViewportSize({ width: 900, height: 650 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(900);
  await page.screenshot({ path: '.artifacts/home-desktop.png' });
});

test('catalogue search, failure retry, worker import and read-book action are connected', async ({
  page,
}) => {
  await openShell(page);
  await page.getByRole('button', { name: 'Explore Classics', exact: true }).click();
  await page.getByRole('textbox', { name: 'Search classics' }).fill('Alice');
  await expect(page.locator('.classic-card')).toHaveCount(1);
  await page.evaluate(() => {
    Reflect.get(window, '__shell').failDownload = true;
  });
  await page.getByRole('button', { name: 'Add to library', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('book server is unavailable');
  await page.getByRole('button', { name: 'Try again', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Read book', exact: true })).toBeVisible();
  await expect(page.locator('.notice-banner')).toContainText('Added Alice in Wonderland');
  await page.getByRole('button', { name: 'Dismiss notification', exact: true }).click();
  await expect(page.getByText('Added Alice in Wonderland', { exact: true })).not.toBeVisible();
  await page.getByRole('button', { name: 'Read book', exact: true }).click();
  await expect(page.locator('.flow-paper')).toContainText('Alice watched the rabbit disappear.');
  await expect(page.locator('.flow-paper')).not.toContainText('PROJECT GUTENBERG');
  await page.getByRole('button', { name: 'Reader settings', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Reader settings' })).toBeVisible();
});

test('palette families, custom accent, system mode and reduced motion use the native settings bridge', async ({
  page,
}) => {
  await openShell(page);
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await page.getByRole('button', { name: 'Dark', exact: true }).click();
  for (const name of ['Sage', 'Midnight', 'Dracula', 'GitHub', 'B/W Gradient', 'Neon']) {
    await page.getByRole('button', { name, exact: true }).click();
    await expect(page.getByRole('button', { name, exact: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  }
  await page.getByRole('button', { name: 'Dracula', exact: true }).click();
  await page.getByLabel('Custom accent colour').fill('#38a169');
  await expect
    .poll(() =>
      page.evaluate(() =>
        getComputedStyle(document.documentElement).getPropertyValue('--accent').trim(),
      ),
    )
    .toBe('#38a169');
  await page.getByLabel('Reduce motion', { exact: false }).check();
  await expect(page.locator('html')).toHaveAttribute('data-reduce-motion', 'true');
  await page.emulateMedia({ colorScheme: 'light' });
  await page.getByRole('button', { name: 'System', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__shell').prefs.colorTheme))
    .toBe('dracula');
  await page.screenshot({ path: '.artifacts/theme-desktop.png' });
});

test('AI handoff copies reviewed context and opens only the selected provider through native command', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await openShell(page);
  await page.getByRole('button', { name: 'Study', exact: true }).click();
  await page.getByRole('button', { name: 'AI handoff', exact: true }).click();
  await page.getByRole('button', { name: 'Summarise', exact: true }).click();
  await page.getByLabel('AI passage or context').fill('Alice watched the rabbit disappear.');
  await page.getByRole('button', { name: 'Notes', exact: true }).click();
  await page.getByRole('button', { name: 'Study', exact: true }).click();
  await page.getByRole('button', { name: 'AI handoff', exact: true }).click();
  await expect(page.getByLabel('AI passage or context')).toHaveValue(
    'Alice watched the rabbit disappear.',
  );
  await page.getByRole('button', { name: 'Claude', exact: true }).click();
  await page.getByRole('button', { name: 'Copy & open assistant', exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__shell').opened))
    .toBe('claude');
  const clipboard = await page.evaluate(() => navigator.clipboard.readText());
  expect(clipboard).toContain('Summarise the main ideas');
  expect(clipboard).toContain('Alice watched the rabbit disappear.');
});

test('reference tools send only reviewed text on explicit actions, retain drafts and recover from errors', async ({
  page,
}) => {
  await openShell(page);
  await page.getByRole('button', { name: 'Study', exact: true }).click();
  await page.getByRole('button', { name: 'Vocabulary & pronunciation', exact: true }).click();
  const text = page.getByLabel('Text to look up or translate');
  const requests = () => page.evaluate(() => Reflect.get(window, '__shell').referenceRequests);
  await expect(page.getByRole('button', { name: 'Open Google Translate' })).toBeDisabled();
  await text.fill('café & sl=es # 日本語');
  expect(await requests()).toEqual([]);
  await page.getByRole('button', { name: 'Notes', exact: true }).click();
  await page.getByRole('button', { name: 'Go back', exact: true }).click();
  await expect(text).toHaveValue('café & sl=es # 日本語');
  for (const [label, site] of [
    ['Open Wikipedia', 'wikipedia'],
    ['Open Wiktionary', 'wiktionary'],
    ['Open Google Translate', 'translate'],
  ]) {
    await page.getByRole('button', { name: label, exact: true }).click();
    await expect.poll(requests).toContainEqual({ site, text: 'café & sl=es # 日本語' });
  }
  await page.evaluate(() => {
    Reflect.get(window, '__shell').failReference = true;
  });
  await page.getByRole('button', { name: 'Open Wikipedia', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Reference browser could not be opened.');
  await expect(text).toHaveValue('café & sl=es # 日本語');
  await page.evaluate(() => {
    Reflect.get(window, '__shell').failReference = false;
  });
  await page.getByRole('button', { name: 'Open Wikipedia', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(await page.evaluate(() => Reflect.get(window, '__shell').prefs.vocabulary)).toEqual([]);
  const snapshot = await page.evaluate(() => {
    const html = document.documentElement.cloneNode(true) as HTMLElement;
    html.querySelectorAll('script, style, link').forEach((node) => node.remove());
    // Activity retains inactive screens. Omit those inline-hidden trees from
    // the static visual snapshot, while keeping CSS-driven responsive elements.
    html.querySelectorAll('[hidden], [style]').forEach((node) => {
      if (node.hasAttribute('hidden') || (node as HTMLElement).style.display === 'none')
        node.remove();
    });
    html.querySelectorAll('img[src="/vern.png"]').forEach((node) => {
      node.setAttribute('src', '/public/vern.png');
    });
    const css = document.createElement('style');
    css.textContent = Array.from(document.styleSheets)
      .flatMap((sheet) => Array.from(sheet.cssRules, (rule) => rule.cssText))
      .join('\n')
      .replaceAll('/fonts/', '/public/fonts/');
    html.querySelector('head')!.append(css);
    const reference = html.querySelector('#reference-text');
    if (reference)
      reference.textContent = (
        document.querySelector('#reference-text') as HTMLTextAreaElement
      ).value;
    return '<!doctype html>\n' + html.outerHTML;
  });
  await writeFile('.artifacts/msi-validation-20261102/reading-tools-live.html', snapshot);
  for (const width of [1440, 900, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await page
      .getByRole('heading', { name: 'Look up or translate', exact: true })
      .scrollIntoViewIfNeeded();
    await expect(text).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page.screenshot({
      path: `.artifacts/msi-validation-20261102/reference-tools-${width}.png`,
    });
  }
});

test('study reply preview, quiz practice, explanations, deletion and imported cards are connected', async ({
  page,
}) => {
  await openShell(page);
  await page.getByRole('button', { name: 'Study', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Study Hub.' })).toBeVisible();
  await page.screenshot({ path: '.artifacts/study-overview.png' });
  await page.getByRole('button', { name: 'Create or paste quiz', exact: true }).click();
  await page.getByLabel('Quiz title').fill('Elements');
  await page
    .getByLabel('Quiz questions')
    .fill(
      'Q: Which is an element?\nA) Carbon\nB) Water\nAnswer: A\nExplanation: Carbon is an element.',
    );
  await page.getByRole('button', { name: 'Preview items', exact: true }).click();
  await expect(page.getByRole('heading', { name: '1 questions ready to save' })).toBeVisible();
  await page.getByRole('button', { name: 'Save study set', exact: true }).click();
  await page.getByRole('button', { name: 'Start quiz', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Check answers', exact: true })).toBeDisabled();
  await page.getByRole('radio', { name: 'Carbon', exact: true }).check();
  await page.getByRole('button', { name: 'Check answers', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Score 1/1');
  await expect(page.locator('.quiz-question')).toContainText('Carbon is an element.');
  await page.screenshot({ path: '.artifacts/quiz-result.png' });
  await page.getByRole('button', { name: 'Close practice', exact: true }).click();
  await expect(page.locator('.quiz-list')).toContainText('Best: 1/1');
  await page.getByRole('button', { name: 'Delete Elements', exact: true }).click();
  await page.getByRole('button', { name: 'Delete quiz', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Your first quiz starts here' })).toBeVisible();
  await page.getByRole('button', { name: 'Paste cards', exact: true }).click();
  await page.getByLabel('Deck name').fill('Elements');
  await page.getByLabel('Flashcard reply').fill('Q: What is carbon?\nA: An element.');
  await page.getByRole('button', { name: 'Preview items', exact: true }).click();
  await page.getByRole('button', { name: 'Save study set', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Flashcards.' })).toBeVisible();
  await expect(page.getByText('What is carbon?', { exact: true })).toBeVisible();
});

test('selected reader text seeds an editable AI handoff and returns to study imports', async ({
  page,
}) => {
  await openShell(page);
  await page.getByRole('button', { name: 'Explore Classics', exact: true }).click();
  await page.getByLabel('Search classics').fill('Alice');
  await page.getByRole('button', { name: 'Add to library', exact: true }).click();
  await page.getByRole('button', { name: 'Read book', exact: true }).click();
  await expect(page.locator('.flow-paper')).toBeVisible();
  await page
    .locator('.flow-paper p')
    .first()
    .evaluate((element) => {
      const range = document.createRange();
      range.selectNodeContents(element);
      const selection = window.getSelection()!;
      selection.removeAllRanges();
      selection.addRange(range);
    });
  await page.getByRole('button', { name: 'Ask AI', exact: true }).click();
  await expect(page.getByLabel('AI passage or context')).toHaveValue(/Alice watched the rabbit/);
  await page.getByRole('button', { name: 'Import a study reply', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Create or paste quiz', exact: true }),
  ).toBeVisible();
});

test('note versions and attachments require review and preserve text when cancelled', async ({
  page,
}) => {
  await openShell(page);
  await page.getByRole('button', { name: 'Notes', exact: true }).click();
  await page.getByRole('button', { name: 'New note', exact: true }).click();
  await page.getByLabel('Note title').fill('Saved thought');
  await page.getByLabel('Note text').fill('Original words.');
  await page.getByRole('button', { name: 'Images & recordings', exact: true }).click();
  await page.getByRole('button', { name: 'Save & add attachment…', exact: true }).click();
  await expect(page.getByRole('listitem').filter({ hasText: 'Reference.png' })).toBeVisible();
  await page.getByRole('button', { name: 'Preview', exact: true }).click();
  await expect(page.getByRole('img', { name: 'Reference.png', exact: true })).toBeVisible();
  await page.getByLabel('Note text').fill('Updated words.');
  await page.getByRole('button', { name: 'Save note', exact: true }).click();
  await page.getByRole('button', { name: 'Edit note', exact: true }).click();
  await page.getByRole('button', { name: 'Version history', exact: true }).click();
  await page.locator('.note-history summary').first().click();
  await expect(page.locator('.note-history pre').first()).toHaveText('Original words.');
  await page.getByRole('button', { name: 'Use this version', exact: true }).first().click();
  await expect(page.getByLabel('Note text')).toHaveValue('Original words.');
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page.getByRole('article').filter({ hasText: 'Saved thought' })).toContainText(
    'Updated words.',
  );
  await page.getByRole('button', { name: 'Edit note', exact: true }).click();
  await page.getByRole('button', { name: 'Images & recordings', exact: true }).click();
  await page.getByRole('button', { name: 'Remove attachment Reference.png', exact: true }).click();
  await page.getByRole('button', { name: 'Keep attachment', exact: true }).click();
  await expect(page.getByRole('listitem').filter({ hasText: 'Reference.png' })).toBeVisible();
  await page.getByRole('button', { name: 'Remove attachment Reference.png', exact: true }).click();
  await page.getByRole('button', { name: 'Remove attachment', exact: true }).click();
  await expect(page.getByText('No attachments yet.', { exact: true })).toBeVisible();
  await page.screenshot({ path: '.artifacts/note-history-attachments.png', fullPage: true });
});

test('saving a checklist before an attachment normalizes lines exactly once', async ({ page }) => {
  await openShell(page);
  await page.getByRole('button', { name: 'Notes', exact: true }).click();
  await page.getByRole('button', { name: 'New note', exact: true }).click();
  await page.getByLabel('Note title').fill('Reading checklist');
  await page.getByLabel('Note text').fill('Read chapter\n- [x] Kept complete\n\nReflect');
  await page.getByLabel('Checklist (one item per line)', { exact: true }).check();
  await page.getByRole('button', { name: 'Images & recordings', exact: true }).click();
  await page.getByRole('button', { name: 'Save & add attachment…', exact: true }).click();
  const normalized = '- [ ] Read chapter\n- [x] Kept complete\n\n- [ ] Reflect';
  await expect(page.getByLabel('Note text')).toHaveValue(normalized);
  await page.getByRole('button', { name: 'Save note', exact: true }).click();
  await expect(page.getByRole('checkbox', { name: 'Read chapter', exact: true })).not.toBeChecked();
  await expect(page.getByRole('checkbox', { name: 'Kept complete', exact: true })).toBeChecked();
  await page.getByRole('button', { name: 'Edit note', exact: true }).click();
  await expect(page.getByLabel('Note text')).toHaveValue(normalized);
});

test('personal notes support notebooks, checklist ticks, pinning and recoverable trash', async ({
  page,
}) => {
  await openShell(page);
  await page.getByRole('button', { name: 'Notes', exact: true }).click();
  await page.getByRole('button', { name: 'Notebooks', exact: true }).click();
  await page.getByLabel('Notebook name').fill('Reading ideas');
  await page.getByRole('button', { name: 'Add notebook', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Rename Reading ideas', exact: true }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Close', exact: true }).click();
  await page.getByRole('button', { name: 'New note', exact: true }).click();
  await page.getByLabel('Note title').fill('Weekend reading');
  await page.getByLabel('Note text').fill('Read a chapter\nWrite a reflection');
  await page.getByLabel('Checklist (one item per line)', { exact: true }).check();
  await page.getByLabel('Pin this note', { exact: true }).check();
  await page.getByRole('checkbox', { name: 'Reading ideas', exact: true }).check();
  await page.getByRole('button', { name: 'Save note', exact: true }).click();
  await page.getByRole('checkbox', { name: 'Read a chapter', exact: true }).check();
  await expect(page.getByRole('checkbox', { name: 'Read a chapter', exact: true })).toBeChecked();
  await expect(page.getByLabel('Pinned', { exact: true })).toBeVisible();
  await page.screenshot({ path: '.artifacts/notes-workspace.png' });
  await page.getByRole('button', { name: 'Move Weekend reading to Trash', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Weekend reading', exact: true }),
  ).not.toBeVisible();
  await page.getByRole('button', { name: 'Trash', exact: true }).click();
  await page.getByRole('button', { name: 'Restore note', exact: true }).click();
  await page.getByRole('button', { name: 'My notes', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Weekend reading', exact: true })).toBeVisible();
  await expect(page.getByRole('checkbox', { name: 'Read a chapter', exact: true })).toBeChecked();
  await page.getByRole('button', { name: 'Notebooks', exact: true }).click();
  await page.getByRole('button', { name: 'Remove notebook Reading ideas', exact: true }).click();
  await page.getByRole('button', { name: 'Remove notebook', exact: true }).click();
  await page.getByRole('button', { name: 'Close', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Weekend reading', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Book notes & bookmarks', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Notes & bookmarks.' })).toBeVisible();
});
