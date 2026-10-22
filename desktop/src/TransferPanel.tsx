import { useEffect, useState } from 'react';
import { api, native } from './api';
import { Modal } from './WorkspacePages';
import type { BackupPreview, Book } from './types';

export function TransferPanel({
  books,
  beforeAction,
  onRestored,
}: {
  books: Book[];
  beforeAction: () => Promise<void>;
  onRestored: () => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [preview, setPreview] = useState<BackupPreview | null>(null);
  const [restoreSettings, setRestoreSettings] = useState(false);
  const [bookId, setBookId] = useState('');
  useEffect(
    () => () => {
      if (preview) void api.discardBackup(preview.token).catch(() => {});
    },
    [preview],
  );
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setMessage('');
    setError('');
    try {
      await beforeAction();
      await action();
    } catch (e) {
      setError(String(e).replace(/^Error: /, ''));
    } finally {
      setBusy(false);
    }
  }
  function close() {
    if (busy || !preview) return;
    void api.discardBackup(preview.token).catch((e) => setError(String(e)));
    setPreview(null);
  }
  return (
    <section className="settings-card transfer-panel">
      <h2>Your library, kept safe</h2>
      <p className="muted">
        Desktop backups include personal notes and notebooks, books, collections, saved positions,
        book notes, bookmarks, flashcards, quizzes and preferences, plus vocabulary, pronunciation
        rules and the listening queue. Voice models and original source files are excluded. Android
        backup migration is still being built.
      </p>
      <div className="action-row">
        <button
          className="primary"
          disabled={busy || !native}
          onClick={() =>
            void run(async () => {
              if (await api.exportBackup()) setMessage('Desktop backup saved.');
            })
          }
        >
          Save desktop backup
        </button>
        <button
          className="secondary"
          disabled={busy || !native}
          onClick={() =>
            void run(async () => {
              const next = await api.inspectBackup();
              setRestoreSettings(false);
              setPreview(next);
            })
          }
        >
          Restore desktop backup…
        </button>
      </div>
      <h3>Export a book or its notes</h3>
      <div className="workspace-filters">
        <label>
          Book
          <select disabled={busy} value={bookId} onChange={(e) => setBookId(e.target.value)}>
            <option value="">Choose a book</option>
            {books.map((book) => (
              <option key={book.id} value={book.id}>
                {book.title}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="action-row">
        {(['text', 'notes'] as const).map((kind) => (
          <button
            key={kind}
            className="secondary"
            disabled={busy || !native || !books.some((b) => b.id === bookId)}
            onClick={() =>
              void run(async () => {
                if (await api.exportBook(bookId, kind))
                  setMessage(kind === 'text' ? 'Book text saved.' : 'Notes saved as Markdown.');
              })
            }
          >
            {kind === 'text' ? 'Export book text' : 'Export notes & bookmarks'}
          </button>
        ))}
      </div>
      {busy && <p role="status">Working…</p>}
      {message && <p role="status">{message}</p>}
      {error && <p role="alert">{error}</p>}
      {preview && (
        <Modal title="Restore desktop backup" onClose={close}>
          <p>
            This backup contains {preview.books} books, {preview.annotations} notes/bookmarks and{' '}
            {preview.flashcards} flashcards, {preview.quizzes ?? 0} quizzes, {preview.vocabulary}{' '}
            vocabulary entries and {preview.pronunciationRules} pronunciation rules, plus{' '}
            {preview.notes ?? 0} personal notes and {preview.notebooks ?? 0} notebooks (including
            Trash).
          </p>
          <p>
            New items will be added to your library. Existing items with the same identifiers keep
            their current content, reading position and review schedule.
          </p>
          <label className="restore-choice">
            <input
              type="checkbox"
              checked={restoreSettings}
              disabled={busy}
              onChange={(e) => setRestoreSettings(e.target.checked)}
            />{' '}
            Also use the backup’s reading and voice preferences
          </label>
          <div className="action-row">
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  const token = preview.token;
                  try {
                    await api.restoreBackup(token, restoreSettings);
                    await onRestored();
                    setMessage('Backup merged into your library.');
                  } finally {
                    setPreview(null);
                  }
                })
              }
            >
              Merge backup into library
            </button>
            <button className="secondary" disabled={busy} onClick={close}>
              Cancel
            </button>
          </div>
        </Modal>
      )}
    </section>
  );
}
