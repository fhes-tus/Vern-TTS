import { useMemo, useState, type RefObject } from 'react';
import { api, native } from './api';
import { BookCover } from './BookCover';
import { Glyph } from './Glyph';
import { Modal } from './WorkspacePages';
import type { Book } from './types';
import './library-shelf.css';

// Android uses the saved final passage as the completed-book boundary.
const finished = (book: Book) => book.chunks.length > 0 && book.position >= book.chunks.length - 1;
type Props = {
  books: Book[];
  queue: string[];
  searchRef: RefObject<HTMLInputElement | null>;
  onOpen: (book: Book, position?: number) => void;
  onDetails: (book: Book) => void;
  onFavorite: (book: Book) => Promise<void>;
  onQueue: (ids: string[]) => Promise<boolean>;
  onDelete: (ids: string[]) => Promise<void>;
  onPosition: (book: Book, position: number) => Promise<void>;
  onRefresh: () => Promise<void>;
};

export function LibraryShelf(props: Props) {
  const { books, queue } = props;
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('all');
  const [collection, setCollection] = useState('');
  const [status, setStatus] = useState('all');
  const [sort, setSort] = useState('recent');
  const [selecting, setSelecting] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [deleting, setDeleting] = useState<string[] | null>(null);
  const [moving, setMoving] = useState(false);
  const [destination, setDestination] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const selected = books.filter((book) => selectedIds.includes(book.id));
  const collections = [...new Set(books.map((book) => book.collection).filter(Boolean))].sort();
  const visible = useMemo(() => {
    const term = query.trim().toLowerCase();
    return books
      .map((book) => ({
        book,
        match: term ? book.chunks.findIndex((chunk) => chunk.toLowerCase().includes(term)) : -1,
      }))
      .filter(
        ({ book, match }) =>
          (filter !== 'favorites' || book.favorite) &&
          (!collection || book.collection === collection) &&
          (status === 'all' || (status === 'finished' ? finished(book) : !finished(book))) &&
          (!term ||
            `${book.title} ${book.author} ${book.collection ?? ''}`.toLowerCase().includes(term) ||
            match >= 0),
      )
      .sort(
        (a, b) =>
          Number(finished(a.book)) - Number(finished(b.book)) ||
          (sort === 'title'
            ? a.book.title.localeCompare(b.book.title)
            : sort === 'author'
              ? a.book.author.localeCompare(b.book.author)
              : sort === 'progress'
                ? b.book.position / Math.max(1, b.book.chunks.length - 1) -
                  a.book.position / Math.max(1, a.book.chunks.length - 1)
                : b.book.updatedAt - a.book.updatedAt),
      );
  }, [books, query, filter, collection, status, sort]);
  function toggle(id: string) {
    setSelecting(true);
    setSelectedIds((ids) => (ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id]));
  }
  async function act(action: () => Promise<unknown>) {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await action();
      return true;
    } catch (e) {
      setError(String(e).replace(/^Error: /, ''));
      return false;
    } finally {
      setBusy(false);
    }
  }
  function cancelSelection() {
    setSelecting(false);
    setSelectedIds([]);
  }
  return (
    <>
      <div className="library-toolbar">
        <div className="tabs" aria-label="Library filter">
          <button className={filter === 'all' ? 'active' : ''} onClick={() => setFilter('all')}>
            All books <span>{books.length}</span>
          </button>
          <button
            className={filter === 'favorites' ? 'active' : ''}
            onClick={() => setFilter('favorites')}
          >
            Favorites
          </button>
        </div>
        <label className="search">
          <Glyph name="search" />
          <input
            ref={props.searchRef}
            type="search"
            aria-label="Search library"
            placeholder="Search books & text…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <kbd>Ctrl F</kbd>
        </label>
      </div>
      <div className="workspace-filters shelf-filters">
        <label>
          Collection
          <select
            aria-label="Collection"
            value={collection}
            onChange={(e) => setCollection(e.target.value)}
          >
            <option value="">All collections</option>
            {collections.map((name) => (
              <option key={name}>{name}</option>
            ))}
          </select>
        </label>
        <label>
          Read status
          <select
            aria-label="Read status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="all">All statuses</option>
            <option value="unfinished">Unfinished</option>
            <option value="finished">Finished</option>
          </select>
        </label>
        <label>
          Sort by
          <select aria-label="Sort by" value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="recent">Recently read</option>
            <option value="title">Title</option>
            <option value="author">Author</option>
            <option value="progress">Progress</option>
          </select>
        </label>
        <button
          className="secondary small"
          aria-pressed={selecting}
          disabled={!books.length || busy}
          onClick={() => (selecting ? cancelSelection() : setSelecting(true))}
        >
          {selecting ? 'Cancel selection' : 'Select books'}
        </button>
        <span className="shelf-sort-note">Finished books appear last</span>
      </div>
      {selecting && (
        <div className="library-selection" role="region" aria-label="Book selection">
          <strong role="status">{selected.length} selected</strong>
          <button
            className="text-button"
            disabled={busy || !visible.length}
            onClick={() =>
              setSelectedIds((ids) => [...new Set([...ids, ...visible.map(({ book }) => book.id)])])
            }
          >
            Select all shown
          </button>
          <button
            className="secondary small"
            disabled={busy || !selected.length || !native}
            onClick={() =>
              void act(async () => {
                for (const book of selected) if (!book.favorite) await props.onFavorite(book);
              })
            }
          >
            Add to favorites
          </button>
          <button
            className="secondary small"
            disabled={busy || !selected.length || !native}
            onClick={() =>
              void act(async () => {
                if (
                  !(await props.onQueue([
                    ...new Set([...queue, ...selected.map((book) => book.id)]),
                  ]))
                )
                  throw new Error('The listening queue could not be saved.');
              })
            }
          >
            Add to queue
          </button>
          <button
            className="secondary small"
            disabled={busy || !selected.length || !native}
            onClick={() => {
              setError('');
              setMoving(true);
            }}
          >
            Move to collection
          </button>
          <button
            className="danger small"
            disabled={busy || !selected.length || !native}
            onClick={() => {
              setError('');
              setDeleting(selected.map((book) => book.id));
            }}
          >
            <Glyph name="trash" />
            Delete selected
          </button>
        </div>
      )}
      {error && !deleting && !moving && <p role="alert">{error}</p>}
      {visible.length ? (
        <div className="book-grid">
          {visible.map(({ book, match }) => (
            <article
              className={`book-card ${selectedIds.includes(book.id) ? 'book-is-selected' : ''}`}
              key={book.id}
              onContextMenu={(event) => {
                event.preventDefault();
                setSelecting(true);
                setSelectedIds((ids) => (ids.includes(book.id) ? ids : [...ids, book.id]));
              }}
            >
              <div className="book-card-actions">
                {selecting && (
                  <label className="book-select" title={`Select ${book.title}`}>
                    <input
                      type="checkbox"
                      aria-label={`Select ${book.title}`}
                      checked={selectedIds.includes(book.id)}
                      disabled={busy}
                      onChange={() => toggle(book.id)}
                    />
                  </label>
                )}
                <details className="book-options">
                  <summary
                    aria-label={`Options for ${book.title}`}
                    title={`Options for ${book.title}`}
                  >
                    •••
                  </summary>
                  <div
                    className="book-options-list"
                    onClick={(event) => {
                      const details = event.currentTarget.closest('details');
                      if (details) details.open = false;
                    }}
                  >
                    <button onClick={() => props.onDetails(book)}>Book details & collection</button>
                    <button onClick={() => void act(() => props.onFavorite(book))}>
                      {book.favorite ? 'Remove from favorites' : 'Add to favorites'}
                    </button>
                    <button
                      onClick={() =>
                        void act(() =>
                          props.onQueue(
                            queue.includes(book.id)
                              ? queue.filter((id) => id !== book.id)
                              : [...queue, book.id],
                          ),
                        )
                      }
                    >
                      {queue.includes(book.id)
                        ? 'Remove from listening queue'
                        : 'Add to listening queue'}
                    </button>
                    <button onClick={() => props.onOpen(book, 0)}>Read from beginning</button>
                    {!finished(book) && (
                      <button
                        disabled={!native || busy}
                        onClick={() =>
                          void act(() => props.onPosition(book, book.chunks.length - 1))
                        }
                      >
                        Mark as finished
                      </button>
                    )}
                    <button
                      className="book-delete"
                      disabled={!native || busy}
                      onClick={() => {
                        setError('');
                        setDeleting([book.id]);
                      }}
                    >
                      Delete book…
                    </button>
                  </div>
                </details>
              </div>
              <button
                className="book-open"
                onClick={() => (selecting ? toggle(book.id) : props.onOpen(book))}
                aria-label={`${selecting ? 'Select book' : 'Open'} ${book.title}`}
              >
                <div className="cover-wrap">
                  <BookCover book={book} large />
                </div>
                <h3>{book.title}</h3>
                <p>{book.author || 'Personal library'}</p>
              </button>
              <div className="book-meta">
                <span>
                  {book.format} <span>·</span>{' '}
                  {finished(book)
                    ? 'Finished'
                    : `${Math.round((book.position / Math.max(1, book.chunks.length - 1)) * 100)}% read`}
                </span>
                <button
                  className={`icon-button ${book.favorite ? 'is-favorite' : ''}`}
                  aria-label={`${book.favorite ? 'Unfavorite' : 'Favorite'} ${book.title}`}
                  onClick={() => void act(() => props.onFavorite(book))}
                >
                  <Glyph name="heart" />
                </button>
              </div>
              <progress
                aria-label={`Reading progress for ${book.title}`}
                value={finished(book) ? 1 : book.position / Math.max(1, book.chunks.length - 1)}
                max={1}
              />
              {book.collection && <span className="collection-label">{book.collection}</span>}
              {match >= 0 && (
                <button className="search-excerpt" onClick={() => props.onOpen(book, match)}>
                  <small>Passage {match + 1} →</small>
                  {book.chunks[match].slice(0, 150)}
                  {book.chunks[match].length > 150 ? '…' : ''}
                </button>
              )}
              <button
                className="text-button book-details"
                onClick={() => props.onDetails(book)}
                aria-label={`Manage ${book.title}`}
              >
                Book details & collection
              </button>
            </article>
          ))}
        </div>
      ) : (
        <div className="empty-library">
          <Glyph name="book" />
          <div>
            <h3>{books.length ? 'No books found' : 'Your shelf starts here'}</h3>
            <p>
              {books.length
                ? 'Try another search or filter.'
                : 'Imported books appear here, with your place kept for you.'}
            </p>
          </div>
        </div>
      )}
      {deleting && (
        <Modal
          title={deleting.length === 1 ? 'Delete book?' : `Delete ${deleting.length} books?`}
          onClose={() => {
            if (!busy) setDeleting(null);
          }}
        >
          <p>
            This removes the selected books, saved positions, book notes, bookmarks and linked
            flashcards from this library. Original files on your computer stay in place.
          </p>
          <ul className="delete-book-list">
            {books
              .filter((book) => deleting.includes(book.id))
              .map((book) => (
                <li key={book.id}>{book.title}</li>
              ))}
          </ul>
          {error && <p role="alert">{error}</p>}
          <div className="action-row">
            <button
              className="secondary"
              disabled={busy}
              autoFocus
              onClick={() => setDeleting(null)}
            >
              Keep books
            </button>
            <button
              className="danger"
              disabled={busy}
              onClick={() =>
                void act(async () => {
                  await props.onDelete(deleting);
                  setSelectedIds((ids) => ids.filter((id) => !deleting.includes(id)));
                  setDeleting(null);
                })
              }
            >
              {busy ? 'Deleting…' : 'Delete from library'}
            </button>
          </div>
        </Modal>
      )}
      {moving && (
        <Modal
          title="Move to collection"
          onClose={() => {
            if (!busy) setMoving(false);
          }}
        >
          <form
            className="workspace-form"
            onSubmit={(event) => {
              event.preventDefault();
              void act(async () => {
                try {
                  for (const book of selected)
                    await api.updateBook(book.id, book.title, book.author, destination.trim());
                } finally {
                  await props.onRefresh();
                }
                setMoving(false);
              });
            }}
          >
            <p>
              Move {selected.length} selected books. Leave the name empty to return them to the main
              shelf.
            </p>
            <label>
              Collection name
              <input
                autoFocus
                maxLength={200}
                list="shelf-collections"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
              />
            </label>
            <datalist id="shelf-collections">
              {collections.map((name) => (
                <option key={name} value={name} />
              ))}
            </datalist>
            {error && <p role="alert">{error}</p>}
            <button className="primary" disabled={busy || !selected.length}>
              {busy ? 'Moving…' : 'Move books'}
            </button>
          </form>
        </Modal>
      )}
      <p className="shelf-footer">
        A quieter corner of your day. <span>Made for reading.</span>
      </p>
    </>
  );
}
