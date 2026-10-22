import { useEffect, useRef, useState } from 'react';
import { api, native, type Classic } from './api';
import { importInWorker } from './import-client';
import { Glyph } from './Glyph';
import type { Book } from './types';

export function ClassicsPage({
  books,
  onAdded,
  onOpen,
  onError,
}: {
  books: Book[];
  onAdded: (book: Book) => Promise<void>;
  onOpen: (book: Book) => void;
  onError: (error: unknown) => void;
}) {
  const [entries, setEntries] = useState<Classic[]>([]);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('All');
  const [busy, setBusy] = useState('');
  const [phase, setPhase] = useState('');
  const [failed, setFailed] = useState('');
  const [list, setList] = useState(false);
  const generation = useRef(0);
  useEffect(() => {
    let live = true;
    api
      .classics()
      .then((items) => {
        if (live) setEntries(items);
      })
      .catch(onError);
    return () => {
      live = false;
    };
  }, [onError]);
  async function add(entry: Classic) {
    const run = ++generation.current;
    setBusy(entry.id);
    setFailed('');
    setPhase('Downloading…');
    try {
      const file = await api.downloadClassic(entry.id);
      if (generation.current !== run) return;
      setPhase('Adding to your library…');
      const raw = new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(file.bytes));
      // Keep the complete source/licence bytes; remove Gutenberg wrappers only from reading flow.
      const body = raw
        .replace(/^[\s\S]*?\*\*\* START OF (?:THE|THIS) PROJECT GUTENBERG[^\n]*\n/i, '')
        .replace(/\*\*\* END OF (?:THE|THIS) PROJECT GUTENBERG[\s\S]*$/i, '')
        .trim();
      const parsed = await importInWorker(file.name, new TextEncoder().encode(body));
      if (generation.current !== run) return;
      const book = await api.saveClassic(entry.id, parsed, file);
      await onAdded(book);
      setEntries(await api.classics());
      setPhase(`Added ${entry.title}`);
    } catch (error) {
      if (generation.current === run) {
        setFailed(entry.id);
        setPhase('Download failed. You can try again.');
        onError(error);
      }
    } finally {
      if (generation.current === run) setBusy('');
    }
  }
  async function cancel() {
    const id = busy;
    ++generation.current;
    setBusy('');
    setPhase('Download cancelled');
    await api.cancelClassic(id).catch(onError);
  }
  const categories = ['All', ...new Set(entries.map((book) => book.category))];
  const visible = entries.filter(
    (book) =>
      (category === 'All' || book.category === category) &&
      `${book.title} ${book.author} ${book.description}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <div className="page classics-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">A WORLD OF STORIES</span>
          <h1>
            Classics<span className="heading-dot">.</span>
          </h1>
          <p className="page-description">
            The familiar Vern catalogue. Pick a cover, find your next chapter.
          </p>
        </div>
        <span className="catalogue-count">{entries.length} curated books</span>
      </div>
      <div className="catalogue-toolbar">
        <label className="search">
          <Glyph name="search" />
          <input
            aria-label="Search classics"
            placeholder="Title, author or a little curiosity…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        <button
          className="icon-button"
          aria-label={list ? 'Show classic covers' : 'Show classics as list'}
          onClick={() => setList(!list)}
        >
          <Glyph name={list ? 'grid' : 'list'} />
        </button>
      </div>
      <div className="genre-chips" aria-label="Classic genres">
        {categories.map((name) => (
          <button key={name} aria-pressed={category === name} onClick={() => setCategory(name)}>
            {name}
          </button>
        ))}
      </div>
      {phase && (
        <div className="catalogue-status" role="status">
          {busy && <span className="activity-ring" />}
          {phase}
          {busy && (
            <button className="text-button" onClick={() => void cancel()}>
              Cancel download
            </button>
          )}
        </div>
      )}
      <div className={`classics-grid ${list ? 'catalogue-list' : ''}`}>
        {visible.map((entry) => {
          const saved = books.find((book) => book.id === entry.bookId);
          return (
            <article className="classic-card" key={entry.id}>
              <div className="classic-art">
                <img
                  src={`/covers/${entry.id}.jpg`}
                  alt={`Cover of ${entry.title}`}
                  loading="lazy"
                />
                <span>{entry.category}</span>
              </div>
              <div className="classic-copy">
                <h2>{entry.title}</h2>
                <p className="classic-author">{entry.author}</p>
                <p className="classic-description">{entry.description}</p>
                <small>
                  About {Math.round((entry.minutes / 60) * 10) / 10} hours · Project Gutenberg
                </small>
                {saved ? (
                  <button className="secondary full" onClick={() => onOpen(saved)}>
                    <Glyph name="book" />
                    Read book
                  </button>
                ) : (
                  <button
                    className="primary full"
                    disabled={!!busy || !native}
                    onClick={() => void add(entry)}
                  >
                    <Glyph name={busy === entry.id ? 'download' : 'plus'} />
                    {busy === entry.id
                      ? phase
                      : failed === entry.id
                        ? 'Try again'
                        : 'Add to library'}
                  </button>
                )}
              </div>
            </article>
          );
        })}
      </div>
      {!visible.length && (
        <div className="empty-library">
          <Glyph name="search" />
          <p>No classics match this search. Try another title or genre.</p>
        </div>
      )}
      <p className="catalogue-footnote">
        Downloads come from Project Gutenberg. Check the book’s copyright status where you live.
        Downloaded books stay in your library for offline reading.
      </p>
    </div>
  );
}
