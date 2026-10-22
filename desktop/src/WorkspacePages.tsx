import { useEffect, useRef, useState, type ReactNode } from 'react';
import { api, native } from './api';
import { Glyph } from './Glyph';
import type { Annotation, Book, CardInput, Flashcard } from './types';

export function TextImportDialog({
  onImport,
  onClose,
}: {
  onImport: (title: string, author: string, text: string) => Promise<void>;
  onClose: () => void;
}) {
  const [title, setTitle] = useState('');
  const [author, setAuthor] = useState('');
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function save() {
    setBusy(true);
    setError('');
    try {
      await onImport(title.trim(), author.trim(), text);
      onClose();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title="Add your own text"
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form
        className="workspace-form"
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <label>
          Title
          <input
            autoFocus
            required
            maxLength={1000}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </label>
        <label>
          Author (optional)
          <input maxLength={1000} value={author} onChange={(e) => setAuthor(e.target.value)} />
        </label>
        <label>
          Text
          <textarea
            required
            maxLength={2000000}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Paste an article, your notes, or something you wrote…"
          />
        </label>
        <p className="muted">Saved to your library for reading, offline listening and study.</p>
        {error && <p role="alert">{error}</p>}
        <button className="primary" disabled={busy || !native || !title.trim() || !text.trim()}>
          {busy ? 'Adding text…' : 'Add to library'}
        </button>
      </form>
    </Modal>
  );
}

export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => element?.close();
  }, []);
  return (
    <dialog
      ref={dialog}
      className="workspace-dialog"
      aria-labelledby="dialog-title"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <div className="dialog-heading">
        <h2 id="dialog-title">{title}</h2>
        <button className="secondary small" onClick={onClose}>
          <Glyph name="close" />
          Close
        </button>
      </div>
      {children}
    </dialog>
  );
}

export function BookDetails({
  book,
  collections,
  onClose,
  onSave,
  onRemove,
}: {
  book: Book;
  collections: string[];
  onClose: () => void;
  onSave: (title: string, author: string, collection: string) => Promise<void>;
  onRemove: () => Promise<void>;
}) {
  const [title, setTitle] = useState(book.title);
  const [author, setAuthor] = useState(book.author);
  const [collection, setCollection] = useState(book.collection ?? '');
  const [removing, setRemoving] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(remove = false) {
    setBusy(true);
    setError('');
    try {
      if (remove) await onRemove();
      else await onSave(title, author, collection);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title="Book details"
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form
        className="workspace-form"
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <label>
          Title
          <input
            autoFocus
            required
            maxLength={1000}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </label>
        <label>
          Author
          <input maxLength={1000} value={author} onChange={(e) => setAuthor(e.target.value)} />
        </label>
        <label>
          Collection
          <input
            list="book-collections"
            maxLength={200}
            value={collection}
            onChange={(e) => setCollection(e.target.value)}
            placeholder="Choose or create a collection"
          />
        </label>
        <datalist id="book-collections">
          {collections.map((name) => (
            <option key={name} value={name} />
          ))}
        </datalist>
        <p className="muted">Leave collection empty to keep this book on your main shelf.</p>
        {error && <p role="alert">{error}</p>}
        <button className="primary" disabled={busy || !native || !title.trim()}>
          Save details
        </button>
      </form>
      <div className="remove-book">
        {removing ? (
          <>
            <p>
              Remove “{book.title}” and its saved position, notes, bookmarks and linked flashcards
              from this library? The original file stays on your computer.
            </p>
            <div className="action-row">
              <button className="danger" disabled={busy} onClick={() => void submit(true)}>
                Remove book and linked items
              </button>
              <button className="secondary" disabled={busy} onClick={() => setRemoving(false)}>
                Keep book
              </button>
            </div>
          </>
        ) : (
          <button
            className="text-button"
            disabled={busy || !native}
            onClick={() => setRemoving(true)}
          >
            Remove from library…
          </button>
        )}
      </div>
    </Modal>
  );
}

export function AnnotationCard({
  item,
  book,
  onOpen,
  onChanged,
  onError,
}: {
  item: Annotation;
  book?: Book;
  onOpen: () => void;
  onChanged: () => Promise<void>;
  onError: (e: unknown) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(item.text);
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState(false);
  async function change(remove: boolean) {
    setBusy(true);
    try {
      if (remove) await api.deleteAnnotation(item.id);
      else await api.annotate({ ...item, text: text.trim() });
      await onChanged();
      setEditing(false);
      setRemoving(false);
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <article className="saved-note">
      <span className="eyebrow">
        {item.kind} · Passage {item.position + 1}
      </span>
      {book && <h3>{book.title}</h3>}
      {editing ? (
        <form
          className="workspace-form"
          onSubmit={(e) => {
            e.preventDefault();
            void change(false);
          }}
        >
          <label>
            Edit {item.kind}
            <textarea
              autoFocus
              required
              maxLength={50000}
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
          </label>
          <div className="action-row">
            <button className="primary small" disabled={busy || !text.trim()}>
              <Glyph name="save" />
              Save changes
            </button>
            <button
              type="button"
              className="secondary small"
              disabled={busy}
              onClick={() => setEditing(false)}
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <p>{item.text}</p>
      )}
      <div className="action-row">
        <button className="text-button" onClick={onOpen}>
          <Glyph name="book" />
          Return to passage →
        </button>
        <button
          className="text-button"
          disabled={busy || !native}
          onClick={() => {
            setText(item.text);
            setEditing(true);
            setRemoving(false);
          }}
        >
          <Glyph name="edit" />
          Edit
        </button>
        <button
          className="text-button"
          disabled={busy || !native}
          onClick={() => setRemoving(!removing)}
        >
          <Glyph name="trash" />
          Delete
        </button>
      </div>
      {removing && (
        <div className="action-row">
          <span>Delete this {item.kind}?</span>
          <button className="danger small" disabled={busy} onClick={() => void change(true)}>
            Delete {item.kind}
          </button>
          <button className="secondary small" disabled={busy} onClick={() => setRemoving(false)}>
            Keep
          </button>
        </div>
      )}
    </article>
  );
}

export function NotesPage({
  books,
  onOpen,
  onError,
}: {
  books: Book[];
  onOpen: (book: Book, position: number) => void;
  onError: (e: unknown) => void;
}) {
  const [items, setItems] = useState<Annotation[]>([]);
  const [query, setQuery] = useState('');
  const [bookId, setBookId] = useState('');
  const [kind, setKind] = useState('all');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let mounted = true;
    api
      .allAnnotations()
      .then((notes) => {
        if (mounted) setItems(notes);
      })
      .catch(onError)
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [onError]);
  async function refresh() {
    setItems(await api.allAnnotations());
  }
  const visible = items.filter(
    (item) =>
      (!bookId || item.bookId === bookId) &&
      (kind === 'all' || item.kind === kind) &&
      `${item.text} ${books.find((b) => b.id === item.bookId)?.title ?? ''}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <div className="page notes-page">
      <span className="eyebrow">KEEP THE WORDS THAT MATTER</span>
      <h1>
        Notes & bookmarks<span className="heading-dot">.</span>
      </h1>
      <p className="page-description">
        Your thoughts across every book, with a path back to the source.
      </p>
      <div className="workspace-filters">
        <label>
          Search notes
          <input
            type="search"
            placeholder="Find a thought…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        <label>
          Book
          <select value={bookId} onChange={(e) => setBookId(e.target.value)}>
            <option value="">All books</option>
            {books.map((b) => (
              <option key={b.id} value={b.id}>
                {b.title}
              </option>
            ))}
          </select>
        </label>
        <label>
          Show
          <select value={kind} onChange={(e) => setKind(e.target.value)}>
            <option value="all">Notes and bookmarks</option>
            <option value="note">Notes</option>
            <option value="bookmark">Bookmarks</option>
          </select>
        </label>
      </div>
      <p className="muted" role="status">
        {loading ? 'Loading your notebook…' : `${visible.length} saved items`}
      </p>
      <div className="annotation-grid">
        {visible.map((item) => {
          const book = books.find((b) => b.id === item.bookId);
          return (
            <AnnotationCard
              key={item.id}
              item={item}
              book={book}
              onOpen={() => {
                if (book) onOpen(book, item.position);
              }}
              onChanged={refresh}
              onError={onError}
            />
          );
        })}
      </div>
      {!loading && !visible.length && (
        <div className="empty-library">
          <Glyph name="note" />
          <div>
            <h3>No saved items here yet</h3>
            <p>Add notes or bookmarks while reading, or adjust your filters.</p>
          </div>
        </div>
      )}
    </div>
  );
}

function CardEditor({
  initial,
  decks,
  onSave,
  onClose,
}: {
  initial: CardInput;
  decks: string[];
  onSave: () => Promise<void>;
  onClose: () => void;
}) {
  const [input, setInput] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function save() {
    setBusy(true);
    setError('');
    try {
      await api.saveFlashcard(input);
      await onSave();
      onClose();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={input.id ? 'Edit flashcard' : 'Create flashcard'}
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form
        className="workspace-form"
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <label>
          Deck
          <input
            autoFocus
            list="study-decks"
            required
            maxLength={200}
            value={input.deck}
            onChange={(e) => setInput({ ...input, deck: e.target.value })}
          />
        </label>
        <datalist id="study-decks">
          {decks.map((deck) => (
            <option key={deck} value={deck} />
          ))}
        </datalist>
        <label>
          Question
          <textarea
            required
            maxLength={10000}
            value={input.front}
            onChange={(e) => setInput({ ...input, front: e.target.value })}
          />
        </label>
        <label>
          Answer
          <textarea
            required
            maxLength={10000}
            value={input.back}
            onChange={(e) => setInput({ ...input, back: e.target.value })}
          />
        </label>
        {input.bookId && (
          <p className="muted">Linked to passage {(input.position ?? 0) + 1} in the source book.</p>
        )}
        {error && <p role="alert">{error}</p>}
        <button
          className="primary"
          disabled={
            busy || !native || !input.front.trim() || !input.back.trim() || !input.deck.trim()
          }
        >
          Save flashcard
        </button>
      </form>
    </Modal>
  );
}

export function StudyPage({
  books,
  seed,
  onSeedUsed,
  onOpen,
  onError,
}: {
  books: Book[];
  seed: CardInput | null;
  onSeedUsed: () => void;
  onOpen: (book: Book, position: number) => void;
  onError: (e: unknown) => void;
}) {
  const [cards, setCards] = useState<Flashcard[]>([]);
  const [deck, setDeck] = useState('');
  const [query, setQuery] = useState('');
  const [editor, setEditor] = useState<CardInput | null>(seed);
  const [queue, setQueue] = useState<string[] | null>(null);
  const [revealed, setRevealed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [now, setNow] = useState(Date.now);
  const [deleting, setDeleting] = useState<string | null>(null);
  useEffect(() => {
    let mounted = true;
    api
      .flashcards()
      .then((items) => {
        if (mounted) setCards(items);
      })
      .catch(onError)
      .finally(() => {
        if (mounted) setLoading(false);
      });
    const timer = setInterval(() => setNow(Date.now()), 60000);
    return () => {
      mounted = false;
      clearInterval(timer);
    };
  }, [onError]);
  async function refresh() {
    setCards(await api.flashcards());
    setNow(Date.now());
  }
  function closeEditor() {
    setEditor(null);
    onSeedUsed();
  }
  const decks = [...new Set(cards.map((card) => card.deck))].sort();
  const visible = cards.filter(
    (card) =>
      (!deck || card.deck === deck) &&
      `${card.front} ${card.back} ${card.deck}`.toLowerCase().includes(query.toLowerCase()),
  );
  const due = visible.filter((card) => !card.recall || card.dueAt <= now);
  const reviewing = queue?.length ? cards.find((card) => card.id === queue[0]) : undefined;
  async function rate(rating: string) {
    if (!reviewing || busy) return;
    setBusy(true);
    try {
      await api.reviewFlashcard(reviewing.id, rating);
      await refresh();
      setQueue((q) => q?.slice(1) ?? null);
      setRevealed(false);
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }
  async function remove(id: string) {
    setBusy(true);
    try {
      await api.deleteFlashcard(id);
      await refresh();
      setDeleting(null);
    } catch (e) {
      onError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="page study-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">A LITTLE PRACTICE, OFTEN</span>
          <h1>
            Flashcards<span className="heading-dot">.</span>
          </h1>
          <p>Turn reading into recall, one card at a time.</p>
        </div>
        <button
          className="primary"
          disabled={!native || queue !== null || loading}
          onClick={() =>
            setEditor({
              bookId: null,
              position: null,
              deck: deck || 'My flashcards',
              front: '',
              back: '',
            })
          }
        >
          <Glyph name="plus" />
          Create flashcard
        </button>
      </div>
      <div className="workspace-filters">
        <label>
          Deck
          <select disabled={queue !== null} value={deck} onChange={(e) => setDeck(e.target.value)}>
            <option value="">All decks</option>
            {decks.map((name) => (
              <option key={name}>{name}</option>
            ))}
          </select>
        </label>
        <label>
          Search flashcards
          <input
            type="search"
            disabled={queue !== null}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Question, answer or deck…"
          />
        </label>
      </div>
      <div className="study-summary">
        <div>
          <strong>{due.length}</strong>
          <span>cards due</span>
        </div>
        <div>
          <strong>{visible.length}</strong>
          <span>cards in this view</span>
        </div>
        <button
          className="primary"
          disabled={!due.length || loading || queue !== null}
          onClick={() => {
            setQueue(due.map((card) => card.id));
            setRevealed(false);
          }}
        >
          <Glyph name="study" />
          Review due cards
        </button>
      </div>
      {reviewing && (
        <section className="review-card" aria-label="Flashcard review">
          <p className="eyebrow">
            {reviewing.deck} · {queue?.length} remaining
          </p>
          <h2>{reviewing.front}</h2>
          {revealed ? (
            <>
              <div className="flashcard-answer">{reviewing.back}</div>
              <div className="action-row">
                {['again', 'hard', 'good', 'easy'].map((rating) => (
                  <button
                    key={rating}
                    disabled={busy}
                    className={rating === 'good' ? 'primary' : 'secondary'}
                    onClick={() => void rate(rating)}
                  >
                    {rating[0].toUpperCase() + rating.slice(1)}
                  </button>
                ))}
              </div>
            </>
          ) : (
            <button className="primary" onClick={() => setRevealed(true)}>
              <Glyph name="study" />
              Show answer
            </button>
          )}
          <button className="text-button" disabled={busy} onClick={() => setQueue(null)}>
            End review
          </button>
        </section>
      )}
      {queue !== null && !queue.length && (
        <section className="review-card" role="status">
          <h2>Review complete</h2>
          <p>Your ratings are saved. Cards will return when they are due.</p>
          <button className="primary" onClick={() => setQueue(null)}>
            Back to flashcards
          </button>
        </section>
      )}
      {queue === null && (
        <>
          <p className="muted" role="status">
            {loading ? 'Loading flashcards…' : `${visible.length} flashcards`}
          </p>
          <div className="annotation-grid">
            {visible.map((card) => {
              const book = books.find((b) => b.id === card.bookId);
              return (
                <article key={card.id} className="saved-note">
                  <span className="eyebrow">{card.deck}</span>
                  <h3>{card.front}</h3>
                  <p className="flashcard-answer">{card.back}</p>
                  <p className="muted">
                    {!card.recall
                      ? 'New · ready to review'
                      : card.dueAt <= now
                        ? 'Due for review'
                        : `Next review ${new Date(card.dueAt).toLocaleDateString()}`}
                  </p>
                  <div className="action-row">
                    {book && card.position !== null && (
                      <button className="text-button" onClick={() => onOpen(book, card.position!)}>
                        Source passage →
                      </button>
                    )}
                    <button className="text-button" disabled={busy} onClick={() => setEditor(card)}>
                      Edit
                    </button>
                    <button
                      className="text-button"
                      disabled={busy}
                      onClick={() => setDeleting(card.id)}
                    >
                      Delete
                    </button>
                  </div>
                  {deleting === card.id && (
                    <div className="action-row">
                      <span>Delete this flashcard?</span>
                      <button
                        className="danger small"
                        disabled={busy}
                        onClick={() => void remove(card.id)}
                      >
                        Delete flashcard
                      </button>
                      <button
                        className="secondary small"
                        disabled={busy}
                        onClick={() => setDeleting(null)}
                      >
                        Keep
                      </button>
                    </div>
                  )}
                </article>
              );
            })}
          </div>
          {!loading && !visible.length && (
            <div className="empty-library">
              <div>
                <h3>No flashcards here yet</h3>
                <p>Create a card here, or use “Create flashcard” beside a passage in the reader.</p>
              </div>
            </div>
          )}
        </>
      )}
      {editor && (
        <CardEditor initial={editor} decks={decks} onSave={refresh} onClose={closeEditor} />
      )}
    </div>
  );
}
