import { expect, test, type Page } from '@playwright/test';
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
} from '../../src/types';

async function openShell(page: Page) {
  await page.addInitScript(
    ({ classics, prefs }) => {
      const state = {
        books: [] as Book[],
        prefs,
        installed: {} as Record<string, string>,
        failDownload: false,
        opened: '',
        quizzes: [] as Quiz[],
        cards: [] as Flashcard[],
        notes: [] as PersonalNote[],
        notebooks: [] as Notebook[],
      };
      Reflect.set(window, '__shell', state);
      Reflect.set(window, 'isTauri', true);
      Reflect.set(window, '__TAURI_INTERNALS__', {
        metadata: { currentWindow: { label: 'main' }, currentWebview: { label: 'main' } },
        invoke: async (command: string, args: Record<string, unknown> = {}) => {
          if (command === 'list_personal_notes') return structuredClone(state.notes);
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
          if (command === 'cancel_speech' || command === 'cancel_classic_download') return;
          throw new Error(`Unexpected shell command ${command}`);
        },
      });
    },
    { classics, prefs: defaultSettings },
  );
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'A little time for you.' })).toBeVisible();
}

test('four primary destinations contain library, study, listening and settings tools', async ({
  page,
}) => {
  await openShell(page);
  const nav = page.getByRole('navigation', { name: 'Main navigation' });
  await expect(nav.getByRole('button')).toHaveText(['Home', 'Library', 'Study', 'Notes']);
  await page.getByRole('button', { name: 'Library', exact: true }).click();
  await expect(
    page.getByRole('navigation', { name: 'Library sections' }).getByRole('button'),
  ).toHaveText(['Added', 'Classics']);
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
  await page.getByRole('button', { name: /Let the words come to you/ }).click();
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
