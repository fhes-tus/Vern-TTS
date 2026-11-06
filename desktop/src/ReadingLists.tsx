import { useEffect, useState } from 'react';
import { api, native } from './api';
import { BookCover } from './BookCover';
import { Glyph } from './Glyph';
import { Notice } from './Notice';
import { Modal } from './WorkspacePages';
import type { Book, ReadingList, ReadingListInput } from './types';
import './reading-lists.css';

const orders: Record<ReadingList['sortMode'], string> = {
  manual: 'My order',
  title: 'Title A–Z',
  'title-desc': 'Title Z–A',
  newest: 'Recently added',
  oldest: 'First added',
};
function orderedListBooks(list: ReadingList, books: Book[]) {
  const byId = new Map(books.map((book) => [book.id, book]));
  const items = list.items.flatMap((item) => {
    const book = byId.get(item.bookId);
    return book ? [{ book, addedAt: item.addedAt }] : [];
  });
  if (list.sortMode !== 'manual')
    items.sort((a, b) =>
      list.sortMode === 'title'
        ? a.book.title.localeCompare(b.book.title)
        : list.sortMode === 'title-desc'
          ? b.book.title.localeCompare(a.book.title)
          : list.sortMode === 'newest'
            ? b.addedAt - a.addedAt
            : a.addedAt - b.addedAt,
    );
  return items.map((item) => item.book);
}
export function ReadingLists({
  books,
  active,
  onOpen,
}: {
  books: Book[];
  active: boolean;
  onOpen: (book: Book) => void;
}) {
  const [lists, setLists] = useState<ReadingList[]>([]);
  const [selected, setSelected] = useState('');
  const [archived, setArchived] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [editing, setEditing] = useState<ReadingListInput | null>(null);
  const [deleting, setDeleting] = useState<ReadingList | null>(null);
  useEffect(() => {
    if (!active) return;
    let current = true;
    api
      .readingLists()
      .then((value) => {
        if (current) {
          setLists(value);
          setLoading(false);
        }
      })
      .catch((e) => {
        if (current) {
          setError(String(e));
          setLoading(false);
        }
      });
    return () => {
      current = false;
    };
  }, [active, books]);
  const visible = lists.filter((list) => list.archived === archived);
  const list = visible.find((item) => item.id === selected) ?? visible[0];
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (e) {
      setError(String(e).replace(/^Error: /, ''));
    } finally {
      setBusy(false);
    }
  }
  const input = (item: ReadingList): ReadingListInput => ({
    ...item,
    bookIds: item.items.map((book) => book.bookId),
  });
  async function save(value: ReadingListInput) {
    const saved = await api.saveReadingList(value);
    setLists((old) => [saved, ...old.filter((item) => item.id !== saved.id)]);
    setSelected(saved.id);
    setArchived(saved.archived);
  }
  const contents = list ? orderedListBooks(list, books) : [];
  return (
    <div className="page reading-lists">
      <div className="page-heading">
        <div>
          <h1>
            Reading lists<span className="heading-dot">.</span>
          </h1>
        </div>
        <button
          className="primary"
          disabled={!native || busy}
          onClick={() =>
            setEditing({
              id: null,
              title: '',
              description: '',
              archived: false,
              sortMode: 'manual',
              bookIds: [],
            })
          }
        >
          <Glyph name="plus" />
          New list
        </button>
      </div>
      {error && (
        <Notice error onDismiss={() => setError('')}>
          {error}
        </Notice>
      )}
      {message && <Notice onDismiss={() => setMessage('')}>{message}</Notice>}
      <div className="list-workspace">
        <aside className="list-index" aria-label="Your reading lists">
          <div className="list-tabs">
            {[false, true].map((value) => (
              <button
                key={String(value)}
                aria-pressed={archived === value}
                onClick={() => setArchived(value)}
              >
                {value ? 'Archived' : 'Active'}
              </button>
            ))}
          </div>
          {loading ? (
            <p role="status">Loading lists…</p>
          ) : visible.length ? (
            visible.map((item) => (
              <button
                className="list-index-item"
                key={item.id}
                aria-current={item.id === list?.id ? 'true' : undefined}
                onClick={() => setSelected(item.id)}
              >
                <Glyph name="list" />
                <span>
                  <strong>{item.title}</strong>
                  <small>
                    {item.items.length} {item.items.length === 1 ? 'book' : 'books'}
                  </small>
                </span>
              </button>
            ))
          ) : (
            <p className="muted">
              {archived
                ? 'No archived lists.'
                : 'Start a list, then choose books from your library.'}
            </p>
          )}
        </aside>
        <section className="list-detail" aria-label="Selected reading list">
          {list ? (
            <>
              <div className="list-detail-heading">
                <div>
                  <h2>{list.title}</h2>
                  {list.description && <p className="muted">{list.description}</p>}
                  <small>{orders[list.sortMode]}</small>
                </div>
                <div className="action-row">
                  <button
                    className="secondary"
                    disabled={busy}
                    onClick={() => setEditing(input(list))}
                  >
                    Edit list
                  </button>
                  <button
                    className="secondary"
                    disabled={busy}
                    onClick={() =>
                      void run(async () => {
                        await save({ ...input(list), archived: !list.archived });
                      })
                    }
                  >
                    {list.archived ? 'Restore list' : 'Archive'}
                  </button>
                  <button
                    className="icon-button"
                    disabled={busy}
                    aria-label="Delete reading list"
                    onClick={() => setDeleting(list)}
                  >
                    <Glyph name="trash" />
                  </button>
                </div>
              </div>
              {contents.length ? (
                <ol className="list-books">
                  {contents.map((book, index) => (
                    <li key={book.id}>
                      <span className="list-position">{index + 1}</span>
                      <button className="list-book-open" onClick={() => onOpen(book)}>
                        <BookCover book={book} />
                        <span>
                          <strong>{book.title}</strong>
                          <small>{book.author || book.format}</small>
                        </span>
                      </button>
                      <div className="list-book-actions">
                        {list.sortMode === 'manual' && (
                          <>
                            <button
                              className="icon-button"
                              aria-label={'Move ' + book.title + ' up'}
                              disabled={busy || index === 0}
                              onClick={() =>
                                void run(async () => {
                                  const value = input(list);
                                  [value.bookIds[index - 1], value.bookIds[index]] = [
                                    value.bookIds[index],
                                    value.bookIds[index - 1],
                                  ];
                                  await save(value);
                                })
                              }
                            >
                              ↑
                            </button>
                            <button
                              className="icon-button"
                              aria-label={'Move ' + book.title + ' down'}
                              disabled={busy || index === contents.length - 1}
                              onClick={() =>
                                void run(async () => {
                                  const value = input(list);
                                  [value.bookIds[index + 1], value.bookIds[index]] = [
                                    value.bookIds[index],
                                    value.bookIds[index + 1],
                                  ];
                                  await save(value);
                                })
                              }
                            >
                              ↓
                            </button>
                          </>
                        )}
                        <button
                          className="icon-button"
                          aria-label={'Remove ' + book.title + ' from list'}
                          disabled={busy}
                          onClick={() =>
                            void run(async () => {
                              await save({
                                ...input(list),
                                bookIds: list.items
                                  .filter((item) => item.bookId !== book.id)
                                  .map((item) => item.bookId),
                              });
                              setMessage('Book removed from the list. It remains in your library.');
                            })
                          }
                        >
                          <Glyph name="close" />
                        </button>
                      </div>
                    </li>
                  ))}
                </ol>
              ) : (
                <div className="list-empty">
                  <Glyph name="book" />
                  <h3>A place for your next reads</h3>
                  <p>Add books with Edit list. Their reading positions stay with them.</p>
                </div>
              )}
            </>
          ) : (
            <div className="list-empty">
              <Glyph name="list" />
              <h2>{archived ? 'Set aside for later' : 'Give your books a path'}</h2>
              <p>
                {archived
                  ? 'Archived lists will appear here.'
                  : 'Create your first list to arrange books in the order you want to read them.'}
              </p>
            </div>
          )}
        </section>
      </div>
      {editing && (
        <ListEditor
          initial={editing}
          books={books}
          onClose={() => setEditing(null)}
          onSave={async (value) => {
            await save(value);
            setEditing(null);
          }}
        />
      )}
      {deleting && (
        <Modal
          title="Delete this reading list?"
          onClose={() => {
            if (!busy) setDeleting(null);
          }}
        >
          <p>“{deleting.title}” will be removed. Its books stay in your library.</p>
          <div className="action-row">
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await api.deleteReadingList(deleting.id);
                  setLists((old) => old.filter((item) => item.id !== deleting.id));
                  setDeleting(null);
                })
              }
            >
              {busy ? 'Deleting…' : 'Delete list'}
            </button>
            <button className="secondary" disabled={busy} onClick={() => setDeleting(null)}>
              Keep list
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
function ListEditor({
  initial,
  books,
  onClose,
  onSave,
}: {
  initial: ReadingListInput;
  books: Book[];
  onClose: () => void;
  onSave: (value: ReadingListInput) => Promise<void>;
}) {
  const [draft, setDraft] = useState(initial);
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const candidates = books
    .filter((book) =>
      (book.title + ' ' + book.author).toLocaleLowerCase().includes(search.toLocaleLowerCase()),
    )
    .slice(0, 100);
  return (
    <Modal
      title={initial.id ? 'Edit reading list' : 'New reading list'}
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form
        className="workspace-form reading-list-editor"
        onSubmit={(event) => {
          event.preventDefault();
          setBusy(true);
          setError('');
          void onSave(draft).catch((e) => {
            setError(String(e));
            setBusy(false);
          });
        }}
      >
        <label>
          List name
          <input
            autoFocus
            required
            maxLength={500}
            value={draft.title}
            onChange={(event) => setDraft({ ...draft, title: event.target.value })}
          />
        </label>
        <label>
          Description (optional)
          <textarea
            maxLength={10000}
            rows={2}
            value={draft.description}
            onChange={(event) => setDraft({ ...draft, description: event.target.value })}
          />
        </label>
        <label>
          Book order
          <select
            value={draft.sortMode}
            onChange={(event) =>
              setDraft({ ...draft, sortMode: event.target.value as ReadingList['sortMode'] })
            }
          >
            {Object.entries(orders).map(([value, title]) => (
              <option key={value} value={value}>
                {title}
              </option>
            ))}
          </select>
        </label>
        <fieldset className="list-book-picker" disabled={busy}>
          <legend>Books · {draft.bookIds.length} selected</legend>
          <label>
            Find a book
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          <div>
            {candidates.map((book) => (
              <label key={book.id}>
                <input
                  type="checkbox"
                  aria-label={book.title}
                  checked={draft.bookIds.includes(book.id)}
                  onChange={(event) =>
                    setDraft({
                      ...draft,
                      bookIds: event.target.checked
                        ? [...draft.bookIds, book.id]
                        : draft.bookIds.filter((id) => id !== book.id),
                    })
                  }
                />
                <span>
                  {book.title}
                  <small>{book.author}</small>
                </span>
              </label>
            ))}
            {!candidates.length && (
              <p className="muted">
                {books.length ? 'No books match your search.' : 'Import a book to add it here.'}
              </p>
            )}
            {books.length > 100 && <p className="muted">Search to find more books.</p>}
          </div>
        </fieldset>
        {error && <p role="alert">{error}</p>}
        <div className="action-row">
          <button className="primary" disabled={busy || !draft.title.trim()}>
            {busy ? 'Saving…' : 'Save list'}
          </button>
          <button type="button" className="secondary" disabled={busy} onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
