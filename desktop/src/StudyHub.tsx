import { useEffect, useState } from 'react';
import { api, native } from './api';
import { Glyph } from './Glyph';
import { Modal, StudyPage } from './WorkspacePages';
import { parseCards, parseQuiz } from './study-import';
import type { Book, CardInput, Quiz, QuizQuestion } from './types';

export function StudyHub(props: {
  books: Book[];
  seed: CardInput | null;
  onSeedUsed: () => void;
  onOpen: (book: Book, position: number) => void;
  onError: (e: unknown) => void;
  onTools: () => void;
  onAi: () => void;
  onNotes: () => void;
}) {
  const [view, setView] = useState<'overview' | 'cards' | 'quizzes'>(
    props.seed ? 'cards' : 'overview',
  );
  const [quizzes, setQuizzes] = useState<Quiz[]>([]);
  const [counts, setCounts] = useState({
    cards: 0,
    due: 0,
    decks: 0,
    notes: 0,
    bookmarks: 0,
    vocabulary: 0,
  });
  const [loading, setLoading] = useState(true);
  const [importing, setImporting] = useState<'cards' | 'quiz' | null>(null);
  const [editing, setEditing] = useState<Quiz | null>(null);
  const [practice, setPractice] = useState<Quiz | null>(null);
  const [answers, setAnswers] = useState<number[]>([]);
  const [result, setResult] = useState<Quiz | null>(null);
  const [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState<string | null>(null);
  async function refresh() {
    const [cards, notes, prefs, sets] = await Promise.all([
      api.flashcards(),
      api.allAnnotations(),
      api.settings(),
      api.quizzes(),
    ]);
    setQuizzes(sets);
    setCounts({
      cards: cards.length,
      due: cards.filter((c) => !c.recall || c.dueAt <= Date.now()).length,
      decks: new Set(cards.map((c) => c.deck)).size,
      notes: notes.filter((n) => n.kind === 'note').length,
      bookmarks: notes.filter((n) => n.kind === 'bookmark').length,
      vocabulary: prefs.vocabulary.length,
    });
  }
  useEffect(() => {
    let mounted = true;
    Promise.all([api.flashcards(), api.allAnnotations(), api.settings(), api.quizzes()])
      .then(([cards, notes, prefs, sets]) => {
        if (!mounted) return;
        setQuizzes(sets);
        setCounts({
          cards: cards.length,
          due: cards.filter((c) => !c.recall || c.dueAt <= Date.now()).length,
          decks: new Set(cards.map((c) => c.deck)).size,
          notes: notes.filter((n) => n.kind === 'note').length,
          bookmarks: notes.filter((n) => n.kind === 'bookmark').length,
          vocabulary: prefs.vocabulary.length,
        });
      })
      .catch(props.onError)
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [props.onError]);
  async function run(action: () => Promise<void>) {
    setBusy(true);
    try {
      await action();
    } catch (e) {
      props.onError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <div className="study-tabs" role="group" aria-label="Study collections">
        {(['overview', 'cards', 'quizzes'] as const).map((v) => (
          <button
            key={v}
            className={v === view ? 'primary small' : 'secondary small'}
            onClick={() => {
              setView(v);
              setPractice(null);
              setResult(null);
              if (v === 'overview') void refresh().catch(props.onError);
            }}
          >
            {v === 'overview' ? 'Overview' : v === 'cards' ? 'Flashcards' : 'Quizzes'}
          </button>
        ))}
      </div>
      {view === 'cards' && <StudyPage {...props} />}
      {view !== 'cards' && (
        <div className="page study-hub">
          <div className="page-heading">
            <div>
              <span className="eyebrow">MAKE ROOM FOR WHAT YOU LEARN</span>
              <h1>
                {view === 'overview' ? 'Study Hub' : 'Quizzes'}
                <span className="heading-dot">.</span>
              </h1>
              <p>
                {view === 'overview'
                  ? 'Your cards, quizzes and reading discoveries, together.'
                  : 'Practise, check explanations and keep your best score.'}
              </p>
            </div>
            <div className="action-row">
              <button
                className="secondary"
                disabled={!native || loading}
                onClick={() => {
                  setEditing(null);
                  setImporting('cards');
                }}
              >
                <Glyph name="plus" />
                Paste cards
              </button>
              <button
                className="primary"
                disabled={!native || loading}
                onClick={() => {
                  setEditing(null);
                  setImporting('quiz');
                }}
              >
                <Glyph name="plus" />
                Create or paste quiz
              </button>
            </div>
          </div>
          {loading && <p role="status">Opening your study collections…</p>}
          {view === 'overview' && (
            <>
              <section className="study-review-banner">
                <div>
                  <span className="eyebrow">READY WHEN YOU ARE</span>
                  <h2>{counts.due} cards ready to review</h2>
                  <p>{counts.decks} decks · Review ratings set the next due date.</p>
                </div>
                <button className="primary" onClick={() => setView('cards')}>
                  Review flashcards
                </button>
              </section>
              <div className="study-collection-grid">
                {[
                  {
                    name: 'Flashcards',
                    count: counts.cards,
                    icon: 'study',
                    open: () => setView('cards'),
                  },
                  {
                    name: 'Quizzes',
                    count: quizzes.length,
                    icon: 'check',
                    open: () => setView('quizzes'),
                  },
                  {
                    name: 'Vocabulary',
                    count: counts.vocabulary,
                    icon: 'book',
                    open: props.onTools,
                  },
                  {
                    name: 'Bookmarks',
                    count: counts.bookmarks,
                    icon: 'bookmark',
                    open: props.onNotes,
                  },
                  { name: 'Book notes', count: counts.notes, icon: 'note', open: props.onNotes },
                ].map((item) => (
                  <button className="study-collection" key={item.name} onClick={item.open}>
                    <Glyph name={item.icon} />
                    <strong>{item.name}</strong>
                    <span>{item.count} saved</span>
                  </button>
                ))}
                <button className="study-collection" onClick={props.onAi}>
                  <Glyph name="sparkles" />
                  <strong>Create with AI</strong>
                  <span>Prepare a prompt, then paste the reply here.</span>
                </button>
              </div>
            </>
          )}
          {view === 'quizzes' && !practice && (
            <div className="quiz-list">
              {!loading && !quizzes.length && (
                <div className="empty-library">
                  <div>
                    <h2>Your first quiz starts here</h2>
                    <p>
                      Write questions or paste a quiz reply. Review the questions before saving.
                    </p>
                  </div>
                </div>
              )}
              {quizzes.map((quiz) => (
                <article className="settings-card" key={quiz.id}>
                  <div>
                    <h2>{quiz.title}</h2>
                    <p>
                      {quiz.questions.length} questions · {quiz.attempts} attempts · Best:{' '}
                      {quiz.bestScore === null
                        ? 'not attempted'
                        : `${quiz.bestScore}/${quiz.questions.length}`}
                    </p>
                    <p className="muted">{props.books.find((b) => b.id === quiz.bookId)?.title}</p>
                  </div>
                  <div className="action-row">
                    <button
                      className="primary"
                      onClick={() => {
                        setPractice(quiz);
                        setAnswers(Array(quiz.questions.length).fill(-1));
                        setResult(null);
                      }}
                    >
                      Start quiz
                    </button>
                    <button
                      className="secondary"
                      disabled={busy}
                      onClick={() => {
                        setEditing(quiz);
                        setImporting('quiz');
                      }}
                    >
                      Edit quiz
                    </button>
                    <button
                      className="icon-button"
                      aria-label={`Delete ${quiz.title}`}
                      disabled={busy}
                      onClick={() => setDeleting(quiz.id)}
                    >
                      <Glyph name="trash" />
                    </button>
                  </div>
                  {deleting === quiz.id && (
                    <div className="inline-confirm">
                      <p>Delete this quiz and its scores?</p>
                      <button
                        className="secondary"
                        disabled={busy}
                        onClick={() =>
                          void run(async () => {
                            await api.deleteQuiz(quiz.id);
                            setDeleting(null);
                            await refresh();
                          })
                        }
                      >
                        Delete quiz
                      </button>
                      <button className="secondary" onClick={() => setDeleting(null)}>
                        Keep quiz
                      </button>
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
          {practice && (
            <section className="quiz-practice">
              <div className="page-heading">
                <h2>{practice.title}</h2>
                <button
                  className="secondary"
                  disabled={busy}
                  onClick={() => {
                    setPractice(null);
                    setResult(null);
                  }}
                >
                  Close practice
                </button>
              </div>
              {result && (
                <p className="quiz-score" role="status">
                  Score {result.lastScore}/{result.questions.length} · Best {result.bestScore}/
                  {result.questions.length}
                </p>
              )}
              {practice.questions.map((q, i) => (
                <fieldset
                  className="quiz-question settings-card"
                  key={i}
                  disabled={!!result || busy}
                >
                  <legend>
                    {i + 1}. {q.question}
                  </legend>
                  {q.options.map((o, j) => (
                    <label key={j} className="quiz-choice">
                      <input
                        type="radio"
                        name={`question-${i}`}
                        checked={answers[i] === j}
                        onChange={() =>
                          setAnswers((a) => a.map((v, index) => (index === i ? j : v)))
                        }
                      />
                      <span>{o}</span>
                      {result && o === q.answer && <strong>Correct answer</strong>}
                    </label>
                  ))}
                  {result && <p>{q.explanation || `Answer: ${q.answer}`}</p>}
                </fieldset>
              ))}
              {!result && (
                <button
                  className="primary"
                  disabled={busy || answers.some((a) => a < 0)}
                  onClick={() =>
                    void run(async () => {
                      const scored = await api.submitQuiz(practice.id, answers);
                      setResult(scored);
                      await refresh();
                    })
                  }
                >
                  Check answers
                </button>
              )}
              {result && (
                <button
                  className="primary"
                  onClick={() => {
                    setAnswers(Array(practice.questions.length).fill(-1));
                    setResult(null);
                  }}
                >
                  Try again
                </button>
              )}
            </section>
          )}
        </div>
      )}
      {importing && (
        <StudyImport
          kind={importing}
          existing={editing}
          books={props.books}
          onClose={() => {
            setImporting(null);
            setEditing(null);
          }}
          onSaved={async () => {
            await refresh();
            setView(importing === 'cards' ? 'cards' : 'quizzes');
            setImporting(null);
            setEditing(null);
          }}
        />
      )}
    </>
  );
}

function StudyImport({
  kind,
  existing,
  books,
  onClose,
  onSaved,
}: {
  kind: 'cards' | 'quiz';
  existing: Quiz | null;
  books: Book[];
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [title, setTitle] = useState(existing?.title ?? 'My study set');
  const [bookId, setBookId] = useState(existing?.bookId ?? '');
  const [text, setText] = useState(existing ? JSON.stringify(existing.questions, null, 2) : '');
  const [items, setItems] = useState<{ front: string; back: string }[] | QuizQuestion[] | null>(
    null,
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  function preview() {
    try {
      setItems(kind === 'cards' ? parseCards(text) : parseQuiz(text));
      setError('');
    } catch (e) {
      setItems(null);
      setError(String(e).replace(/^Error: /, ''));
    }
  }
  async function save() {
    if (!items) return;
    setBusy(true);
    setError('');
    try {
      if (kind === 'cards')
        await api.importFlashcards(
          (items as { front: string; back: string }[]).map((c) => ({
            ...c,
            deck: title,
            bookId: null,
            position: null,
          })),
        );
      else
        await api.saveQuiz({
          id: existing?.id,
          title,
          bookId: bookId || null,
          questions: items as QuizQuestion[],
        });
      await onSaved();
    } catch (e) {
      setError(String(e).replace(/^Error: /, ''));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={
        kind === 'cards' ? 'Paste flashcards' : existing ? 'Edit quiz' : 'Create or paste quiz'
      }
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form
        className="workspace-form"
        onSubmit={(e) => {
          e.preventDefault();
          if (items) void save();
          else preview();
        }}
      >
        <label>
          {kind === 'cards' ? 'Deck name' : 'Quiz title'}
          <input
            autoFocus
            required
            maxLength={200}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </label>
        {kind === 'quiz' && (
          <label>
            Source book (optional)
            <select value={bookId} onChange={(e) => setBookId(e.target.value)}>
              <option value="">No source book</option>
              {books.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.title}
                </option>
              ))}
            </select>
          </label>
        )}
        <p className="muted">
          {kind === 'cards'
            ? 'Paste JSON cards, a Term | Definition table, or Q: / A: pairs.'
            : 'Use JSON questions with options and answer, or write Q:, A), B), Answer: A and Explanation:. Answers must match a choice.'}{' '}
          Up to 200 items. Only complete items appear in the preview.
        </p>
        {existing && <p>Changing questions resets this quiz’s scores. Renaming keeps them.</p>}
        <label>
          {kind === 'cards' ? 'Flashcard reply' : 'Quiz questions'}
          <textarea
            required
            maxLength={500000}
            rows={10}
            value={text}
            onChange={(e) => {
              setText(e.target.value);
              setItems(null);
            }}
            placeholder={
              kind === 'cards'
                ? 'Q: What is recall?\nA: Retrieving a memory.'
                : 'Q: Which is an element?\nA) Carbon\nB) Water\nAnswer: A\nExplanation: Carbon is an element.'
            }
          />
        </label>
        {items && (
          <section className="study-import-preview">
            <h3>
              {items.length} {kind === 'cards' ? 'cards' : 'questions'} ready to save
            </h3>
            {items.map((item, i) => (
              <article key={i}>
                {'front' in item ? (
                  <>
                    <strong>{item.front}</strong>
                    <p>{item.back}</p>
                  </>
                ) : (
                  <>
                    <strong>{item.question}</strong>
                    <ol type="A">
                      {item.options.map((o) => (
                        <li key={o}>
                          {o}
                          {o === item.answer ? ' ✓' : ''}
                        </li>
                      ))}
                    </ol>
                    <p>{item.explanation}</p>
                  </>
                )}
              </article>
            ))}
          </section>
        )}
        {error && <p role="alert">{error}</p>}
        <div className="action-row">
          <button className="primary" disabled={busy}>
            {busy ? 'Saving…' : items ? 'Save study set' : 'Preview items'}
          </button>
          <button type="button" className="secondary" disabled={busy} onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
