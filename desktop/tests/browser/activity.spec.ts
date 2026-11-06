import { expect, test, type Page } from '@playwright/test';
import { defaultSettings, type ActivityReport } from '../../src/types';

async function activityShell(page: Page, initialPosition = 0) {
  await page.addInitScript(
    ({ prefs, initialPosition }) => {
      const book = {
        id: 'book-1',
        title: 'The reading room',
        author: 'Vern',
        format: 'TXT',
        chunks: ['A quiet page.', 'A second page.'],
        position: initialPosition,
        favorite: false,
        updatedAt: Date.now(),
      };
      const today = new Date().toLocaleDateString('en-CA');
      const state = {
        prefs,
        books: [
          book,
          {
            ...book,
            id: 'book-2',
            position: 0,
            title: 'Another story',
            chunks: ['A different book opens here.'],
          },
        ],
        events: [] as unknown[],
        exports: 0,
        fail: false,
        legacyDays: [] as NonNullable<ActivityReport['legacyDays']>,
        legacyBookTimes: [] as NonNullable<ActivityReport['legacyBookTimes']>,
        sessions: [
          {
            id: 'session-1',
            bookId: book.id,
            title: book.title,
            mode: 'reading',
            startedAt: Date.now() - 3_600_000,
            updatedAt: Date.now() - 2_700_000,
            startPosition: 0,
            endPosition: 1,
            measuredMs: 900_000,
            correctedMs: null as number | null,
            excluded: false,
            manual: false,
            days: [{ date: today, milliseconds: 900_000 }],
          },
        ],
      };
      Reflect.set(window, '__activity', state);
      Reflect.set(window, 'isTauri', true);
      Reflect.set(window, '__TAURI_INTERNALS__', {
        metadata: { currentWindow: { label: 'main' }, currentWebview: { label: 'main' } },
        invoke: async (command: string, args: Record<string, unknown> = {}) => {
          if (command === 'list_books') return structuredClone(state.books);
          if (command === 'record_reading_open') return;
          if (command === 'get_settings') return state.prefs;
          if (command === 'save_settings') {
            state.prefs = args.settings as typeof prefs;
            return;
          }
          if (command === 'record_reading_activity') {
            state.events.push(args.event);
            return;
          }
          if (command === 'get_reading_activity') {
            if (state.fail) throw new Error('Could not load reading activity. Try again.');
            const ms = state.sessions.reduce(
              (ms, s) => ms + (s.excluded ? 0 : (s.correctedMs ?? s.measuredMs)),
              0,
            );
            return {
              today,
              days: [
                {
                  date: today,
                  readingMs: ms,
                  listeningMs: 0,
                  legacyMs: state.legacyDays.find((d) => d.date === today)?.readingMs ?? 0,
                },
                ...state.legacyDays
                  .filter((d) => d.date !== today)
                  .map((d) => ({
                    date: d.date,
                    readingMs: 0,
                    listeningMs: 0,
                    legacyMs: d.readingMs,
                  })),
              ],
              legacyDays: state.legacyDays,
              legacyBookTimes: state.legacyBookTimes,
              books: ms
                ? [
                    {
                      bookId: book.id,
                      title: book.title,
                      readingMs: ms,
                      listeningMs: 0,
                      sessions: state.sessions.length,
                    },
                  ]
                : [],
              sessions: state.sessions,
              totalSessions: state.sessions.length,
              currentStreak: ms ? 1 : 0,
              bestStreak: ms ? 1 : 0,
            };
          }
          if (command === 'correct_reading_session') {
            const session = state.sessions.find((s) => s.id === args.id)!;
            session.correctedMs = args.correctedMs as number | null;
            session.excluded = args.excluded as boolean;
            return;
          }
          if (command === 'add_reading_session') {
            state.sessions.push({
              ...state.sessions[0],
              id: 'manual-1',
              measuredMs: args.durationMs as number,
              correctedMs: null,
              excluded: false,
              manual: true,
            });
            return;
          }
          if (command === 'export_reading_sessions') {
            state.exports++;
            return true;
          }
          if (command === 'get_source_info') return null;
          if (command === 'save_progress') {
            state.books.find((item) => item.id === args.id)!.position = args.position as number;
            return;
          }
          if (command === 'delete_book') {
            state.books = state.books.filter((item) => item.id !== args.id);
            state.prefs.queue = state.prefs.queue.filter((id) => id !== args.id);
            if (state.prefs.lastBook === args.id) state.prefs.lastBook = '';
            return;
          }
          if (command === 'toggle_favorite') {
            const target = state.books.find((item) => item.id === args.id)!;
            target.favorite = !target.favorite;
            return;
          }
          if (command === 'update_book') {
            Object.assign(
              state.books.find((item) => item.id === args.id)!,
              { title: args.title, author: args.author, collection: args.collection },
            );
            return;
          }
          if (command === 'cancel_speech') return;
          if (command.startsWith('list_')) return [];
          throw new Error(`Unexpected command: ${command}`);
        },
      });
    },
    { prefs: defaultSettings, initialPosition },
  );
  await page.goto('/');
  await page.getByRole('button', { name: 'Reading insights' }).click();
  await expect(page.getByRole('heading', { name: 'Reading insights', exact: true })).toBeVisible();
}

test('legacy activity keeps unknown durations and undated book totals distinct from sessions', async ({
  page,
}) => {
  await activityShell(page, 1);
  await page.evaluate(() => {
    const state = Reflect.get(window, '__activity');
    const today = state.sessions[0].days[0].date;
    const yesterday = new Date(`${today}T12:00:00`);
    yesterday.setDate(yesterday.getDate() - 1);
    state.legacyDays = [
      { date: today, appOpenCount: 1, usageMs: 1800000, readingMs: 300000, recordedBooks: 1 },
      {
        date: yesterday.toLocaleDateString('en-CA'),
        appOpenCount: 1,
        usageMs: 1800000,
        readingMs: 0,
        recordedBooks: 2,
      },
      { date: '2020-02-29', appOpenCount: 1, usageMs: 900000, readingMs: 0, recordedBooks: 0 },
    ];
    state.legacyBookTimes = [
      {
        id: 'old-1',
        bookId: 'book-1',
        title: 'The reading room',
        milliseconds: 900000,
        snapshotAt: 1000,
      },
      {
        id: 'old-2',
        bookId: null,
        title: 'Removed Android reading',
        milliseconds: 120000,
        snapshotAt: 0,
      },
    ];
  });
  await page.getByRole('button', { name: 'Refresh activity', exact: true }).click();
  await expect(page.locator('.insights-totals').getByText('20 min', { exact: true })).toBeVisible();
  await expect(page.locator('.insights-totals')).toContainText('5 min legacy');
  const legacy = page.getByRole('region', { name: 'Older Android activity', exact: true });
  await expect(legacy.locator('.legacy-day-list > li')).toHaveCount(3);
  await expect(legacy.getByText('Reading recorded', { exact: true })).toBeVisible();
  await expect(
    legacy.getByText('2 books recorded · Duration unavailable', { exact: true }),
  ).toBeVisible();
  await expect(legacy).toContainText('does not record their month');
  await page.getByLabel('Minutes per day').selectOption('30');
  await expect(page.getByRole('progressbar', { name: 'Daily reading goal' })).toHaveAttribute(
    'value',
    '1200000',
  );
  for (const width of [1440, 900, 390]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(width);
    await page.screenshot({
      path: `.artifacts/msi-validation-20261102/legacy-activity-${width}.png`,
      fullPage: true,
    });
    await legacy.screenshot({
      path: `.artifacts/msi-validation-20261102/legacy-activity-section-${width}.png`,
    });
  }
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByLabel('Filter legacy dates').fill('2020-02');
  await expect(legacy.locator('.legacy-day-list > li')).toHaveCount(1);
  await expect(legacy.locator('.legacy-day-list')).toContainText('No reading recorded');
  await page.getByLabel('Filter legacy dates').fill('no match');
  await expect(legacy.getByText('No legacy activity matches this date.')).toBeVisible();
  await page.getByLabel('Filter legacy dates').fill('');
  await page.getByLabel('Search legacy book totals').fill('Removed');
  await expect(legacy.locator('.activity-book-list > li')).toHaveCount(1);
  await expect(legacy.getByRole('button', { name: 'Open book', exact: true })).toHaveCount(0);
  await page.getByLabel('Search legacy book totals').fill('');
  await page
    .getByRole('button', { name: 'Edit session for The reading room', exact: true })
    .click();
  await page.getByLabel('Exclude this session from totals').check();
  await page.getByRole('button', { name: 'Save session', exact: true }).click();
  await expect(page.locator('.insights-totals').getByText('5 min', { exact: true })).toBeVisible();
  await expect(page.getByRole('progressbar', { name: 'Daily reading goal' })).toHaveAttribute(
    'value',
    '300000',
  );
  expect(await page.evaluate(() => Reflect.get(window, '__activity').sessions.length)).toBe(1);
  await legacy.getByRole('button', { name: 'Open book', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Reading room', exact: true })).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, '__activity').books[0].position)).toBe(1);
});

test('insights saves goals, corrects/excludes sessions, adds manual activity and exports history', async ({
  page,
}) => {
  test.setTimeout(60_000);
  await activityShell(page);
  await expect(page.locator('.insights-totals').getByText('15 min', { exact: true })).toBeVisible();
  await page.getByLabel('Minutes per day').selectOption('30');
  await expect(page.getByRole('progressbar', { name: 'Daily reading goal' })).toHaveAttribute(
    'value',
    '900000',
  );
  await page.getByLabel('Pause reading after inactivity').selectOption('60');
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__activity').prefs.readingIdleSeconds))
    .toBe(60);
  await page.getByRole('button', { name: 'Edit session for The reading room' }).click();
  await page.getByLabel('Duration in minutes').fill('10');
  await page.getByRole('button', { name: 'Save session', exact: true }).click();
  await expect(page.locator('.insights-totals').getByText('10 min', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Edit session for The reading room' }).click();
  await page.getByLabel('Exclude this session from totals').check();
  await page.getByRole('button', { name: 'Save session', exact: true }).click();
  await expect(page.locator('.insights-totals').getByText('0 min', { exact: true })).toBeVisible();
  await expect(page.getByText('Excluded from totals', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Edit session for The reading room' }).click();
  await page.getByLabel('Exclude this session from totals').uncheck();
  await page.getByRole('button', { name: 'Use measured time' }).click();
  await expect(page.locator('.insights-totals').getByText('15 min', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Add manual session', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Duration in minutes').fill('5');
  await page.getByRole('button', { name: 'Add session', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.locator('.insights-totals').getByText('20 min', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Export history CSV' }).click();
  await expect(page.getByRole('status')).toHaveText('Reading history saved as CSV.');
  await page.getByLabel('Search book titles').fill('nothing like this');
  await expect(page.getByText('No sessions match your filters.')).toBeVisible();
  await page.getByLabel('Search book titles').fill('');
  await page.getByRole('button', { name: 'Open passage' }).first().click();
  await expect
    .poll(() =>
      page.evaluate(() =>
        Reflect.get(window, '__activity').events.some(
          (e: { reader?: { bookId: string; position: number } }) =>
            e.reader?.bookId === 'book-1' && e.reader?.position === 1,
        ),
      ),
    )
    .toBe(true);
  await page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('button', { name: 'Home', exact: true })
    .click();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__activity').events.at(-1).reader))
    .toBe(null);
});

test('imported history opens the current book and preserves long measured sessions when excluded', async ({
  page,
}) => {
  await activityShell(page, 1);
  await page.evaluate(() => {
    const state = Reflect.get(window, '__activity');
    const session = state.sessions[0];
    session.startedAt = Date.now() - 172_800_000;
    session.updatedAt = Date.now() - 86_400_000;
    session.measuredMs = 90_000_000;
    session.startPosition = null;
    session.endPosition = null;
    session.androidSource = { record: { revision: 'cached.txt:old-revision', startLocation: 900 } };
  });
  await page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('button', { name: 'Home', exact: true })
    .click();
  await page.getByRole('button', { name: 'Reading insights', exact: true }).click();
  await page.getByRole('button', { name: 'Edit session for The reading room' }).click();
  const editor = page.getByRole('dialog', { name: 'Edit reading session' });
  await expect(editor.getByLabel('Keep measured duration')).toBeChecked();
  await expect(editor.getByLabel('Duration in minutes')).toBeDisabled();
  await editor.getByLabel('Exclude this session from totals').check();
  await editor.getByRole('button', { name: 'Save session', exact: true }).click();
  await expect(editor).toHaveCount(0);
  expect(await page.evaluate(() => Reflect.get(window, '__activity').sessions[0])).toMatchObject({
    measuredMs: 90_000_000,
    correctedMs: null,
    excluded: true,
    startPosition: null,
    endPosition: null,
  });
  const history = page.locator('.activity-session-list');
  await expect(history.getByRole('button', { name: 'Open passage', exact: true })).toHaveCount(0);
  await history.getByRole('button', { name: 'Open book', exact: true }).click();
  await expect
    .poll(() =>
      page.evaluate(() =>
        Reflect.get(window, '__activity').events.some(
          (event: { reader?: { bookId: string; position: number } }) =>
            event.reader?.bookId === 'book-1' && event.reader?.position === 1,
        ),
      ),
    )
    .toBe(true);
});

test('activity errors retain history, offer retry, and layouts fit light/dark desktop and narrow screens', async ({
  page,
}) => {
  await activityShell(page);
  await page.evaluate(() => {
    Reflect.get(window, '__activity').fail = true;
  });
  await page.getByRole('button', { name: 'Refresh activity' }).click();
  await expect(page.getByRole('alert')).toContainText('Could not load reading activity');
  await expect(page.locator('.insights-totals').getByText('15 min', { exact: true })).toBeVisible();
  await page.evaluate(() => {
    Reflect.get(window, '__activity').fail = false;
  });
  await page.getByRole('button', { name: 'Retry activity' }).click();
  await expect(page.getByRole('alert')).not.toBeVisible();
  for (const width of [1440, 900, 768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await expect(page.locator('.insights-page')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: `.artifacts/insights-${width}.png`, fullPage: true });
  }
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await page.getByRole('button', { name: 'Dark', exact: true }).click();
  await page.getByRole('button', { name: 'Close settings', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.screenshot({ path: '.artifacts/insights-dark.png', fullPage: true });
});

test('back and forward restore workspace filters, scroll and reader context; Settings returns to its entry', async ({
  page,
}) => {
  test.setTimeout(90_000);
  await activityShell(page);
  await page.getByLabel('Search book titles').fill('reading');
  await page.getByRole('combobox', { name: 'Session type', exact: true }).selectOption('reading');
  await page.locator('main').evaluate((main) => {
    main.scrollTop = 250;
  });
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await page.getByRole('button', { name: 'Close settings', exact: true }).click();
  await expect(page.getByLabel('Search book titles')).toHaveValue('reading');
  await expect(page.getByRole('combobox', { name: 'Session type', exact: true })).toHaveValue(
    'reading',
  );
  await expect
    .poll(() => page.locator('main').evaluate((main) => main.scrollTop))
    .toBeGreaterThan(200);
  await page.getByRole('button', { name: 'Go back', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Reading insights', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Go back', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Go forward', exact: true }).click();
  await expect(page.getByLabel('Search book titles')).toHaveValue('reading');
  await page.keyboard.press('Alt+ArrowLeft');
  await expect(page.getByRole('button', { name: 'Reading insights', exact: true })).toBeVisible();
  await page.keyboard.press('Alt+ArrowRight');
  await expect(page.getByRole('heading', { name: 'Reading insights', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Add manual session', exact: true }).click();
  await page.keyboard.press('Alt+ArrowLeft');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('heading', { name: 'Reading insights', exact: true })).toBeVisible();
  await page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('button', { name: 'Library', exact: true })
    .click();
  await page.getByLabel('Search library').fill('reading room');
  await page.locator('.book-card').first().getByRole('button').first().click();
  await expect(page).toHaveURL(/#reader\?book=book-1$/);
  await expect(page.locator('.flow-page-body')).toContainText('A quiet page.');
  await page.getByRole('button', { name: 'Go back', exact: true }).click();
  await expect(page.getByLabel('Search library')).toHaveValue('reading room');
  await page.keyboard.press('Control+f');
  await expect(page.getByLabel('Search library')).toBeFocused();
  await page.goForward();
  await expect(page.locator('.flow-page-body')).toContainText('A quiet page.');
  await page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('button', { name: 'Notes', exact: true })
    .click();
  await page.getByPlaceholder('Search your ideas…').fill('a thought to keep');
  await page.getByRole('button', { name: 'Settings', exact: true }).click();
  await page.getByRole('button', { name: 'Close settings', exact: true }).click();
  await expect(page.getByPlaceholder('Search your ideas…')).toHaveValue('a thought to keep');
  await expect(page.getByRole('button', { name: 'Go forward', exact: true })).toBeEnabled();
});

test('reader history restores the right book and handles a book removed from the library', async ({
  page,
}) => {
  test.setTimeout(90_000);
  await activityShell(page);
  const library = () =>
    page
      .getByRole('navigation', { name: 'Main navigation' })
      .getByRole('button', { name: 'Library', exact: true })
      .click();
  const back = () => page.getByRole('button', { name: 'Go back', exact: true }).click();
  await library();
  await page.getByRole('button', { name: 'Open The reading room', exact: true }).click();
  await expect(page.locator('.flow-page-body')).toContainText('A quiet page.');
  await page.getByRole('button', { name: 'Toggle notebook', exact: true }).click();
  await page.getByLabel('Leave a thought').fill('A draft for the first book.');
  await library();
  await page.getByRole('button', { name: 'Open Another story', exact: true }).click();
  await expect(page.locator('.flow-page-body')).toContainText('A different book opens here.');
  await expect(page.getByLabel('Leave a thought')).toHaveValue('');
  await page.getByLabel('Leave a thought').fill('A separate draft for the second book.');
  await back();
  await expect(page.getByLabel('Search library')).toBeVisible();
  await back();
  await expect(page).toHaveURL(/#reader\?book=book-1$/);
  await expect(page.locator('.flow-page-body')).toContainText('A quiet page.');
  await expect(page.getByLabel('Leave a thought')).toHaveValue('A draft for the first book.');
  await page.getByRole('button', { name: 'Go forward', exact: true }).click();
  await expect(page.getByLabel('Search library')).toBeVisible();
  await page.getByRole('button', { name: 'Go forward', exact: true }).click();
  await expect(page).toHaveURL(/#reader\?book=book-2$/);
  await expect(page.locator('.flow-page-body')).toContainText('A different book opens here.');
  await expect(page.getByLabel('Leave a thought')).toHaveValue(
    'A separate draft for the second book.',
  );
  await library();
  await page.getByRole('button', { name: 'Manage The reading room', exact: true }).click();
  await page.getByRole('button', { name: 'Remove from library…', exact: true }).click();
  await page.getByRole('button', { name: 'Remove book and linked items', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await back();
  await expect(page).toHaveURL(/#reader\?book=book-2$/);
  await back();
  await expect(page.getByLabel('Search library')).toBeVisible();
  await back();
  await expect(page).toHaveURL(/#library$/);
  await expect(page.getByRole('status')).toContainText('That book is no longer in your library.');
  await expect(page.getByRole('heading', { name: /^Library/ })).toBeVisible();
});

test('library options confirm individual deletion and keep finished books last under every sort', async ({
  page,
}) => {
  await activityShell(page);
  await page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('button', { name: 'Library', exact: true })
    .click();
  await expect(page.locator('.book-card input[type="checkbox"]')).toHaveCount(0);
  for (const sort of ['recent', 'title', 'author', 'progress']) {
    await page.getByLabel('Sort by', { exact: true }).selectOption(sort);
    await expect(page.locator('.book-card h3')).toHaveText(['The reading room', 'Another story']);
  }
  await page.getByLabel('Read status', { exact: true }).selectOption('finished');
  await expect(page.locator('.book-card h3')).toHaveText(['Another story']);
  await page.getByLabel('Read status', { exact: true }).selectOption('all');
  await page.screenshot({ path: '.artifacts/library-options-desktop.png' });
  await page.getByLabel('Options for Another story', { exact: true }).click();
  await page.screenshot({ path: '.artifacts/library-menu-desktop.png' });
  await page.getByRole('button', { name: 'Delete book…', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Another story');
  await page.getByRole('button', { name: 'Keep books', exact: true }).click();
  await expect(page.locator('.book-card')).toHaveCount(2);
  await page.getByLabel('Options for Another story', { exact: true }).click();
  await page.getByRole('button', { name: 'Delete book…', exact: true }).click();
  await page.getByRole('button', { name: 'Delete from library', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.locator('.book-card h3')).toHaveText(['The reading room']);
  expect(
    await page.evaluate(() =>
      Reflect.get(window, '__activity').books.map((book: { id: string }) => book.id),
    ),
  ).toEqual(['book-1']);
});

test('library selection wires favorites, queue, collection moves and batch deletion', async ({
  page,
}) => {
  await activityShell(page);
  await page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('button', { name: 'Library', exact: true })
    .click();
  await expect(page.getByRole('region', { name: 'Book selection' })).not.toBeVisible();
  await page
    .getByRole('button', { name: 'Open The reading room', exact: true })
    .click({ button: 'right' });
  await expect(page.getByRole('region', { name: 'Book selection' })).toContainText('1 selected');
  await expect(
    page.getByRole('checkbox', { name: 'Select The reading room', exact: true }),
  ).toBeChecked();
  await page.getByRole('button', { name: 'Cancel selection', exact: true }).click();
  await expect(page.locator('.book-card input[type="checkbox"]')).toHaveCount(0);
  await page
    .getByRole('button', { name: 'Open Another story', exact: true })
    .click({ button: 'right' });
  await page.getByRole('button', { name: 'Select all shown', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Book selection' })).toContainText('2 selected');
  await page.setViewportSize({ width: 900, height: 650 });
  await page.screenshot({ path: '.artifacts/library-selection-900.png' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Add to favorites', exact: true }).click();
  await expect
    .poll(() =>
      page.evaluate(() =>
        Reflect.get(window, '__activity').books.every(
          (book: { favorite: boolean }) => book.favorite,
        ),
      ),
    )
    .toBe(true);
  await page.getByRole('button', { name: 'Add to queue', exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__activity').prefs.queue))
    .toEqual(['book-1', 'book-2']);
  await page.getByRole('button', { name: 'Move to collection', exact: true }).click();
  await page.getByLabel('Collection name', { exact: true }).fill('Weekend reading');
  await page.getByRole('button', { name: 'Move books', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.locator('.collection-label')).toHaveText([
    'Weekend reading',
    'Weekend reading',
  ]);
  await page.getByRole('button', { name: 'Delete selected', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Delete 2 books?');
  await page.getByRole('button', { name: 'Delete from library', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('heading', { name: 'Your shelf starts here' })).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, '__activity').prefs.queue)).toEqual([]);
  await expect(page.getByRole('contentinfo', { name: 'Playback controls' })).not.toBeVisible();
});
