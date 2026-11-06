import { useEffect, useRef, useState } from 'react';
import { Notice } from './Notice';
import { api, native } from './api';
import { inspectBackup } from './backup-conversion';
import { Modal } from './WorkspacePages';
import type { AndroidImportSummary, BackupPreview, Book } from './types';

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
  const conversion = useRef<AbortController | null>(null);
  const [conversionProgress, setConversionProgress] = useState('');
  const [retained, setRetained] = useState<AndroidImportSummary[]>([]);
  const [retainedId, setRetainedId] = useState('');
  const [deletingRetained, setDeletingRetained] = useState('');
  useEffect(() => {
    let mounted = true;
    void api
      .androidImports()
      .then((items) => {
        if (mounted) setRetained(items);
      })
      .catch((e) => {
        if (mounted) setError(String(e));
      });
    return () => {
      mounted = false;
      conversion.current?.abort();
    };
  }, []);
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
      <h2>Backup and restore</h2>
      <p className="muted">
        Includes books and original files, notes and media, study data and settings. Voice models
        download separately. Android JSON and ZIP backups can also be imported; the preview lists
        any data still awaiting desktop support.
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
              const controller = new AbortController();
              conversion.current = controller;
              try {
                const next = await inspectBackup(controller.signal, (done, total) =>
                  setConversionProgress(`Converting Android readings: ${done} of ${total}`),
                );
                setRestoreSettings(false);
                setPreview(next);
              } finally {
                conversion.current = null;
                setConversionProgress('');
              }
            })
          }
        >
          Restore backup…
        </button>
      </div>
      {conversionProgress && (
        <div className="action-row">
          <p role="status">{conversionProgress}</p>
          <button className="secondary" onClick={() => conversion.current?.abort()}>
            Cancel conversion
          </button>
        </div>
      )}
      {retained.length > 0 && (
        <div className="workspace-filters">
          <label>
            Original Android backup
            <select
              disabled={busy}
              value={retainedId}
              onChange={(e) => setRetainedId(e.target.value)}
            >
              <option value="">Choose a retained backup</option>
              {retained.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.createdAt
                    ? new Date(item.createdAt).toLocaleDateString()
                    : 'Android backup'}{' '}
                  · {item.readings} books · {item.assets} files · {item.id.slice(0, 8)}
                </option>
              ))}
            </select>
          </label>
          <button
            className="secondary"
            disabled={busy || !retained.some((item) => item.id === retainedId)}
            onClick={() =>
              void run(async () => {
                if (await api.exportAndroidImport(retainedId))
                  setMessage('Original Android backup saved.');
              })
            }
          >
            Export original backup
          </button>
          <button
            className="text-button"
            disabled={busy || !retained.some((item) => item.id === retainedId)}
            onClick={() => setDeletingRetained(retainedId)}
          >
            Delete retained backup…
          </button>
        </div>
      )}
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
      {busy && !conversionProgress && <p role="status">Working…</p>}
      {message && <Notice onDismiss={() => setMessage('')}>{message}</Notice>}
      {error && (
        <Notice error onDismiss={() => setError('')}>
          {error}
        </Notice>
      )}
      {preview && (
        <Modal
          title={`Restore ${preview.source === 'Android' ? 'Android ' : ''}backup`}
          onClose={close}
        >
          <dl className="backup-counts">
            {(
              [
                ['Books', preview.books],
                ['Original files', preview.sourceFiles],
                ['Book notes & bookmarks', preview.annotations],
                ['Highlights', preview.highlights],
                ['Flashcards', preview.flashcards],
                ['Quizzes', preview.quizzes],
                ['Personal notes', preview.notes],
                ['Notebooks', preview.notebooks],
                ['Earlier note versions', preview.noteRevisions],
                ['Note attachments', preview.noteMedia],
                ['Reading lists', preview.readingLists],
                ['Reading sessions', preview.sessions],
                ['Recent books', preview.openedBooks],
                ['Legacy activity dates', preview.legacyDays],
                ['Legacy book totals', preview.legacyBookTimes],
                ['Vocabulary', preview.vocabulary],
                ['Pronunciation rules', preview.pronunciationRules],
              ] as const
            )
              .filter(([, count]) => !!count)
              .map(([label, count]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{count}</dd>
                </div>
              ))}
          </dl>
          {!!preview.retainedAndroidBackups && (
            <p>
              The original Android backup and included files are kept for re-export, including data
              still awaiting desktop support.
            </p>
          )}
          {preview.warnings?.map((warning, index) => (
            <p key={index} className="muted">
              {warning}
            </p>
          ))}
          <p>Adds new items. Existing items keep their content, position and review schedule.</p>
          <label className="restore-choice">
            <input
              type="checkbox"
              checked={restoreSettings}
              disabled={busy}
              onChange={(e) => setRestoreSettings(e.target.checked)}
            />{' '}
            Use backup reading and voice preferences
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
                    setRetained(await api.androidImports());
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
      {deletingRetained && (
        <Modal
          title="Delete retained Android backup"
          onClose={() => {
            if (!busy) setDeletingRetained('');
          }}
        >
          <p>
            Remove the original Android backup and its retained files from this library? Export it
            first if it contains data still awaiting desktop support. Imported books and notes
            remain.
          </p>
          <div className="action-row">
            <button
              className="danger"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await api.deleteAndroidImport(deletingRetained);
                  setRetained(await api.androidImports());
                  setRetainedId('');
                  setDeletingRetained('');
                  setMessage('Retained Android backup deleted.');
                })
              }
            >
              Delete retained backup
            </button>
            <button className="secondary" disabled={busy} onClick={() => setDeletingRetained('')}>
              Cancel
            </button>
          </div>
        </Modal>
      )}
    </section>
  );
}
