import { useCallback, useEffect, useState } from 'react';
import { api, native } from './api';
import { BookCover } from './BookCover';
import { Modal } from './WorkspacePages';
import { Notice } from './Notice';
import type { Book, OpenedBook } from './types';
import './reading-history.css';

export function ReadingHistoryPage({
  books,
  active,
  onOpen,
}: {
  books: Book[];
  active: boolean;
  onOpen: (book: Book) => void;
}) {
  const [entries, setEntries] = useState<OpenedBook[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const [removing, setRemoving] = useState<OpenedBook | 'all' | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const rows = await api.openedBooks();
      setEntries(rows);
      setError('');
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    if (!active) return;
    let live = true;
    void api
      .openedBooks()
      .then((rows) => {
        if (live) {
          setEntries(rows);
          setError('');
        }
      })
      .catch((e) => {
        if (live) setError(String(e));
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [active]);
  const visible = entries.filter((entry) =>
    `${books.find((book) => book.id === entry.bookId)?.title ?? entry.title} ${entry.format}`
      .toLowerCase()
      .includes(query.trim().toLowerCase()),
  );
  async function remove() {
    if (!removing) return;
    setBusy(true);
    setError('');
    try {
      if (removing === 'all') await api.clearOpenedBooks();
      else await api.removeOpenedBook(removing.id);
      await load();
      setRemoving(null);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="page reading-history-page">
      <div className="page-heading">
        <h1>Reading history</h1>
        <button
          className="secondary"
          disabled={!native || busy || !entries.length}
          onClick={() => setRemoving('all')}
        >
          Clear history…
        </button>
      </div>
      <div className="workspace-filters">
        <label>
          Search history
          <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <span className="muted">
          {loading ? '—' : `${visible.length} of ${entries.length} recent books`}
        </span>
      </div>
      {error && !removing && (
        <Notice error onDismiss={() => setError('')}>
          {error}
          <button
            className="text-button"
            disabled={loading}
            onClick={() => void load().catch((e) => setError(String(e)))}
          >
            Retry
          </button>
        </Notice>
      )}
      {loading && <p role="status">Loading history…</p>}
      {!loading && !error && !visible.length && (
        <p className="history-empty">
          {entries.length
            ? 'No books match your search.'
            : 'Books you open or listen to appear here.'}
        </p>
      )}
      <ul className="opened-book-list">
        {visible.map((entry) => {
          const book = books.find((book) => book.id === entry.bookId);
          return (
            <li key={entry.id}>
              {book && (
                <span className="history-cover">
                  <BookCover book={book} />
                </span>
              )}
              <div className="opened-book-details">
                <strong>{book?.title ?? entry.title}</strong>
                <span>
                  {entry.format} ·{' '}
                  {entry.openedAt ? (
                    <time dateTime={new Date(entry.openedAt).toISOString()}>
                      {new Date(entry.openedAt).toLocaleString(undefined, {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                      })}
                    </time>
                  ) : (
                    'Date unavailable'
                  )}
                </span>
                {!book && <span>Not in your library</span>}
              </div>
              <div className="action-row">
                <button
                  className="secondary"
                  disabled={!book || busy}
                  onClick={() => book && onOpen(book)}
                >
                  Open book
                </button>
                <button
                  className="text-button"
                  disabled={!native || busy}
                  onClick={() => setRemoving(entry)}
                >
                  Remove…
                </button>
              </div>
            </li>
          );
        })}
      </ul>
      {removing && (
        <Modal
          title={removing === 'all' ? 'Clear reading history' : 'Remove from history'}
          onClose={() => {
            if (!busy) setRemoving(null);
          }}
        >
          {error && (
            <Notice error onDismiss={() => setError('')}>
              {error}
            </Notice>
          )}
          <p>
            {removing === 'all'
              ? 'Clear your recent-books list? Your books and measured reading sessions remain.'
              : `Remove “${removing.title}” from recent books? The book and measured sessions remain.`}
          </p>
          <div className="action-row">
            <button className="danger" disabled={busy} onClick={() => void remove()}>
              {removing === 'all' ? 'Clear history' : 'Remove from history'}
            </button>
            <button className="secondary" disabled={busy} onClick={() => setRemoving(null)}>
              Cancel
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
