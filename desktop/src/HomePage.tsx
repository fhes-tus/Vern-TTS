import { useEffect, useState } from 'react';
import { api } from './api';
import { Glyph } from './Glyph';
import type { Book } from './types';
import { BookCover } from './BookCover';

export function HomePage({
  books,
  active,
  onOpen,
  onNavigate,
  onImport,
  onError,
}: {
  books: Book[];
  active?: Book;
  onOpen: (book: Book) => void;
  onNavigate: (
    page: 'library' | 'classics' | 'study' | 'notes' | 'queue' | 'ai' | 'insights',
  ) => void;
  onImport: () => void;
  onError: (error: unknown) => void;
}) {
  const [notes, setNotes] = useState<number | null>(null);
  const [due, setDue] = useState<number | null>(null);
  useEffect(() => {
    let live = true;
    Promise.all([api.allAnnotations(), api.flashcards(), api.personalNotes()])
      .then(([notes, cards, personal]) => {
        if (live) {
          setNotes(notes.length + personal.filter((n) => !n.deletedAt).length);
          setDue(cards.filter((card) => card.dueAt <= Date.now()).length);
        }
      })
      .catch(onError);
    return () => {
      live = false;
    };
  }, [onError]);
  return (
    <div className="page home-page">
      <div className="page-heading home-heading">
        <h1>Home</h1>
        <button className="text-button home-insights-link" onClick={() => onNavigate('insights')}>
          Reading insights <Glyph name="arrow" />
        </button>
      </div>
      <section className="home-hero" aria-labelledby="home-reading-title">
        <div>
          <h2 id="home-reading-title">{active?.title ?? 'Add your first book'}</h2>
          {active ? (
            active.author && <p>{active.author}</p>
          ) : (
            <p>Import a file or browse Classics.</p>
          )}
          {active && (
            <div className="continue-progress">
              <progress value={active.position + 1} max={active.chunks.length} />
              <span>{Math.round(((active.position + 1) / active.chunks.length) * 100)}% read</span>
            </div>
          )}
          <div className="action-row">
            <button className="primary" onClick={() => (active ? onOpen(active) : onImport())}>
              <Glyph name="book" />
              {active ? 'Continue reading' : 'Import book'}
            </button>
            <button className="secondary" onClick={() => onNavigate('classics')}>
              <Glyph name="compass" />
              Explore Classics
            </button>
          </div>
        </div>
        <div className="home-book-stack" aria-hidden="true">
          {active ? (
            <BookCover book={active} large />
          ) : (
            <>
              <img src="/covers/classic_secret_garden.jpg" alt="" />
              <img src="/covers/classic_alice_wonderland.jpg" alt="" />
              <img src="/covers/classic_pride_and_prejudice.jpg" alt="" />
            </>
          )}
        </div>
      </section>
      <div className="home-counters">
        <button onClick={() => onNavigate('library')}>
          <Glyph name="book" />
          <strong>{books.length}</strong>
          <span>Books</span>
        </button>
        <button onClick={() => onNavigate('notes')}>
          <Glyph name="note" />
          <strong>{notes ?? '—'}</strong>
          <span>Notes</span>
        </button>
        <button onClick={() => onNavigate('study')}>
          <Glyph name="study" />
          <strong>{due ?? '—'}</strong>
          <span>Due cards</span>
        </button>
      </div>
      <div className="section-heading">
        <h2>Shortcuts</h2>
      </div>
      <div className="home-actions">
        {[
          {
            page: 'classics',
            icon: 'compass',
            title: 'Classics',
          },
          {
            page: 'queue',
            icon: 'headphones',
            title: 'Listening queue',
          },
          {
            page: 'study',
            icon: 'study',
            title: 'Study tools',
          },
          {
            page: 'ai',
            icon: 'sparkles',
            title: 'AI handoff',
          },
        ].map((item) => (
          <button
            key={item.page}
            onClick={() => onNavigate(item.page as 'classics' | 'queue' | 'study' | 'ai')}
          >
            <span className="home-action-icon">
              <Glyph name={item.icon} />
            </span>
            <span>
              <strong>{item.title}</strong>
            </span>
            <Glyph name="arrow" />
          </button>
        ))}
      </div>
    </div>
  );
}
