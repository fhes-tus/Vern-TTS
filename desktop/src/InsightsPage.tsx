import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Notice } from './Notice';
import { api, native } from './api';
import { Modal } from './WorkspacePages';
import './insights.css';
import type { ActivityReport, Book, ReadingSession, Settings } from './types';

const number = new Intl.NumberFormat();
function duration(ms: number) {
  if (ms === 0) return '0 min';
  if (ms < 60_000) return `${number.format(Math.floor(ms / 1000))} sec`;
  const minutes = Math.floor(ms / 60_000);
  return minutes < 60
    ? `${number.format(minutes)} min`
    : `${number.format(Math.floor(minutes / 60))} hr ${number.format(minutes % 60)} min`;
}
function dateLabel(
  value: string,
  options: Intl.DateTimeFormatOptions = { month: 'short', day: 'numeric' },
) {
  return new Intl.DateTimeFormat(undefined, options).format(new Date(`${value}T12:00:00`));
}
function dateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
function localInput(time: number) {
  const date = new Date(time);
  return `${dateKey(date)}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}
const activeMs = (s: ReadingSession) => (s.excluded ? 0 : (s.correctedMs ?? s.measuredMs));

export function InsightsPage({
  books,
  settings,
  onPreferences,
  onOpen,
}: {
  books: Book[];
  settings: Settings;
  onPreferences: (patch: Partial<Settings>) => Promise<boolean>;
  onOpen: (book: Book, position?: number) => void;
}) {
  const [report, setReport] = useState<ActivityReport | null>(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState('all');
  const [query, setQuery] = useState('');
  const [visible, setVisible] = useState(20);
  const [edit, setEdit] = useState<ReadingSession | null>(null);
  const [manual, setManual] = useState(false);
  const load = useCallback(async () => {
    try {
      const data = await api.activity();
      setReport(data);
      setError('');
    } catch (e) {
      setError(String(e).replace(/^Error: /, ''));
    }
  }, []);
  useEffect(() => {
    let live = true;
    const refresh = () =>
      api
        .activity()
        .then((data) => {
          if (live) {
            setReport(data);
            setError('');
          }
        })
        .catch((e: unknown) => {
          if (live) setError(String(e));
        });
    void refresh();
    const interval = setInterval(refresh, 10_000);
    return () => {
      live = false;
      clearInterval(interval);
    };
  }, []);
  async function preference(patch: Partial<Settings>) {
    setBusy(true);
    try {
      if (await onPreferences(patch)) setMessage('Reading preferences saved.');
    } finally {
      setBusy(false);
    }
  }
  const today = report?.days.find((d) => d.date === report.today);
  const todayMs = (today?.readingMs ?? 0) + (today?.listeningMs ?? 0) + (today?.legacyMs ?? 0);
  const week = Array.from({ length: 7 }, (_, i) => {
    const date = new Date(`${report?.today ?? dateKey(new Date())}T12:00:00`);
    date.setDate(date.getDate() - 6 + i);
    const key = dateKey(date);
    const day = report?.days.find((d) => d.date === key);
    return {
      date: key,
      reading: day?.readingMs ?? 0,
      listening: day?.listeningMs ?? 0,
      legacy: day?.legacyMs ?? 0,
    };
  });
  const weekMax = Math.max(60_000, ...week.map((d) => d.reading + d.listening + d.legacy));
  const weekTotal = week.reduce((ms, d) => ms + d.reading + d.listening + d.legacy, 0);
  const sessions = (report?.sessions ?? []).filter(
    (s) =>
      (mode === 'all' || s.mode === mode) &&
      s.title.toLocaleLowerCase().includes(query.toLocaleLowerCase()),
  );
  return (
    <div className="page insights-page">
      <div className="insights-title">
        <div>
          <h1>Reading insights</h1>
        </div>
        <button className="secondary" onClick={() => void load()}>
          Refresh activity
        </button>
      </div>
      {error && (
        <div className="insights-error" role="alert">
          <p>{error}</p>
          <button className="text-button" onClick={() => void load()}>
            Retry activity
          </button>
          <button className="text-button" onClick={() => setError('')}>
            Dismiss error
          </button>
        </div>
      )}
      {message && <Notice onDismiss={() => setMessage('')}>{message}</Notice>}
      {!report ? (
        <p role="status">Loading reading activity…</p>
      ) : (
        <>
          <dl className="insights-totals">
            <div>
              <dt>Today</dt>
              <dd>{duration(todayMs)}</dd>
              <p>
                {duration(today?.readingMs ?? 0)} reading · {duration(today?.listeningMs ?? 0)}{' '}
                listening
                {!!today?.legacyMs && <> · {duration(today.legacyMs)} legacy</>}
              </p>
              {!!today?.legacyBookCount && todayMs === 0 && (
                <p>Reading recorded; duration unavailable.</p>
              )}
            </div>
            <div>
              <dt>Current streak</dt>
              <dd>
                {number.format(report.currentStreak)}{' '}
                <small>{report.currentStreak === 1 ? 'day' : 'days'}</small>
              </dd>
              <p>Any recorded reading or listening</p>
            </div>
            <div>
              <dt>Longest streak</dt>
              <dd>
                {number.format(report.bestStreak)}{' '}
                <small>{report.bestStreak === 1 ? 'day' : 'days'}</small>
              </dd>
              <p>Across your saved history</p>
            </div>
            <div>
              <dt>Saved sessions</dt>
              <dd>{number.format(report.totalSessions)}</dd>
              <p>Including corrections and exclusions</p>
            </div>
          </dl>
          <div className="insights-grid">
            <section className="insights-panel insights-week" aria-labelledby="week-title">
              <div className="insights-section-title">
                <h2 id="week-title">Your last 7 days</h2>
                <span>{duration(weekTotal)}</span>
              </div>
              <p className="insights-period">
                {dateLabel(week[0].date)} – {dateLabel(week[6].date)}
              </p>
              <ol
                className={`activity-bars${weekTotal === 0 ? ' activity-bars-empty' : ''}`}
                aria-label="Daily reading and listening time"
              >
                {week.map((day) => (
                  <li
                    key={day.date}
                    aria-label={`${dateLabel(day.date, { weekday: 'long', month: 'short', day: 'numeric' })}: ${duration(day.reading)} reading, ${duration(day.listening)} listening${day.legacy ? `, ${duration(day.legacy)} legacy` : ''}`}
                  >
                    <span className="activity-bar-value">
                      {duration(day.reading + day.listening + day.legacy)}
                    </span>
                    <div className="activity-bar-track" aria-hidden="true">
                      <span
                        className="activity-bar-reading"
                        style={{ height: `${(day.reading / weekMax) * 100}%` }}
                      />
                      <span
                        className="activity-bar-listening"
                        style={{ height: `${(day.listening / weekMax) * 100}%` }}
                      />
                      <span
                        className="activity-bar-legacy"
                        style={{ height: `${(day.legacy / weekMax) * 100}%` }}
                      />
                    </div>
                    <span>{dateLabel(day.date, { weekday: 'short' })}</span>
                  </li>
                ))}
              </ol>
              <div className="activity-legend">
                <span>
                  <i className="reading-swatch" />
                  Reading
                </span>
                <span>
                  <i className="listening-swatch" />
                  Listening
                </span>
                {week.some((d) => d.legacy > 0) && (
                  <span>
                    <i className="legacy-swatch" />
                    Legacy
                  </span>
                )}
              </div>
            </section>
            <section className="insights-panel insights-goal" aria-labelledby="goal-title">
              <h2 id="goal-title">Daily goal</h2>
              <label htmlFor="reading-goal">Minutes per day</label>
              <select
                id="reading-goal"
                name="reading-goal"
                value={settings.dailyGoalMinutes}
                disabled={!native || busy}
                onChange={(e) => void preference({ dailyGoalMinutes: Number(e.target.value) })}
              >
                {[...new Set([0, 15, 30, 45, 60, 90, 120, 180, settings.dailyGoalMinutes])]
                  .sort((a, b) => a - b)
                  .map((n) => (
                    <option key={n} value={n}>
                      {n === 0 ? 'Goal off' : `${number.format(n)} minutes`}
                    </option>
                  ))}
              </select>
              {settings.dailyGoalMinutes > 0 ? (
                <>
                  <progress
                    aria-label="Daily reading goal"
                    value={Math.min(todayMs, settings.dailyGoalMinutes * 60_000)}
                    max={settings.dailyGoalMinutes * 60_000}
                  />
                  <p>
                    {todayMs >= settings.dailyGoalMinutes * 60_000
                      ? 'Your goal is met today.'
                      : `${duration(settings.dailyGoalMinutes * 60_000 - todayMs)} to your goal.`}
                  </p>
                </>
              ) : (
                <p>Set a goal when you want one. Reading and listening both count.</p>
              )}
              <label htmlFor="reading-idle">Pause reading after inactivity</label>
              <select
                id="reading-idle"
                name="reading-idle"
                value={settings.readingIdleSeconds}
                disabled={!native || busy}
                onChange={(e) => void preference({ readingIdleSeconds: Number(e.target.value) })}
              >
                {[...new Set([30, 60, 120, 300, 600, 900, settings.readingIdleSeconds])]
                  .sort((a, b) => a - b)
                  .map((n) => (
                    <option key={n} value={n}>
                      {duration(n * 1000)}
                    </option>
                  ))}
              </select>
            </section>
            <section
              className="insights-panel insights-books"
              aria-labelledby="activity-books-title"
            >
              <h2 id="activity-books-title">Time by book</h2>
              {!report.books.length ? (
                <p>Open a book to start your first reading session.</p>
              ) : (
                <ul className="activity-book-list">
                  {report.books.slice(0, 5).map((item, index) => {
                    const book = books.find((b) => b.id === item.bookId);
                    return (
                      <li key={item.bookId ?? `removed-${index}`}>
                        <div>
                          <strong>{item.title}</strong>
                          <small>
                            {duration(item.readingMs)} reading · {duration(item.listeningMs)}{' '}
                            listening{!book ? ' · Removed from library' : ''}
                          </small>
                        </div>
                        <span>{duration(item.readingMs + item.listeningMs)}</span>
                        {book && (
                          <button className="text-button" onClick={() => onOpen(book)}>
                            Read book
                          </button>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
              {report.books.length > 5 && (
                <p className="insights-period">
                  Your 5 most read books, across all saved sessions.
                </p>
              )}
            </section>
            <section
              className="insights-panel insights-about"
              aria-labelledby="activity-about-title"
            >
              <h2 id="activity-about-title">What counts</h2>
              <p>
                Reading counts while the reader is visible and this window has focus. Keys, clicks
                and page gestures renew the inactivity timer.
              </p>
              <p>
                Listening counts while audio plays. Voice preparation and pauses add no listening
                time.
              </p>
              <p>
                These are activity estimates. Correct a session or exclude it from your totals at
                any time.
              </p>
            </section>
          </div>
          <LegacyActivity report={report} books={books} onOpen={onOpen} />
          <section className="insights-history" aria-labelledby="history-title">
            <div className="insights-section-title">
              <div>
                <h2 id="history-title">Session history</h2>
                <p className="insights-period">
                  Latest {number.format(report.sessions.length)} of{' '}
                  {number.format(report.totalSessions)} saved sessions
                </p>
              </div>
              <div className="action-row">
                <button
                  className="secondary"
                  disabled={!native || !books.length}
                  onClick={() => setManual(true)}
                >
                  Add manual session
                </button>
                <button
                  className="secondary"
                  disabled={!native || busy || !report.totalSessions}
                  onClick={() => {
                    setBusy(true);
                    setError('');
                    void api
                      .exportSessions()
                      .then((saved) => {
                        if (saved) setMessage('Reading history saved as CSV.');
                      })
                      .catch((e: unknown) => setError(String(e)))
                      .finally(() => setBusy(false));
                  }}
                >
                  Export history CSV
                </button>
              </div>
            </div>
            <div className="activity-filters">
              <label>
                Search book titles
                <input
                  name="activity-search"
                  type="search"
                  autoComplete="off"
                  placeholder="Search your reading history…"
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value);
                    setVisible(20);
                  }}
                />
              </label>
              <label>
                Session type
                <select
                  name="activity-mode"
                  value={mode}
                  onChange={(e) => {
                    setMode(e.target.value);
                    setVisible(20);
                  }}
                >
                  <option value="all">Reading & listening</option>
                  <option value="reading">Reading</option>
                  <option value="listening">Listening</option>
                </select>
              </label>
            </div>
            {!sessions.length ? (
              <p className="activity-empty">
                {report.totalSessions
                  ? 'No sessions match your filters.'
                  : 'Your first session will appear after a few seconds with a book. You can also add reading you did elsewhere.'}
              </p>
            ) : (
              <ul className="activity-session-list">
                {sessions.slice(0, visible).map((s) => {
                  const book = books.find((b) => b.id === s.bookId);
                  return (
                    <li key={s.id} className={s.excluded ? 'session-excluded' : undefined}>
                      <div>
                        <strong>{s.title}</strong>
                        <small>
                          {new Intl.DateTimeFormat(undefined, {
                            dateStyle: 'medium',
                            timeStyle: 'short',
                          }).format(s.startedAt)}{' '}
                          · {s.mode === 'reading' ? 'Reading' : 'Listening'}
                          {s.manual ? ' · Manual' : ''}
                          {s.correctedMs !== null ? ' · Corrected' : ''}
                          {s.excluded ? ' · Excluded from totals' : ''}
                        </small>
                      </div>
                      <span className="session-duration">{duration(activeMs(s))}</span>
                      <div className="activity-row-actions">
                        {book && (
                          <button
                            className="text-button"
                            onClick={() => onOpen(book, s.endPosition ?? book.position)}
                          >
                            {s.endPosition === null ? 'Open book' : 'Open passage'}
                          </button>
                        )}
                        <button
                          className="text-button"
                          disabled={!native}
                          onClick={() => setEdit(s)}
                          aria-label={`Edit session for ${s.title}`}
                        >
                          Edit session
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
            {sessions.length > visible && (
              <button className="secondary activity-more" onClick={() => setVisible((n) => n + 20)}>
                Show more sessions
              </button>
            )}
          </section>
        </>
      )}
      {edit && <SessionEditor session={edit} onClose={() => setEdit(null)} onSaved={load} />}
      {manual && <ManualSession books={books} onClose={() => setManual(false)} onSaved={load} />}
    </div>
  );
}

function LegacyActivity({
  report,
  books,
  onOpen,
}: {
  report: ActivityReport;
  books: Book[];
  onOpen: (book: Book) => void;
}) {
  const [dateQuery, setDateQuery] = useState('');
  const [bookQuery, setBookQuery] = useState('');
  const [dayLimit, setDayLimit] = useState(20);
  const [bookLimit, setBookLimit] = useState(20);
  const days = (report.legacyDays ?? []).filter((d) => d.date.includes(dateQuery.trim()));
  const totals = (report.legacyBookTimes ?? []).filter((b) =>
    b.title.toLocaleLowerCase().includes(bookQuery.trim().toLocaleLowerCase()),
  );
  if (!report.legacyDays?.length && !report.legacyBookTimes?.length) return null;
  return (
    <section className="insights-legacy" aria-labelledby="legacy-title">
      <h2 id="legacy-title">Older Android activity</h2>
      <p>
        Legacy reading totals count towards dated activity and streaks. Reading markers with unknown
        duration preserve earned days. App use and opens add no reading minutes.
      </p>
      {!!report.legacyDays?.length && (
        <>
          <div className="workspace-filters">
            <label>
              Filter legacy dates
              <input
                type="search"
                placeholder="YYYY-MM or YYYY-MM-DD"
                value={dateQuery}
                onChange={(e) => {
                  setDateQuery(e.target.value);
                  setDayLimit(20);
                }}
              />
            </label>
            <span>
              {number.format(days.length)} of {number.format(report.legacyDays.length)} saved dates
            </span>
          </div>
          <ul className="legacy-day-list">
            {days.slice(0, dayLimit).map((d) => (
              <li key={d.date}>
                <time dateTime={d.date}>
                  {dateLabel(d.date, { year: 'numeric', month: 'short', day: 'numeric' })}
                </time>
                <div>
                  <strong>
                    {d.readingMs
                      ? `${duration(d.readingMs)} legacy reading`
                      : d.recordedBooks
                        ? 'Reading recorded'
                        : 'No reading recorded'}
                  </strong>
                  {d.recordedBooks > 0 && (
                    <span>
                      {number.format(d.recordedBooks)} {d.recordedBooks === 1 ? 'book' : 'books'}{' '}
                      recorded{d.readingMs === 0 ? ' · Duration unavailable' : ''}
                    </span>
                  )}
                  <span>
                    {duration(d.usageMs)} app use · {number.format(d.appOpenCount)}{' '}
                    {d.appOpenCount === 1 ? 'open' : 'opens'}
                  </span>
                </div>
              </li>
            ))}
          </ul>
          {!days.length && <p>No legacy activity matches this date.</p>}
          {days.length > dayLimit && (
            <button className="secondary" onClick={() => setDayLimit((n) => n + 20)}>
              Show more legacy dates
            </button>
          )}
        </>
      )}
      {!!report.legacyBookTimes?.length && (
        <div className="legacy-book-totals">
          <h3>Legacy book totals</h3>
          <p>
            The original backup does not record their month. These totals can overlap daily records
            and remain outside dated totals and timed sessions. Repeated imports keep the largest
            saved value per book.
          </p>
          <label className="legacy-book-search">
            Search legacy book totals
            <input
              type="search"
              value={bookQuery}
              onChange={(e) => {
                setBookQuery(e.target.value);
                setBookLimit(20);
              }}
            />
          </label>
          <ul className="activity-book-list">
            {totals.slice(0, bookLimit).map((item) => {
              const book = books.find((b) => b.id === item.bookId);
              return (
                <li key={item.id}>
                  <div>
                    <strong>{item.title}</strong>
                    <small>
                      Month not recorded{!book ? ' · Not in your library' : ''}
                      {item.snapshotAt
                        ? ` · Backup ${new Date(item.snapshotAt).toLocaleDateString()}`
                        : ''}
                    </small>
                  </div>
                  <span>{duration(item.milliseconds)}</span>
                  {book && (
                    <button className="text-button" onClick={() => onOpen(book)}>
                      Open book
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
          {!totals.length && <p>No legacy book totals match your search.</p>}
          {totals.length > bookLimit && (
            <button className="secondary" onClick={() => setBookLimit((n) => n + 20)}>
              Show more legacy book totals
            </button>
          )}
        </div>
      )}
    </section>
  );
}

function SessionEditor({
  session,
  onClose,
  onSaved,
}: {
  session: ReadingSession;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [minutes, setMinutes] = useState(
    String((session.correctedMs ?? session.measuredMs) / 60_000),
  );
  const [excluded, setExcluded] = useState(session.excluded);
  const [keepMeasured, setKeepMeasured] = useState(
    session.measuredMs > 86_400_000 && session.correctedMs === null,
  );
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function save(reset: boolean) {
    const value = Number(minutes);
    if (!reset && (!minutes.trim() || !Number.isFinite(value) || value < 0 || value > 1440)) {
      setError('Enter a duration between 0 and 1,440 minutes.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await api.correctSession(session.id, reset ? null : Math.round(value * 60_000), excluded);
      await onSaved();
      onClose();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title="Edit reading session"
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form
        className="workspace-form"
        onSubmit={(e) => {
          e.preventDefault();
          void save(keepMeasured);
        }}
      >
        <p>
          {session.title} · Measured: {duration(session.measuredMs)}
        </p>
        {session.measuredMs > 86_400_000 && (
          <label className="restore-choice">
            <input
              type="checkbox"
              checked={keepMeasured}
              onChange={(e) => setKeepMeasured(e.target.checked)}
            />
            Keep measured duration
          </label>
        )}
        <label>
          Duration in minutes
          <input
            name="session-duration"
            type="number"
            min="0"
            max="1440"
            step="any"
            required
            disabled={keepMeasured}
            autoComplete="off"
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
          />
        </label>
        <label className="restore-choice">
          <input
            name="session-excluded"
            type="checkbox"
            checked={excluded}
            onChange={(e) => setExcluded(e.target.checked)}
          />
          Exclude this session from totals
        </label>
        {error && <p role="alert">{error}</p>}
        <div className="action-row">
          <button className="primary" disabled={busy}>
            {busy ? 'Saving session…' : 'Save session'}
          </button>
          <button
            className="secondary"
            type="button"
            disabled={busy}
            onClick={() => void save(true)}
          >
            Use measured time
          </button>
        </div>
      </form>
    </Modal>
  );
}
function ManualSession({
  books,
  onClose,
  onSaved,
}: {
  books: Book[];
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [bookId, setBookId] = useState(books[0]?.id ?? '');
  const [mode, setMode] = useState<'reading' | 'listening'>('reading');
  const [start, setStart] = useState(() => localInput(Date.now() - 30 * 60_000));
  const [minutes, setMinutes] = useState('30');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault();
    const startedAt = new Date(start).getTime();
    const ms = Math.round(Number(minutes) * 60_000);
    if (
      !Number.isFinite(startedAt) ||
      !Number.isFinite(ms) ||
      ms <= 0 ||
      ms > 86_400_000 ||
      startedAt + ms > Date.now()
    ) {
      setError('Choose a past session of up to 24 hours that has already ended.');
      return;
    }
    const book = books.find((b) => b.id === bookId);
    if (!book) return;
    setBusy(true);
    setError('');
    try {
      await api.addSession({ bookId, position: book.position }, mode, startedAt, ms);
      await onSaved();
      onClose();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title="Add manual session"
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form className="workspace-form" onSubmit={(e) => void submit(e)}>
        <label>
          Book
          <select name="session-book" value={bookId} onChange={(e) => setBookId(e.target.value)}>
            {books.map((b) => (
              <option key={b.id} value={b.id}>
                {b.title}
              </option>
            ))}
          </select>
        </label>
        <label>
          Session type
          <select
            name="session-type"
            value={mode}
            onChange={(e) => setMode(e.target.value as typeof mode)}
          >
            <option value="reading">Reading</option>
            <option value="listening">Listening</option>
          </select>
        </label>
        <label>
          Started at (local time)
          <input
            name="session-start"
            type="datetime-local"
            required
            value={start}
            onChange={(e) => setStart(e.target.value)}
          />
        </label>
        <label>
          Duration in minutes
          <input
            name="session-minutes"
            type="number"
            min="0.01"
            max="1440"
            step="any"
            autoComplete="off"
            required
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
          />
        </label>
        <p className="muted">
          Manual sessions are labelled in history and count towards your goal and streak.
        </p>
        {error && <p role="alert">{error}</p>}
        <button className="primary" disabled={busy || !native}>
          {busy ? 'Saving session…' : 'Add session'}
        </button>
      </form>
    </Modal>
  );
}
