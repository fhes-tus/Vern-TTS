import { useState } from 'react';
import { native } from './api';
import type { Book, CardInput, PronunciationRule, Settings, Vocabulary } from './types';

export function ReadingToolsPage({
  settings,
  books,
  source,
  onSave,
  onOpen,
  onCard,
}: {
  settings: Settings;
  books: Book[];
  source: { bookId: string; position: number; word?: string } | null;
  onSave: (patch: Partial<Settings>) => Promise<boolean>;
  onOpen: (book: Book, position: number) => void;
  onCard: (card: CardInput) => void;
}) {
  const [word, setWord] = useState<Vocabulary>({
    id: crypto.randomUUID(),
    word: source?.word ?? '',
    definition: '',
    bookId: source?.bookId ?? null,
    position: source?.position ?? null,
  });
  const [rule, setRule] = useState<PronunciationRule>({
    id: crypto.randomUUID(),
    find: '',
    replaceWith: '',
    enabled: true,
  });
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [deleting, setDeleting] = useState<string | null>(null);
  async function save(patch: Partial<Settings>, done?: () => void) {
    setBusy(true);
    setMessage('');
    try {
      if (await onSave(patch)) {
        done?.();
        setMessage('Saved on this device.');
      }
    } finally {
      setBusy(false);
    }
  }
  function resetWord() {
    setWord({ id: crypto.randomUUID(), word: '', definition: '', bookId: null, position: null });
  }
  function resetRule() {
    setRule({ id: crypto.randomUUID(), find: '', replaceWith: '', enabled: true });
  }
  const words = settings.vocabulary.filter((entry) =>
    `${entry.word} ${entry.definition}`.toLowerCase().includes(query.toLowerCase()),
  );
  return (
    <div className="page reading-tools-page">
      <span className="eyebrow">MAKE THE WORDS YOUR OWN</span>
      <h1>
        Reading tools<span className="heading-dot">.</span>
      </h1>
      <p className="page-description">
        Keep your vocabulary and teach your offline voices how to say a word or phrase.
      </p>
      {message && <p role="status">{message}</p>}
      <section className="settings-card">
        <h2>Vocabulary notebook</h2>
        <p className="muted">
          Record a word and its meaning, then turn it into a flashcard. Definitions are written by
          you; online dictionary and translation lookup are still being built.
        </p>
        <form
          className="workspace-form"
          onSubmit={(e) => {
            e.preventDefault();
            void save(
              {
                vocabulary: [
                  ...settings.vocabulary.filter((entry) => entry.id !== word.id),
                  { ...word, word: word.word.trim(), definition: word.definition.trim() },
                ],
              },
              resetWord,
            );
          }}
        >
          <label>
            Word or phrase
            <input
              required
              maxLength={100}
              value={word.word}
              onChange={(e) => setWord({ ...word, word: e.target.value })}
            />
          </label>
          <label>
            Meaning
            <textarea
              required
              maxLength={2000}
              value={word.definition}
              onChange={(e) => setWord({ ...word, definition: e.target.value })}
            />
          </label>
          {word.bookId && (
            <p className="muted">
              Source: {books.find((b) => b.id === word.bookId)?.title} · passage{' '}
              {(word.position ?? 0) + 1}{' '}
              <button
                type="button"
                className="text-button"
                onClick={() => setWord({ ...word, bookId: null, position: null })}
              >
                Remove source link
              </button>
            </p>
          )}
          <div className="action-row">
            <button
              className="primary"
              disabled={busy || !native || !word.word.trim() || !word.definition.trim()}
            >
              Save vocabulary
            </button>
            <button className="secondary" type="button" disabled={busy} onClick={resetWord}>
              Clear form
            </button>
          </div>
        </form>
        <div className="workspace-filters">
          <label>
            Search vocabulary
            <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} />
          </label>
        </div>
        <div className="annotation-grid">
          {words.map((entry) => {
            const book = books.find((b) => b.id === entry.bookId);
            return (
              <article className="saved-note" key={entry.id}>
                <h3>{entry.word}</h3>
                <p>{entry.definition}</p>
                <div className="action-row">
                  <button className="text-button" disabled={busy} onClick={() => setWord(entry)}>
                    Edit
                  </button>
                  <button
                    className="text-button"
                    disabled={busy}
                    onClick={() =>
                      onCard({
                        deck: 'Vocabulary',
                        front: entry.word,
                        back: entry.definition,
                        bookId: entry.bookId,
                        position: entry.position,
                      })
                    }
                  >
                    Create flashcard
                  </button>
                  {book && entry.position !== null && (
                    <button className="text-button" onClick={() => onOpen(book, entry.position!)}>
                      Source passage →
                    </button>
                  )}
                  <button
                    className="text-button"
                    disabled={busy}
                    onClick={() => setDeleting(entry.id)}
                  >
                    Delete
                  </button>
                </div>
                {deleting === entry.id && (
                  <div className="action-row">
                    <span>Delete this vocabulary entry?</span>
                    <button
                      className="danger small"
                      disabled={busy}
                      onClick={() =>
                        void save(
                          { vocabulary: settings.vocabulary.filter((v) => v.id !== entry.id) },
                          () => {
                            setDeleting(null);
                            if (word.id === entry.id) resetWord();
                          },
                        )
                      }
                    >
                      Delete entry
                    </button>
                    <button className="secondary small" onClick={() => setDeleting(null)}>
                      Keep
                    </button>
                  </div>
                )}
              </article>
            );
          })}
        </div>
        {!words.length && <p className="muted">No vocabulary matches this view.</p>}
      </section>
      <section className="settings-card">
        <h2>Pronunciation rules</h2>
        <p className="muted">
          Match whole words or phrases without case sensitivity. Replacements affect speech; your
          book text and passage anchors stay intact. Longer overlapping phrases take priority.
        </p>
        <form
          className="workspace-form"
          onSubmit={(e) => {
            e.preventDefault();
            void save(
              {
                pronunciationRules: [
                  ...settings.pronunciationRules.filter((entry) => entry.id !== rule.id),
                  { ...rule, find: rule.find.trim() },
                ],
              },
              resetRule,
            );
          }}
        >
          <label>
            Written word or phrase
            <input
              required
              maxLength={100}
              value={rule.find}
              onChange={(e) => setRule({ ...rule, find: e.target.value })}
            />
          </label>
          <label>
            Say it as
            <input
              maxLength={200}
              value={rule.replaceWith}
              onChange={(e) => setRule({ ...rule, replaceWith: e.target.value })}
              placeholder="Leave empty to skip this phrase"
            />
          </label>
          <div className="action-row">
            <button className="primary" disabled={busy || !native || !rule.find.trim()}>
              Save pronunciation
            </button>
            <button className="secondary" type="button" disabled={busy} onClick={resetRule}>
              Clear form
            </button>
          </div>
        </form>
        {settings.pronunciationRules.map((entry) => (
          <article className="saved-note" key={entry.id}>
            <h3>{entry.find}</h3>
            <p>Say: {entry.replaceWith || '(skip)'}</p>
            <div className="action-row">
              <button
                className="secondary small"
                disabled={busy}
                aria-pressed={entry.enabled}
                onClick={() =>
                  void save({
                    pronunciationRules: settings.pronunciationRules.map((r) =>
                      r.id === entry.id ? { ...r, enabled: !r.enabled } : r,
                    ),
                  })
                }
              >
                {entry.enabled ? 'Enabled' : 'Disabled'}
              </button>
              <button className="text-button" disabled={busy} onClick={() => setRule(entry)}>
                Edit
              </button>
              <button
                className="text-button"
                disabled={busy}
                onClick={() =>
                  void save(
                    {
                      pronunciationRules: settings.pronunciationRules.filter(
                        (r) => r.id !== entry.id,
                      ),
                    },
                    () => {
                      if (rule.id === entry.id) resetRule();
                    },
                  )
                }
              >
                Delete
              </button>
            </div>
          </article>
        ))}
      </section>
    </div>
  );
}
