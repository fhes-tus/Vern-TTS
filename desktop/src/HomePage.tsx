import { useEffect, useState } from 'react';
import { api } from './api';
import { Glyph } from './Glyph';
import type { Book } from './types';

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
  onNavigate: (page: 'library' | 'classics' | 'study' | 'notes' | 'queue' | 'ai') => void;
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
      <span className="eyebrow">YOUR LITTLE READING WORLD</span>
      <h1>
        A little time for you<span className="heading-dot">.</span>
      </h1>
      <p className="page-description">Pick up a story. Keep a thought. Learn something new.</p>
      <section className="home-hero">
        <div>
          <span className="eyebrow">
            {active ? 'PICK UP WHERE YOU LEFT OFF' : 'MAKE YOURSELF AT HOME'}
          </span>
          <h2>{active?.title ?? 'A reading room of your own.'}</h2>
          <p>
            {active
              ? active.author || 'From your personal library'
              : 'Bring a book you love, or discover an old favourite in Classics.'}
          </p>
          {active && (
            <div className="continue-progress">
              <progress value={active.position + 1} max={active.chunks.length} />
              <span>{Math.round(((active.position + 1) / active.chunks.length) * 100)}% read</span>
            </div>
          )}
          <div className="action-row">
            <button className="primary" onClick={() => (active ? onOpen(active) : onImport())}>
              <Glyph name="book" />
              {active ? 'Continue reading' : 'Import your first book'}
            </button>
            <button className="secondary" onClick={() => onNavigate('classics')}>
              <Glyph name="compass" />
              Explore Classics
            </button>
          </div>
        </div>
        <div className="home-book-stack" aria-hidden="true">
          <img src="/covers/classic_secret_garden.jpg" alt="" />
          <img src="/covers/classic_alice_wonderland.jpg" alt="" />
          <img src="/covers/classic_pride_and_prejudice.jpg" alt="" />
        </div>
      </section>
      <div className="home-counters">
        <button onClick={() => onNavigate('library')}>
          <Glyph name="book" />
          <strong>{books.length}</strong>
          <span>books on your shelf</span>
        </button>
        <button onClick={() => onNavigate('notes')}>
          <Glyph name="note" />
          <strong>{notes ?? '—'}</strong>
          <span>thoughts kept</span>
        </button>
        <button onClick={() => onNavigate('study')}>
          <Glyph name="study" />
          <strong>{due ?? '—'}</strong>
          <span>cards ready to review</span>
        </button>
      </div>
      <div className="section-heading">
        <h2>Make a little room</h2>
        <span className="muted">Everything has its place.</span>
      </div>
      <div className="home-actions">
        {[
          {
            page: 'classics',
            icon: 'compass',
            title: 'Find your next story',
            text: 'Browse the Classics catalogue.',
          },
          {
            page: 'queue',
            icon: 'headphones',
            title: 'Let the words come to you',
            text: 'Your listening queue and voices.',
          },
          {
            page: 'study',
            icon: 'study',
            title: 'Turn pages into knowledge',
            text: 'Flashcards and spaced practice.',
          },
          {
            page: 'ai',
            icon: 'sparkles',
            title: 'Think it through',
            text: 'Prepare a passage for your assistant.',
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
              <small>{item.text}</small>
            </span>
            <Glyph name="arrow" />
          </button>
        ))}
      </div>
    </div>
  );
}
