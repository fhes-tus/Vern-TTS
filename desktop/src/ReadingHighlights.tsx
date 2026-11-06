import { useEffect, useLayoutEffect, useState, type RefObject } from 'react';
import { api, native } from './api';
import { Modal } from './WorkspacePages';
import { highlightPresets, highlightTone, useReadingHighlights } from './use-reading-highlights';
import type { Book, HighlightColor, HighlightInput, ReadingHighlight } from './types';

type Selection = { position: number; endPosition?: number };
type Props = {
  book: Book;
  viewport: RefObject<HTMLElement | null>;
  pageStarts?: number[];
  open: boolean;
  request: Selection | null;
  onClose: () => void;
  onSeek: (position: number) => Promise<void>;
};

const passageLabel = (item: { position: number; endPosition: number }) =>
  item.position === item.endPosition
    ? `Passage ${item.position + 1}`
    : `Passages ${item.position + 1}–${item.endPosition + 1}`;

export function ReadingHighlights({
  book,
  viewport,
  pageStarts,
  open,
  request,
  onClose,
  onSeek,
}: Props) {
  const [items, setItems] = useState<ReadingHighlight[]>([]);
  const [draft, setDraft] = useState<HighlightInput | null>(null);
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [status, setStatus] = useState('');
  const [deleting, setDeleting] = useState('');
  const [page, setPage] = useState(0);
  useReadingHighlights(viewport, book, items, pageStarts);

  useEffect(() => {
    let live = true;
    setLoading(true);
    api
      .highlights(book.id)
      .then((values) => {
        if (!Array.isArray(values)) throw new Error('Could not read this book’s highlights.');
        if (live) {
          setItems(values);
          setError('');
        }
      })
      .catch((e) => {
        if (live) setError(String(e).replace(/^Error: /, ''));
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [book.id, reload]);

  useLayoutEffect(() => {
    if (!open) return;
    setDeleting('');
    setDraft(
      request
        ? {
            bookId: book.id,
            position: request.position,
            endPosition: request.endPosition ?? request.position,
            color: 'yellow',
            note: '',
          }
        : null,
    );
  }, [open, request, book.id]);

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

  const close = () => {
    if (!busy) onClose();
  };
  const announce = status ? (
    <span role="status" className="sr-only">
      {status}
    </span>
  ) : null;
  if (!open) return announce;
  const currentPage = Math.min(page, Math.max(0, Math.ceil(items.length / 50) - 1));
  const listed = [...items].sort((a, b) => a.position - b.position || a.createdAt - b.createdAt);
  let customColor = '#f0d36b';
  if (draft) {
    const tone = highlightTone(draft.color);
    if (draft.color.startsWith('#')) customColor = draft.color;
    else if (tone) {
      const stored = getComputedStyle(document.documentElement)
        .getPropertyValue(`--highlight-${draft.color}`)
        .trim();
      if (/^#[0-9a-f]{6}$/i.test(stored)) customColor = stored;
    }
  }
  return (
    <>
      {announce}
      <Modal
        title={draft ? (draft.id ? 'Edit highlight' : 'Highlight passages') : 'Reading highlights'}
        onClose={close}
      >
        <div className="reading-highlights">
          <p className="highlight-book" title={book.title}>
            {book.title}
          </p>
          {loading && <p role="status">Loading highlights…</p>}
          {error && (
            <div className="highlight-error">
              <p role="alert">{error}</p>
              <button
                type="button"
                className="text-button"
                disabled={busy}
                onClick={() => setReload((value) => value + 1)}
              >
                Retry loading highlights
              </button>
            </div>
          )}
          {draft ? (
            <form
              className="workspace-form"
              onSubmit={(e) => {
                e.preventDefault();
                void run(async () => {
                  const saved = await api.saveHighlight(draft);
                  setItems((previous) => [
                    ...previous.filter((item) => item.id !== saved.id),
                    saved,
                  ]);
                  setReload((value) => value + 1);
                  setStatus('Highlight saved.');
                  onClose();
                });
              }}
            >
              <p>{passageLabel(draft)}</p>
              <blockquote className="highlight-excerpt">
                {book.chunks[draft.position]?.slice(0, 400)}
              </blockquote>
              <p className="muted">
                Highlights mark the selected passages and stay with this reading.
              </p>
              <fieldset className="highlight-colours" disabled={busy}>
                <legend>Highlight colour</legend>
                {highlightPresets.map((color) => (
                  <label key={color.value}>
                    <input
                      type="radio"
                      name="highlight-colour"
                      checked={draft.color === color.value}
                      onChange={() => setDraft({ ...draft, color: color.value })}
                    />
                    <span
                      className="highlight-swatch"
                      style={{ backgroundColor: highlightTone(color.value)! }}
                      aria-hidden="true"
                    />
                    {color.label}
                  </label>
                ))}
              </fieldset>
              <label>
                Custom highlight colour
                <input
                  type="color"
                  value={customColor}
                  disabled={busy}
                  onChange={(e) => setDraft({ ...draft, color: e.target.value as HighlightColor })}
                />
              </label>
              <label>
                Highlight note (optional)
                <textarea
                  rows={4}
                  maxLength={10000}
                  value={draft.note}
                  disabled={busy}
                  onChange={(e) => setDraft({ ...draft, note: e.target.value })}
                />
              </label>
              {!native && <p className="muted">Open Vern’s desktop window to save highlights.</p>}
              <div className="action-row">
                <button className="primary" disabled={busy || !native}>
                  {busy ? 'Saving…' : 'Save highlight'}
                </button>
                <button type="button" className="secondary" disabled={busy} onClick={close}>
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <>
              <p>
                {items.length} {items.length === 1 ? 'highlight' : 'highlights'} in this reading.
              </p>
              {!items.length && !loading && !error && (
                <p className="muted">
                  Select words in the reader, then choose Highlight passages to keep them here.
                </p>
              )}
              <ul className="reading-highlight-list">
                {listed.slice(currentPage * 50, currentPage * 50 + 50).map((item) => (
                  <li key={item.id}>
                    <div className="highlight-heading">
                      <span
                        className="highlight-swatch"
                        style={{ backgroundColor: highlightTone(item.color) ?? 'transparent' }}
                        aria-hidden="true"
                      />
                      <strong>{passageLabel(item)}</strong>
                    </div>
                    <p>{book.chunks[item.position]?.slice(0, 180)}</p>
                    {item.note && <p>{item.note.slice(0, 400)}</p>}
                    <div className="action-row">
                      <button
                        type="button"
                        className="text-button"
                        disabled={busy}
                        onClick={() =>
                          void run(async () => {
                            await onSeek(item.position);
                            onClose();
                          })
                        }
                      >
                        Read passage {item.position + 1}
                      </button>
                      <button
                        type="button"
                        className="text-button"
                        disabled={busy}
                        onClick={() => {
                          setError('');
                          setDraft({
                            id: item.id,
                            bookId: item.bookId,
                            position: item.position,
                            endPosition: item.endPosition,
                            color: item.color,
                            note: item.note,
                          });
                        }}
                      >
                        Edit highlight
                      </button>
                      <button
                        type="button"
                        className="text-button"
                        disabled={busy}
                        onClick={() => setDeleting(item.id)}
                      >
                        Remove highlight
                      </button>
                    </div>
                    {deleting === item.id && (
                      <div className="highlight-confirm">
                        <p>
                          Remove this highlight and its note? The reading and other notes will stay.
                        </p>
                        <div className="action-row">
                          <button
                            type="button"
                            className="secondary"
                            disabled={busy}
                            onClick={() =>
                              void run(async () => {
                                await api.deleteHighlight(item.id);
                                setItems((values) =>
                                  values.filter((value) => value.id !== item.id),
                                );
                                setDeleting('');
                                setStatus('Highlight removed.');
                              })
                            }
                          >
                            Confirm removal
                          </button>
                          <button
                            type="button"
                            className="text-button"
                            disabled={busy}
                            onClick={() => setDeleting('')}
                          >
                            Keep highlight
                          </button>
                        </div>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
              {items.length > 50 && (
                <div className="action-row">
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy || currentPage === 0}
                    onClick={() => setPage(currentPage - 1)}
                  >
                    Previous highlights
                  </button>
                  <span>
                    {currentPage * 50 + 1}–{Math.min(items.length, currentPage * 50 + 50)} of{' '}
                    {items.length}
                  </span>
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy || (currentPage + 1) * 50 >= items.length}
                    onClick={() => setPage(currentPage + 1)}
                  >
                    Next highlights
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </Modal>
    </>
  );
}
