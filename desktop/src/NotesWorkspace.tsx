import { useEffect, useRef, useState } from 'react';
import { api, native } from './api';
import { Glyph } from './Glyph';
import { NoteAttachments } from './NoteAttachments';
import { Modal, NotesPage } from './WorkspacePages';
import type { Book, NoteInput, Notebook, PersonalNote, NotesSettings, NoteRevision } from './types';
const empty: NoteInput = {
  title: '',
  content: '',
  color: 'paper',
  pinned: false,
  isChecklist: false,
  labelIds: [],
};
const inputFor = (n: PersonalNote): NoteInput => ({
  id: n.id,
  title: n.title,
  content: n.content,
  color: n.color,
  pinned: n.pinned,
  isChecklist: n.isChecklist,
  labelIds: n.labelIds,
});
export function NotesWorkspace(props: {
  settings: NotesSettings;
  onSettings: (settings: NotesSettings) => Promise<boolean>;
  onPreferences: () => void;
  books: Book[];
  onOpen: (book: Book, position: number) => void;
  onError: (e: unknown) => void;
  initialView?: 'personal' | 'books';
  entryVersion?: number;
}) {
  const [view, setView] = useState<'personal' | 'books' | 'trash'>(props.initialView ?? 'personal');
  const lastEntry = useRef(props.entryVersion);
  useEffect(() => {
    if (lastEntry.current === props.entryVersion) return;
    lastEntry.current = props.entryVersion;
    setView(props.initialView ?? 'personal');
  }, [props.entryVersion, props.initialView]);
  const [notes, setNotes] = useState<PersonalNote[]>([]);
  const [notebooks, setNotebooks] = useState<Notebook[]>([]);
  const [query, setQuery] = useState('');
  const [label, setLabel] = useState('');
  const { sort, grid } = props.settings;
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [editor, setEditor] = useState<NoteInput | null>(null);
  const [managing, setManaging] = useState(false);
  const [deleting, setDeleting] = useState<string | null>(null);
  async function refresh() {
    const [n, b] = await Promise.all([api.personalNotes(), api.notebooks()]);
    setNotes(n);
    setNotebooks(b);
  }
  useEffect(() => {
    let live = true;
    Promise.all([api.personalNotes(), api.notebooks()])
      .then(([n, b]) => {
        if (live) {
          setNotes(n);
          setNotebooks(b);
        }
      })
      .catch(props.onError)
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [props.onError]);
  async function run(action: () => Promise<void>) {
    setBusy(true);
    try {
      await action();
      await refresh();
    } catch (e) {
      props.onError(e);
    } finally {
      setBusy(false);
    }
  }
  const visible = notes
    .filter(
      (n) =>
        !!n.deletedAt === (view === 'trash') &&
        (!label || n.labelIds.includes(label)) &&
        `${n.title} ${n.content}`.toLowerCase().includes(query.toLowerCase()),
    )
    .sort(
      (a, b) =>
        Number(b.pinned) - Number(a.pinned) ||
        (sort === 'title'
          ? a.title.localeCompare(b.title)
          : sort === 'created'
            ? b.createdAt - a.createdAt
            : b.updatedAt - a.updatedAt),
    );
  return (
    <>
      <div className="study-tabs" role="group" aria-label="Notes collections">
        <button className="secondary small" onClick={props.onPreferences}>
          <Glyph name="settings" />
          Notes settings
        </button>
        {(['personal', 'books', 'trash'] as const).map((v) => (
          <button
            key={v}
            className={view === v ? 'primary small' : 'secondary small'}
            onClick={() => setView(v)}
          >
            {v === 'personal' ? 'My notes' : v === 'books' ? 'Book notes & bookmarks' : 'Trash'}
          </button>
        ))}
      </div>
      {view === 'books' ? (
        <NotesPage {...props} />
      ) : (
        <div className="page notes-workspace">
          <div className="page-heading">
            <div>
              <h1>
                {view === 'trash' ? 'Trash' : 'Notes'}
                <span className="heading-dot">.</span>
              </h1>
              {view === 'trash' && <p>Restore or permanently delete notes.</p>}
            </div>
            <div className="action-row">
              <button
                className="secondary"
                disabled={!native || loading}
                onClick={() => setManaging(true)}
              >
                <Glyph name="book" />
                Notebooks
              </button>
              {view !== 'trash' && (
                <button
                  className="primary"
                  disabled={!native || loading}
                  onClick={() =>
                    setEditor({
                      ...empty,
                      isChecklist: props.settings.newChecklist,
                      labelIds: label ? [label] : [],
                    })
                  }
                >
                  <Glyph name="plus" />
                  New note
                </button>
              )}
            </div>
          </div>
          <div className="workspace-filters">
            <label>
              Find a note
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search your ideas…"
              />
            </label>
            <label>
              Notebook
              <select value={label} onChange={(e) => setLabel(e.target.value)}>
                <option value="">All notebooks</option>
                {notebooks.map((n) => (
                  <option key={n.id} value={n.id}>
                    {n.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Sort notes
              <select
                value={sort}
                onChange={(e) =>
                  void props.onSettings({
                    ...props.settings,
                    sort: e.target.value as NotesSettings['sort'],
                  })
                }
              >
                <option value="updated">Last edited</option>
                <option value="created">Date created</option>
                <option value="title">Title</option>
              </select>
            </label>
            <button
              className="icon-button"
              aria-label={grid ? 'Show notes as list' : 'Show notes as grid'}
              onClick={() => void props.onSettings({ ...props.settings, grid: !grid })}
            >
              <Glyph name={grid ? 'list' : 'grid'} />
            </button>
          </div>
          <p className="muted" role="status">
            {loading
              ? 'Opening your notes…'
              : `${visible.length} notes${view === 'trash' ? ' in Trash' : ''}`}
          </p>
          <div className={grid ? 'personal-note-grid' : 'personal-note-list'}>
            {visible.map((note) => (
              <article className="personal-note" data-color={note.color} key={note.id}>
                <div className="personal-note-heading">
                  <h2>{note.title || 'Untitled note'}</h2>
                  {note.pinned && (
                    <span aria-label="Pinned">
                      <Glyph name="pin" />
                    </span>
                  )}
                </div>
                {note.isChecklist ? (
                  <div className="note-checklist">
                    {note.content
                      .split('\n')
                      .map((line, index) => ({ line, index }))
                      .sort((a, b) =>
                        props.settings.checkedToBottom
                          ? Number(/^\s*-\s*\[x\]/i.test(a.line)) -
                            Number(/^\s*-\s*\[x\]/i.test(b.line))
                          : a.index - b.index,
                      )
                      .map(
                        ({ line, index: i }) =>
                          line.trim() && (
                            <label key={i} className="note-check">
                              <input
                                type="checkbox"
                                disabled={busy || view === 'trash'}
                                checked={/^\s*-\s*\[x\]/i.test(line)}
                                onChange={() =>
                                  void run(async () => {
                                    const lines = note.content.split('\n');
                                    lines[i] =
                                      `- [${/^\s*-\s*\[x\]/i.test(line) ? ' ' : 'x'}] ${line.replace(/^\s*-\s*\[[ x]\]\s*/i, '')}`;
                                    await api.savePersonalNote({
                                      ...inputFor(note),
                                      content: lines.join('\n'),
                                    });
                                  })
                                }
                              />
                              <span>{line.replace(/^\s*-\s*\[[ x]\]\s*/i, '')}</span>
                            </label>
                          ),
                      )}
                  </div>
                ) : (
                  <p
                    className="personal-note-body"
                    style={{
                      display: props.settings.previewLines ? '-webkit-box' : 'none',
                      WebkitBoxOrient: 'vertical',
                      WebkitLineClamp: props.settings.previewLines,
                      overflow: 'hidden',
                    }}
                  >
                    {note.content.slice(0, 1000)}
                    {note.content.length > 1000 ? '…' : ''}
                  </p>
                )}
                <div className="note-labels">
                  {note.labelIds.map((id) => (
                    <span key={id}>{notebooks.find((n) => n.id === id)?.name}</span>
                  ))}
                </div>
                <small className="muted">{new Date(note.updatedAt).toLocaleDateString()}</small>
                <div className="action-row">
                  {view === 'trash' ? (
                    <>
                      <button
                        className="secondary small"
                        disabled={busy}
                        onClick={() => void run(() => api.trashPersonalNote(note.id, false))}
                      >
                        Restore note
                      </button>
                      <button
                        className="secondary small"
                        disabled={busy}
                        onClick={() => setDeleting(note.id)}
                      >
                        Delete permanently
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        className="secondary small"
                        disabled={busy}
                        onClick={() => setEditor(inputFor(note))}
                      >
                        <Glyph name="edit" />
                        Edit note
                      </button>
                      <button
                        className="icon-button"
                        aria-label={note.pinned ? `Unpin ${note.title}` : `Pin ${note.title}`}
                        disabled={busy}
                        onClick={() =>
                          void run(async () => {
                            await api.savePersonalNote({ ...inputFor(note), pinned: !note.pinned });
                          })
                        }
                      >
                        <Glyph name="pin" />
                      </button>
                      <button
                        className="icon-button"
                        aria-label={`Move ${note.title} to Trash`}
                        disabled={busy}
                        onClick={() => void run(() => api.trashPersonalNote(note.id, true))}
                      >
                        <Glyph name="trash" />
                      </button>
                    </>
                  )}
                </div>
                {deleting === note.id && (
                  <div className="inline-confirm">
                    <p>Permanently delete this note? This cannot be undone.</p>
                    <button
                      className="secondary small"
                      disabled={busy}
                      onClick={() =>
                        void run(async () => {
                          await api.deletePersonalNote(note.id);
                          setDeleting(null);
                        })
                      }
                    >
                      Confirm permanent deletion
                    </button>
                    <button
                      className="secondary small"
                      disabled={busy}
                      onClick={() => setDeleting(null)}
                    >
                      Keep in Trash
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
          {!loading && !visible.length && (
            <div className="empty-library">
              <Glyph name="note" />
              <div>
                <h2>{view === 'trash' ? 'Trash is empty' : 'Make a note of it'}</h2>
                <p>
                  {view === 'trash'
                    ? 'Notes you move here can be restored.'
                    : 'Write a thought, make a checklist, or organise notes into notebooks.'}
                </p>
              </div>
            </div>
          )}
        </div>
      )}
      {editor && (
        <NoteEditor
          settings={props.settings}
          initial={editor}
          notebooks={notebooks}
          onClose={() => setEditor(null)}
          onSaved={refresh}
        />
      )}
      {managing && (
        <NotebookManager
          notebooks={notebooks}
          onChanged={async () => {
            await refresh();
            setLabel('');
          }}
          onClose={() => setManaging(false)}
        />
      )}
    </>
  );
}
function NoteEditor({
  settings,
  initial,
  notebooks,
  onClose,
  onSaved,
}: {
  settings: NotesSettings;
  initial: NoteInput;
  notebooks: Notebook[];
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [draft, setDraft] = useState(initial);
  const [history, setHistory] = useState<NoteRevision[] | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [revisionMessage, setRevisionMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  function persistDraft() {
    return api.savePersonalNote({
      ...draft,
      content: draft.isChecklist
        ? draft.content
            .split('\n')
            .map((l) => (!l.trim() || /^\s*-\s*\[[ x]\]/i.test(l) ? l : `- [ ] ${l}`))
            .join('\n')
        : draft.content,
    });
  }
  async function save() {
    setBusy(true);
    setError('');
    try {
      await persistDraft();
      await onSaved();
      onClose();
    } catch (e) {
      setError(String(e).replace(/^Error: /, ''));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={draft.id ? 'Edit note' : 'New note'}
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
          Note title
          <input
            autoFocus
            maxLength={500}
            value={draft.title}
            onChange={(e) => setDraft({ ...draft, title: e.target.value })}
          />
        </label>
        <label>
          Note text
          <textarea
            className="notes-writing"
            data-paper={settings.paper}
            style={{ fontSize: `${settings.fontScale}em`, lineHeight: settings.lineSpacing }}
            maxLength={200000}
            rows={10}
            value={draft.content}
            onChange={(e) => setDraft({ ...draft, content: e.target.value })}
          />
        </label>
        <label className="note-check">
          <input
            type="checkbox"
            checked={draft.isChecklist}
            onChange={(e) => setDraft({ ...draft, isChecklist: e.target.checked })}
          />
          Checklist (one item per line)
        </label>
        <label className="note-check">
          <input
            type="checkbox"
            checked={draft.pinned}
            onChange={(e) => setDraft({ ...draft, pinned: e.target.checked })}
          />
          Pin this note
        </label>
        <label>
          Note colour
          <select
            value={draft.color}
            onChange={(e) => setDraft({ ...draft, color: e.target.value })}
          >
            {['paper', 'sage', 'rose', 'blue', 'amber'].map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </label>
        {!!notebooks.length && (
          <fieldset className="note-label-picker">
            <legend>Keep in notebooks</legend>
            {notebooks.map((n) => (
              <label className="note-check" key={n.id}>
                <input
                  type="checkbox"
                  checked={draft.labelIds.includes(n.id)}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      labelIds: e.target.checked
                        ? [...draft.labelIds, n.id]
                        : draft.labelIds.filter((id) => id !== n.id),
                    })
                  }
                />
                {n.name}
              </label>
            ))}
          </fieldset>
        )}
        <NoteAttachments
          noteId={draft.id}
          disabled={busy}
          onBusy={setBusy}
          prepare={async () => {
            const saved = await persistDraft();
            setDraft(inputFor(saved));
            await onSaved();
            return saved.id;
          }}
        />
        {draft.id && (
          <section className="note-history">
            <button
              className="text-button"
              type="button"
              disabled={busy}
              aria-expanded={historyOpen}
              onClick={() => {
                if (historyOpen) {
                  setHistoryOpen(false);
                  return;
                }
                setBusy(true);
                setError('');
                void api
                  .noteRevisions(draft.id!)
                  .then((value) => {
                    setHistory(value);
                    setHistoryOpen(true);
                  })
                  .catch((e) => setError(String(e)))
                  .finally(() => setBusy(false));
              }}
            >
              Version history
            </button>
            {historyOpen && (
              <>
                <p className="muted">
                  The last 30 saved versions are kept. Choose a version to review it, then save to
                  apply it.
                </p>
                {history?.length ? (
                  history.map((revision) => (
                    <details key={revision.id}>
                      <summary>
                        {new Date(revision.note.updatedAt).toLocaleString()} ·{' '}
                        {revision.note.title || 'Untitled note'}
                      </summary>
                      <pre>{revision.note.content}</pre>
                      <button
                        type="button"
                        className="secondary small"
                        onClick={() => {
                          setDraft({
                            ...inputFor(revision.note),
                            id: initial.id,
                            labelIds: revision.note.labelIds.filter((id) =>
                              notebooks.some((notebook) => notebook.id === id),
                            ),
                          });
                          setRevisionMessage(
                            'Previous version loaded into the editor. Save note to keep it, or cancel to leave the current note.',
                          );
                        }}
                      >
                        Use this version
                      </button>
                    </details>
                  ))
                ) : (
                  <p className="muted">No previous versions yet.</p>
                )}
              </>
            )}
            {revisionMessage && <p role="status">{revisionMessage}</p>}
          </section>
        )}
        {error && <p role="alert">{error}</p>}
        <div className="action-row">
          <button className="primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save note'}
          </button>
          <button type="button" className="secondary" disabled={busy} onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
function NotebookManager({
  notebooks,
  onChanged,
  onClose,
}: {
  notebooks: Notebook[];
  onChanged: () => Promise<void>;
  onClose: () => void;
}) {
  const [id, setId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [deleting, setDeleting] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await action();
      await onChanged();
      setId(null);
      setName('');
      setDeleting(null);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title="Notebooks"
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form
        className="workspace-form"
        onSubmit={(e) => {
          e.preventDefault();
          void run(() => api.saveNotebook(id, name));
        }}
      >
        <label>
          Notebook name
          <input required maxLength={200} value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <button className="primary" disabled={busy}>
          {id ? 'Rename notebook' : 'Add notebook'}
        </button>
      </form>
      {error && <p role="alert">{error}</p>}
      <div className="notebook-manager">
        {notebooks.map((n) => (
          <article key={n.id}>
            <strong>{n.name}</strong>
            <div className="action-row">
              <button
                className="secondary small"
                disabled={busy}
                onClick={() => {
                  setId(n.id);
                  setName(n.name);
                }}
              >
                Rename {n.name}
              </button>
              <button
                className="icon-button"
                aria-label={`Remove notebook ${n.name}`}
                disabled={busy}
                onClick={() => setDeleting(n.id)}
              >
                <Glyph name="trash" />
              </button>
            </div>
            {deleting === n.id && (
              <div className="inline-confirm">
                <p>Remove this notebook? Its notes stay in My notes.</p>
                <button
                  className="secondary small"
                  disabled={busy}
                  onClick={() => void run(() => api.deleteNotebook(n.id))}
                >
                  Remove notebook
                </button>
                <button className="secondary small" onClick={() => setDeleting(null)}>
                  Keep notebook
                </button>
              </div>
            )}
          </article>
        ))}
      </div>
    </Modal>
  );
}
