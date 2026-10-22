import { useState } from 'react';
import { call, native } from './api';
import { Glyph } from './Glyph';
import type { Book } from './types';

const tasks = [
  {
    id: 'explain',
    icon: 'sparkles',
    name: 'Explain',
    instruction:
      'Explain this passage clearly. Define unfamiliar terms and give a concrete example. Distinguish what the author says from your interpretation.',
  },
  {
    id: 'summary',
    icon: 'note',
    name: 'Summarise',
    instruction:
      'Summarise the main ideas in this passage, preserving important qualifications. Use only the supplied source and flag anything it does not establish.',
  },
  {
    id: 'cards',
    icon: 'study',
    name: 'Flashcards',
    instruction:
      'Create concise question-and-answer flashcards from this passage. Each card should test one idea. Return a table with Question and Answer columns so I can review and add them to my Vern deck.',
  },
  {
    id: 'quiz',
    icon: 'check',
    name: 'Quiz',
    instruction:
      'Create a multiple-choice quiz from this passage. Return a JSON array of objects with question, options (2–4 distinct strings), answer (the exact correct option text), and explanation. Use only facts established by the passage. I will preview and import the reply in Vern’s Study Hub.',
  },
  {
    id: 'notes',
    icon: 'edit',
    name: 'Study notes',
    instruction:
      'Turn this passage into structured study notes, with key ideas, definitions and questions for further reading. Cite short phrases from the supplied passage when helpful.',
  },
  { id: 'custom', icon: 'settings', name: 'Your own task', instruction: '' },
];
const assistants = [
  { id: 'chatgpt', name: 'ChatGPT', badge: 'C' },
  { id: 'gemini', name: 'Gemini', badge: 'G' },
  { id: 'claude', name: 'Claude', badge: 'Cl' },
  { id: 'copilot', name: 'Copilot', badge: 'Co' },
  { id: 'perplexity', name: 'Perplexity', badge: 'P' },
];
export function AiHandoff({
  books,
  active,
  onError,
  onStudy,
  initial,
}: {
  books: Book[];
  active?: Book;
  onError: (error: unknown) => void;
  onStudy: () => void;
  initial?: { bookId: string; text: string };
}) {
  const [bookId, setBookId] = useState(initial?.bookId ?? '');
  const [task, setTask] = useState('explain');
  const [instructions, setInstructions] = useState(tasks[0].instruction);
  const [passage, setPassage] = useState(initial?.text.slice(0, 12000) ?? '');
  const [assistant, setAssistant] = useState('chatgpt');
  const [status, setStatus] = useState('');
  const book = books.find((book) => book.id === bookId) ?? active ?? books[0];
  const prompt = `${instructions.trim()}\n\n${book ? `Source: ${book.title}${book.author ? ` — ${book.author}` : ''}\n` : ''}Passage:\n${passage.trim()}`;
  async function copy(open: boolean) {
    try {
      await navigator.clipboard.writeText(prompt);
      setStatus('Prompt copied. Paste it into your assistant when you are ready.');
      if (open) await call('open_assistant', { id: assistant });
    } catch (error) {
      onError(error);
    }
  }
  return (
    <div className="page ai-page">
      <span className="eyebrow">A LITTLE HELP THINKING IT THROUGH</span>
      <h1>
        AI handoff<span className="heading-dot">.</span>
      </h1>
      <p className="page-description">
        Prepare a prompt for an assistant you already use. You choose what leaves Vern.
      </p>
      <div className="handoff-layout">
        <section className="settings-card">
          <div className="section-heading">
            <Glyph name="sparkles" />
            <h2>What would you like to do?</h2>
          </div>
          <div className="task-choices">
            {tasks.map((item) => (
              <button
                key={item.id}
                aria-pressed={task === item.id}
                onClick={() => {
                  setTask(item.id);
                  setInstructions(item.instruction);
                  setStatus('');
                }}
              >
                <Glyph name={item.icon} />
                {item.name}
              </button>
            ))}
          </div>
          <label>
            Instructions
            <textarea
              aria-label="AI instructions"
              maxLength={5000}
              value={instructions}
              onChange={(e) => setInstructions(e.target.value)}
              placeholder="Ask in your own words…"
            />
          </label>
          <div className="settings-pair">
            <label>
              Reading
              <select
                aria-label="AI source reading"
                value={book?.id ?? ''}
                onChange={(e) => setBookId(e.target.value)}
              >
                <option value="" disabled>
                  Choose a reading
                </option>
                {books.map((book) => (
                  <option key={book.id} value={book.id}>
                    {book.title}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="secondary"
              disabled={!book || book.textAvailable === false}
              onClick={() => {
                setPassage(book!.chunks[book!.position].slice(0, 12000));
                setStatus('');
              }}
            >
              <Glyph name="book" />
              Use current passage
            </button>
          </div>
          <label>
            Passage or context
            <textarea
              aria-label="AI passage or context"
              maxLength={12000}
              value={passage}
              onChange={(e) => {
                setPassage(e.target.value);
                setStatus('');
              }}
              placeholder="Paste a passage, or use your current reading position."
            />
          </label>
          <small className="muted">
            {passage.length.toLocaleString()} / 12,000 characters. You can edit the passage before
            copying.
          </small>
        </section>
        <aside className="settings-card">
          <h2>Choose your assistant</h2>
          <div className="assistant-choices">
            {assistants.map((item) => (
              <button
                key={item.id}
                aria-label={item.name}
                aria-pressed={assistant === item.id}
                onClick={() => setAssistant(item.id)}
              >
                <span className={`assistant-badge ${item.id}`} aria-hidden="true">
                  {item.badge}
                </span>
                <span>{item.name}</span>
                {assistant === item.id && <Glyph name="check" />}
              </button>
            ))}
          </div>
          <details className="prompt-preview">
            <summary>Preview prepared prompt</summary>
            <pre>{prompt}</pre>
          </details>
          <button
            className="primary full"
            disabled={!instructions.trim() || !passage.trim()}
            onClick={() => void copy(false)}
          >
            <Glyph name="copy" />
            Copy prompt
          </button>
          <button
            className="secondary full"
            disabled={!native || !instructions.trim() || !passage.trim()}
            onClick={() => void copy(true)}
          >
            <Glyph name="external" />
            Copy & open assistant
          </button>
          {status && <p role="status">{status}</p>}
          <p className="muted">
            Opens the assistant’s website in your browser. Paste and send the prompt there. Replies
            can be pasted into Study Hub and reviewed before saving.
          </p>
          <button className="secondary" onClick={onStudy}>
            <Glyph name="study" />
            Import a study reply
          </button>
        </aside>
      </div>
    </div>
  );
}
