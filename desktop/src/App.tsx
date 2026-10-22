import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api, call, native } from './api';
import { importInWorker } from './import-client';
import { Playback } from './playback';
import { TransferPanel } from './TransferPanel';
import { ReadingToolsPage } from './ReadingToolsPage';
import { ReaderSurface, type ReaderSelection } from './ReaderSurface';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { Glyph } from './Glyph';
import { HomePage } from './HomePage';
import { ClassicsPage } from './ClassicsPage';
import { AppearanceSettings } from './AppearanceSettings';
import { AiHandoff } from './AiHandoff';
import { StudyHub } from './StudyHub';
import { NotesWorkspace } from './NotesWorkspace';
import { useTheme } from './themes';
import classics from './data/classics.json';
import {
  defaultSettings,
  type Annotation,
  type Book,
  type CardInput,
  type Settings,
  type Voice,
} from './types';
import { AnnotationCard, BookDetails, TextImportDialog, Modal } from './WorkspacePages';

type Page =
  | 'home'
  | 'library'
  | 'classics'
  | 'reader'
  | 'notes'
  | 'study'
  | 'ai'
  | 'queue'
  | 'tools'
  | 'voices'
  | 'settings';
const pageNames: Record<Page, string> = {
  home: 'Home',
  library: 'Library',
  classics: 'Classics',
  reader: 'Reading room',
  notes: 'Notes',
  study: 'Study Hub',
  ai: 'AI handoff',
  queue: 'Listening queue',
  tools: 'Vocabulary & pronunciation',
  voices: 'Voice Studio',
  settings: 'Settings',
};
function Cover({ book, large = false }: { book: Book; large?: boolean }) {
  const classic = classics.find(
    (entry) => entry.title === book.title && entry.author === book.author,
  );
  if (classic)
    return (
      <img
        className={`cover classic-shelf-cover ${large ? 'large' : ''}`}
        src={`/covers/${classic.id}.jpg`}
        alt=""
      />
    );
  const colors = ['sage', 'copper', 'ink', 'rose'];
  return (
    <div
      className={`cover ${colors[book.title.length % colors.length]} ${large ? 'large' : ''}`}
      aria-hidden="true"
    >
      <span className="cover-label">VERN LIBRARY</span>
      <span className="cover-title">{book.title}</span>
      <div className="cover-ornament">✳</div>
      <span className="cover-author">{book.author || book.format}</span>
    </div>
  );
}
const player = new Playback();
export default function App() {
  const [page, setPage] = useState<Page>('home');
  const [settingsReturn, setSettingsReturn] = useState<Page>('home');
  const [readerPreferences, setReaderPreferences] = useState(false);
  const [books, setBooks] = useState<Book[]>([]);
  const [activeId, setActiveId] = useState('');
  const [settings, setSettings] = useState<Settings>(defaultSettings);
  useTheme(settings);
  const [voices, setVoices] = useState<Voice[]>([]);
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('all');
  const [collection, setCollection] = useState('');
  const [sort, setSort] = useState('recent');
  const [details, setDetails] = useState<Book | null>(null);
  const [addingText, setAddingText] = useState(false);
  const [studySeed, setStudySeed] = useState<CardInput | null>(null);
  const [notesEntry, setNotesEntry] = useState<'personal' | 'books'>('personal');
  const [handoffSeed, setHandoffSeed] = useState<(ReaderSelection & { key: string }) | undefined>();
  const [vocabSource, setVocabSource] = useState<{
    bookId: string;
    position: number;
    word?: string;
  } | null>(null);
  const [note, setNote] = useState('');
  const [noteAnchor, setNoteAnchor] = useState<number | null>(null);
  const [readerFullscreen, setReaderFullscreen] = useState(false);
  const [savingAnnotation, setSavingAnnotation] = useState(false);
  const [notebook, setNotebook] = useState(false);
  const [busy, setBusy] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [sleepDeadline, setSleepDeadline] = useState<number | null>(null);
  const playbackRun = useRef(0);
  const [audioState, setAudioState] = useState('Ready when you are');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [ready, setReady] = useState(false);
  const search = useRef<HTMLInputElement>(null);
  const settingsRef = useRef(defaultSettings);
  const persistedSettings = useRef(defaultSettings);
  const settingsQueue = useRef<Promise<void>>(Promise.resolve());
  const progressQueue = useRef<Promise<void>>(Promise.resolve());
  const navigation = useRef(0);
  const active = books.find((book) => book.id === activeId);
  const selectedVoice = voices.find((voice) => voice.id === settings.voice);
  const fail = useCallback(
    (reason: unknown) => setError(String(reason).replace(/^Error: /, '')),
    [],
  );

  useEffect(() => {
    let mounted = true;
    Promise.all([api.books(), api.settings(), api.voices()])
      .then(([items, prefs, catalogue]) => {
        if (!mounted) return;
        setBooks(items);
        setSettings(prefs);
        settingsRef.current = prefs;
        persistedSettings.current = prefs;
        setActiveId(
          items.some((book) => book.id === prefs.lastBook) ? prefs.lastBook : items[0]?.id || '',
        );
        setVoices(catalogue);
        setReady(true);
      })
      .catch(fail);
    return () => {
      mounted = false;
    };
  }, [fail]);
  useEffect(() => {
    let cancelled = false;
    if (activeId)
      api
        .annotations(activeId)
        .then((items) => {
          if (!cancelled) setAnnotations(items);
        })
        .catch(fail);
    else setAnnotations([]);
    return () => {
      cancelled = true;
    };
  }, [activeId, fail]);
  useEffect(() => {
    const timer = setInterval(() => {
      api.voices().then(setVoices).catch(fail);
    }, 1000);
    return () => clearInterval(timer);
  }, [fail]);
  async function toggleReaderFullscreen() {
    const next = !readerFullscreen;
    setReaderFullscreen(next);
    if (native) await getCurrentWindow().setFullscreen(next).catch(fail);
    else if (next) await document.documentElement.requestFullscreen?.().catch(fail);
    else if (document.fullscreenElement) await document.exitFullscreen().catch(fail);
  }
  useEffect(() => {
    if (page !== 'reader' && readerFullscreen) {
      setReaderFullscreen(false);
      if (native) void getCurrentWindow().setFullscreen(false).catch(fail);
    }
  }, [page, readerFullscreen, fail]);
  useEffect(() => {
    const changed = () => {
      if (!native && !document.fullscreenElement) setReaderFullscreen(false);
    };
    document.addEventListener('fullscreenchange', changed);
    return () => document.removeEventListener('fullscreenchange', changed);
  }, []);

  async function preferences(patch: Partial<Settings>) {
    const next = { ...settingsRef.current, ...patch };
    settingsRef.current = next;
    setSettings(next);
    try {
      settingsQueue.current = settingsQueue.current
        .catch(() => {})
        .then(() => api.saveSettings(next));
      await settingsQueue.current;
      persistedSettings.current = next;
      return true;
    } catch (e) {
      fail(e);
      if (settingsRef.current === next) {
        settingsRef.current = persistedSettings.current;
        setSettings(persistedSettings.current);
      }
      return false;
    }
  }
  async function stop() {
    ++playbackRun.current;
    setPlaying(false);
    setAudioState('Paused');
    if (native) await player.stop().catch(fail);
  }
  async function position(book: Book, index: number) {
    const safe = Math.max(0, Math.min(book.chunks.length - 1, index));
    progressQueue.current = progressQueue.current
      .catch(() => {})
      .then(() => api.progress(book.id, safe));
    await progressQueue.current;
    setBooks((items) =>
      items.map((item) => (item.id === book.id ? { ...item, position: safe } : item)),
    );
  }
  async function seek(index: number) {
    if (!active) return;
    const request = ++navigation.current;
    await stop();
    if (request !== navigation.current) return;
    await position(active, index).catch(fail);
  }
  async function open(book: Book, passage?: number) {
    const request = ++navigation.current;
    await stop();
    if (request !== navigation.current) return;
    if (passage !== undefined) {
      try {
        await position(book, passage);
      } catch (e) {
        fail(e);
        return;
      }
      if (request !== navigation.current) return;
    }
    setActiveId(book.id);
    setPage('reader');
    setQuery('');
    setNote('');
    setNoteAnchor(null);
    await preferences({ lastBook: book.id });
  }
  async function importBook() {
    setError('');
    setBusy(true);
    try {
      const file = await api.pickBook();
      if (!file) return;
      const parsed = await importInWorker(file.name, Uint8Array.from(file.bytes));
      const book = await api.importSource(parsed, file);
      setBooks(await api.books());
      await open(book);
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }
  async function importText(title: string, author: string, text: string) {
    const parsed = await importInWorker('text.txt', new TextEncoder().encode(text));
    const book = await api.importBook({ ...parsed, title, author });
    setBooks(await api.books());
    await open(book);
  }
  async function exportPassageAudio() {
    if (!active) return;
    if (selectedVoice?.state !== 'installed') {
      setPage('voices');
      setError('Download a voice before exporting audio.');
      return;
    }
    setBusy(true);
    setNotice('');
    try {
      await beforeTransfer();
      if (
        await api.exportPassageAudio(
          active.id,
          active.position,
          settingsRef.current.voice,
          settingsRef.current.rate,
        )
      )
        setNotice('Passage audio saved as WAV.');
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }
  async function togglePlay() {
    if (busy) return;
    if (playing) {
      await stop();
      return;
    }
    if (!active) return;
    if (selectedVoice?.state !== 'installed') {
      setPage('voices');
      setError('Download a voice to listen offline.');
      return;
    }
    await playBook(active);
  }
  async function playBook(first: Book) {
    const run = ++playbackRun.current;
    setPlaying(true);
    setError('');
    try {
      let book: Book | undefined = first;
      while (book && run === playbackRun.current) {
        const current: Book = book;
        if (current.textAvailable === false)
          throw new Error(
            'This document needs OCR before it can be read aloud. Open Original document view to see its pages.',
          );
        let finished = false;
        await player.start(
          settingsRef.current.voice,
          current.chunks,
          current.position,
          settingsRef.current.rate,
          async (index) => {
            if (run === playbackRun.current) await position(current, index);
          },
          (state) => {
            if (run !== playbackRun.current) return;
            setAudioState(state);
            finished = state === 'Finished';
            if (state === 'Sleep timer ended') {
              setPlaying(false);
              setSleepDeadline(null);
            }
          },
        );
        if (run !== playbackRun.current || !finished) return;
        const queue = settingsRef.current.queue;
        if (!queue.length) break;
        const remaining: string[] = queue.filter((id) => id !== current.id);
        if (!(await preferences({ queue: remaining })))
          throw new Error('The listening queue could not be saved.');
        if (run !== playbackRun.current) return;
        const fresh = await api.books();
        if (run !== playbackRun.current) return;
        setBooks(fresh);
        book = fresh.find((item) => item.id === remaining[0]);
        if (book) {
          setActiveId(book.id);
          setNote('');
          if (!(await preferences({ lastBook: book.id })))
            throw new Error('The next reading position could not be saved.');
        }
      }
      if (run === playbackRun.current) setPlaying(false);
    } catch (e) {
      if (run !== playbackRun.current) return;
      setPlaying(false);
      setAudioState('Playback failed');
      fail(e);
    }
  }
  useEffect(() => {
    player.setSleepDeadline(sleepDeadline);
    if (sleepDeadline === null) return;
    const timer = setTimeout(
      () => {
        setSleepDeadline(null);
        ++playbackRun.current;
        setPlaying(false);
        setAudioState('Sleep timer ended');
        if (native) void player.stop().catch(fail);
      },
      Math.max(0, sleepDeadline - Date.now()),
    );
    return () => clearTimeout(timer);
  }, [sleepDeadline, fail]);
  async function beforeTransfer() {
    await stop();
    await progressQueue.current.catch(() => {});
    await settingsQueue.current.catch(() => {});
  }
  async function restoredLibrary() {
    const [items, prefs] = await Promise.all([api.books(), api.settings()]);
    setBooks(items);
    setSettings(prefs);
    settingsRef.current = prefs;
    persistedSettings.current = prefs;
    const selected = items.some((b) => b.id === prefs.lastBook)
      ? prefs.lastBook
      : (items[0]?.id ?? '');
    setActiveId(selected);
    setAnnotations([]);
    setNote('');
    if (selected) setAnnotations(await api.annotations(selected));
  }
  async function queueBook(book: Book) {
    const queue = settingsRef.current.queue;
    await preferences({
      queue: queue.includes(book.id) ? queue.filter((id) => id !== book.id) : [...queue, book.id],
    });
  }
  async function moveQueued(id: string, offset: number) {
    const queue = [...settingsRef.current.queue];
    const at = queue.indexOf(id);
    const next = at + offset;
    if (at < 0 || next < 0 || next >= queue.length) return;
    [queue[at], queue[next]] = [queue[next], queue[at]];
    await preferences({ queue });
  }
  async function addAnnotation(kind: 'note' | 'bookmark') {
    if (!active || savingAnnotation || (kind === 'note' && !note.trim())) return;
    const savedText = note;
    setSavingAnnotation(true);
    try {
      const existing =
        kind === 'bookmark'
          ? annotations.find(
              (item) =>
                item.bookId === active.id &&
                item.kind === 'bookmark' &&
                item.position === active.position,
            )
          : undefined;
      if (existing) await api.deleteAnnotation(existing.id);
      else
        await api.annotate({
          id: crypto.randomUUID(),
          bookId: active.id,
          position: kind === 'note' ? (noteAnchor ?? active.position) : active.position,
          text: kind === 'note' ? note.trim() : active.chunks[active.position].slice(0, 120),
          kind,
        });
      setAnnotations(await api.annotations(active.id));
      if (kind === 'note') {
        setNote((draft) => (draft === savedText ? '' : draft));
        setNoteAnchor(null);
      }
    } catch (e) {
      fail(e);
    } finally {
      setSavingAnnotation(false);
    }
  }
  async function listenToSelection(selection: ReaderSelection) {
    if (!active || selection.bookId !== active.id) return;
    if (selectedVoice?.state !== 'installed') {
      setPage('voices');
      setError('Download a voice to listen offline.');
      return;
    }
    await seek(selection.position);
    const run = ++playbackRun.current;
    setPlaying(true);
    setError('');
    try {
      const parsed = await importInWorker(
        'selection.txt',
        new TextEncoder().encode(selection.text),
      );
      if (run !== playbackRun.current) return;
      await player.start(
        settingsRef.current.voice,
        parsed.chunks,
        0,
        settingsRef.current.rate,
        async () => {},
        (state) => {
          if (run !== playbackRun.current) return;
          setAudioState(state);
          if (['Finished', 'Sleep timer ended'].includes(state)) setPlaying(false);
          if (state === 'Sleep timer ended') setSleepDeadline(null);
        },
      );
    } catch (e) {
      if (run === playbackRun.current) {
        setPlaying(false);
        fail(e);
      }
    }
  }
  async function favorite(book: Book) {
    try {
      await api.favorite(book.id);
      setBooks(await api.books());
    } catch (e) {
      fail(e);
    }
  }
  async function refreshAnnotations() {
    if (activeId) setAnnotations(await api.annotations(activeId));
  }
  async function saveDetails(title: string, author: string, group: string) {
    if (!details) return;
    await api.updateBook(details.id, title, author, group);
    setBooks(await api.books());
    setDetails(null);
  }
  async function removeBook() {
    if (!details) return;
    if (details.id === activeId) {
      ++navigation.current;
      await stop();
    }
    await progressQueue.current.catch(() => {});
    await settingsQueue.current.catch(() => {});
    await api.deleteBook(details.id);
    const remaining = await api.books();
    const prefs = await api.settings();
    settingsRef.current = prefs;
    persistedSettings.current = prefs;
    setSettings(prefs);
    setBooks(remaining);
    if (details.id === activeId) {
      setActiveId(remaining[0]?.id ?? '');
      setAnnotations([]);
      setNote('');
      await preferences({
        lastBook: remaining[0]?.id ?? '',
        queue: settingsRef.current.queue.filter((id) => id !== details.id),
      });
    } else {
      await preferences({ queue: settingsRef.current.queue.filter((id) => id !== details.id) });
    }
    setDetails(null);
  }
  // Global shortcuts ignore editable controls and preserve native text editing.
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (document.querySelector('dialog[open]')) return;
      if (
        page === 'reader' &&
        (event.key === 'F11' ||
          (event.key === 'Escape' && readerFullscreen && window.getSelection()?.isCollapsed))
      ) {
        event.preventDefault();
        void toggleReaderFullscreen();
        return;
      }
      if ((event.target as HTMLElement).closest('input,textarea,select,button,[contenteditable]'))
        return;
      if ((event.ctrlKey || event.metaKey) && event.key === 'f') {
        event.preventDefault();
        search.current?.focus();
      }
      if (event.code === 'Space' && active && window.getSelection()?.isCollapsed) {
        event.preventDefault();
        void togglePlay();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  });

  const collections = [
    ...new Set(books.map((book) => book.collection).filter((name): name is string => !!name)),
  ].sort();
  const visible = useMemo(() => {
    if (page !== 'library') return [];
    const term = query.trim().toLowerCase();
    return books
      .map((book) => ({
        book,
        match: term ? book.chunks.findIndex((chunk) => chunk.toLowerCase().includes(term)) : -1,
      }))
      .filter(
        ({ book, match }) =>
          (filter !== 'favorites' || book.favorite) &&
          (!collection || book.collection === collection) &&
          (!term ||
            `${book.title} ${book.author} ${book.collection ?? ''}`.toLowerCase().includes(term) ||
            match >= 0),
      )
      .sort((a, b) =>
        sort === 'title'
          ? a.book.title.localeCompare(b.book.title)
          : sort === 'author'
            ? a.book.author.localeCompare(b.book.author)
            : b.book.updatedAt - a.book.updatedAt,
      );
  }, [books, query, filter, collection, sort, page]);
  const sectionPages: Page[] = ['library', 'classics'].includes(page)
    ? ['library', 'classics']
    : ['study', 'tools', 'ai'].includes(page)
      ? ['study', 'tools', 'ai']
      : ['queue', 'voices'].includes(page)
        ? ['queue', 'voices']
        : [];

  return (
    <div className={`app-shell ${readerFullscreen && page === 'reader' ? 'reader-focus' : ''}`}>
      <aside className="sidebar">
        <a
          className="brand"
          href="#home"
          onClick={() => {
            setPage('home');
            setQuery('');
          }}
        >
          <img src="/vern.png" alt="" />
          <span>
            vern<span className="brand-dot">.</span>
          </span>
        </a>
        <p className="sidebar-caption">A little space for a good book.</p>
        <nav aria-label="Main navigation">
          {(
            [
              { id: 'home', icon: 'home', name: 'Home' },
              { id: 'library', icon: 'book', name: 'Library' },
              { id: 'study', icon: 'study', name: 'Study' },
              { id: 'notes', icon: 'note', name: 'Notes' },
            ] as const
          ).map((item) => {
            const current = page === 'settings' ? settingsReturn : page;
            const selected =
              item.id === 'library'
                ? ['library', 'classics', 'reader'].includes(current)
                : item.id === 'study'
                  ? ['study', 'tools', 'ai'].includes(current)
                  : item.id === 'home'
                    ? ['home', 'queue', 'voices'].includes(current)
                    : item.id === current;
            return (
              <button
                key={item.id}
                className={selected ? 'nav-item selected' : 'nav-item'}
                aria-current={selected ? 'page' : undefined}
                onClick={() => {
                  if (item.id === 'notes') setNotesEntry('personal');
                  setPage(item.id);
                  setQuery('');
                }}
              >
                <Glyph name={item.icon} />
                <span>{item.name}</span>
              </button>
            );
          })}
        </nav>
        <div className="sidebar-bottom">
          <span className="rail-status">
            <span className="status-dot" />
            Your space, on device.
          </span>
          <span className="version">VERN DESKTOP</span>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            Your space <span>/</span> <strong>{pageNames[page]}</strong>
          </div>
          <div className="topbar-actions">
            <span className="local-label">
              <span className="status-dot" />{' '}
              {native ? 'On your device' : 'Browser preview · read-only'}
            </span>
            {['home', 'library', 'classics'].includes(page) && (
              <>
                <button
                  className="secondary small"
                  disabled={busy || !ready || !native}
                  onClick={() => setAddingText(true)}
                >
                  <Glyph name="edit" />
                  Add text
                </button>
                <button
                  className="primary small"
                  disabled={busy || !ready}
                  onClick={() => void importBook()}
                >
                  <Glyph name="plus" />
                  {busy ? 'Importing…' : 'Import book'}
                </button>
              </>
            )}
            <button
              className="icon-button settings-launch"
              disabled={!ready}
              aria-label={page === 'settings' ? 'Close settings' : 'Settings'}
              title="Settings"
              aria-pressed={page === 'settings'}
              onClick={() => {
                if (page === 'settings') setPage(settingsReturn);
                else {
                  setSettingsReturn(page);
                  setPage('settings');
                }
              }}
            >
              <Glyph name="settings" />
            </button>
          </div>
        </header>
        {!!sectionPages.length && (
          <nav
            className="section-nav"
            aria-label={
              ['library', 'classics'].includes(page)
                ? 'Library sections'
                : ['queue', 'voices'].includes(page)
                  ? 'Listening sections'
                  : 'Study sections'
            }
          >
            {sectionPages.map((item) => (
              <button
                key={item}
                aria-current={page === item ? 'page' : undefined}
                onClick={() => {
                  setPage(item);
                  setQuery('');
                }}
              >
                <Glyph
                  name={
                    item === 'library'
                      ? 'book'
                      : item === 'classics'
                        ? 'compass'
                        : item === 'study'
                          ? 'study'
                          : item === 'tools'
                            ? 'dictionary'
                            : item === 'ai'
                              ? 'sparkles'
                              : item === 'queue'
                                ? 'queue'
                                : 'headphones'
                  }
                />
                {item === 'library'
                  ? 'Added'
                  : item === 'tools'
                    ? 'Vocabulary & pronunciation'
                    : pageNames[item]}
              </button>
            ))}
          </nav>
        )}
        {!native && (
          <div className="preview-banner">
            You’re viewing the desktop interface in a browser. Launch Vern with{' '}
            <code>npm run tauri dev</code> to import, save and listen.
          </div>
        )}
        {error && (
          <div className="error-banner" role="alert">
            <span>{error}</span>
            <button className="icon-button" aria-label="Dismiss error" onClick={() => setError('')}>
              <Glyph name="close" />
            </button>
          </div>
        )}
        {notice && (
          <div className="preview-banner" role="status">
            {notice}
            <button className="text-button" onClick={() => setNotice('')}>
              Dismiss
            </button>
          </div>
        )}
        <main>
          <section hidden={page !== 'ai'}>
            <AiHandoff
              key={handoffSeed?.key ?? 'draft'}
              initial={handoffSeed}
              books={books}
              active={active}
              onError={fail}
              onStudy={() => setPage('study')}
            />
          </section>
          {page === 'home' && (
            <HomePage
              books={books}
              active={active}
              onOpen={(book) => void open(book)}
              onNavigate={setPage}
              onImport={() => void importBook()}
              onError={fail}
            />
          )}
          {page === 'classics' && (
            <ClassicsPage
              books={books}
              onAdded={async (book) => {
                setBooks(await api.books());
                setNotice(`Added “${book.title}” to your library.`);
              }}
              onOpen={(book) => void open(book)}
              onError={fail}
            />
          )}
          {page === 'library' && (
            <div className="library page">
              <div className="page-heading">
                <div>
                  <span className="eyebrow">YOUR BOOKS, YOUR PLACE</span>
                  <h1>
                    My Library<span className="heading-dot">.</span>
                  </h1>
                  <p>Your next chapter is waiting.</p>
                </div>
                <div className="book-count">
                  <strong>{books.length.toString().padStart(2, '0')}</strong>
                  <span>books in your space</span>
                </div>
              </div>
              <div className="library-toolbar">
                <div className="tabs" aria-label="Library filter">
                  <button
                    className={filter === 'all' ? 'active' : ''}
                    onClick={() => setFilter('all')}
                  >
                    All books <span>{books.length}</span>
                  </button>
                  <button
                    className={filter === 'favorites' ? 'active' : ''}
                    onClick={() => setFilter('favorites')}
                  >
                    Favorites
                  </button>
                </div>
                <label className="search">
                  <Glyph name="search" />
                  <input
                    ref={search}
                    aria-label="Search library"
                    placeholder="Search books & text…"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                  <kbd>Ctrl F</kbd>
                </label>
              </div>
              <div className="workspace-filters shelf-filters">
                <label>
                  Collection
                  <select value={collection} onChange={(e) => setCollection(e.target.value)}>
                    <option value="">All collections</option>
                    {collections.map((name) => (
                      <option key={name}>{name}</option>
                    ))}
                    {collection && !collections.includes(collection) && (
                      <option>{collection}</option>
                    )}
                  </select>
                </label>
                <label>
                  Sort by
                  <select value={sort} onChange={(e) => setSort(e.target.value)}>
                    <option value="recent">Recently read</option>
                    <option value="title">Title</option>
                    <option value="author">Author</option>
                  </select>
                </label>
              </div>
              {visible.length ? (
                <div className="book-grid">
                  {visible.map(({ book, match }) => (
                    <article className="book-card" key={book.id}>
                      <button
                        className="book-open"
                        onClick={() => void open(book)}
                        aria-label={`Open ${book.title}`}
                      >
                        <div className="cover-wrap">
                          <Cover book={book} large />
                        </div>
                        <h3>{book.title}</h3>
                        <p>{book.author || 'Personal library'}</p>
                      </button>
                      <div className="book-meta">
                        <span>
                          {book.format} <span>·</span>{' '}
                          {Math.round(((book.position + 1) / book.chunks.length) * 100)}% read
                        </span>
                        <button
                          className={`icon-button ${book.favorite ? 'is-favorite' : ''}`}
                          aria-label={`${book.favorite ? 'Unfavorite' : 'Favorite'} ${book.title}`}
                          onClick={() => void favorite(book)}
                        >
                          <Glyph name="heart" />
                        </button>
                      </div>
                      <progress value={book.position + 1} max={book.chunks.length} />
                      {book.collection && (
                        <span className="collection-label">{book.collection}</span>
                      )}
                      {match >= 0 && (
                        <button className="search-excerpt" onClick={() => void open(book, match)}>
                          <small>Passage {match + 1} →</small>
                          {book.chunks[match].slice(0, 150)}
                          {book.chunks[match].length > 150 ? '…' : ''}
                        </button>
                      )}
                      <button
                        className="text-button book-details"
                        onClick={() => setDetails(book)}
                        aria-label={`Manage ${book.title}`}
                      >
                        Book details & collection
                      </button>
                      <button
                        className="text-button book-details"
                        aria-pressed={settings.queue.includes(book.id)}
                        onClick={() => void queueBook(book)}
                      >
                        {settings.queue.includes(book.id)
                          ? 'Remove from listening queue'
                          : 'Add to listening queue'}
                      </button>
                    </article>
                  ))}
                </div>
              ) : (
                <div className="empty-library">
                  <Glyph name="book" />
                  <div>
                    <h3>{books.length ? 'No books found' : 'Your shelf starts here'}</h3>
                    <p>
                      {books.length
                        ? 'Try another search or filter.'
                        : 'Imported books appear here, with your place kept for you.'}
                    </p>
                  </div>
                </div>
              )}
              <p className="shelf-footer">
                A quieter corner of your day. <span>Made for reading.</span>
              </p>
            </div>
          )}

          {page === 'notes' && (
            <NotesWorkspace
              initialView={notesEntry}
              books={books}
              onOpen={(book, passage) => void open(book, passage)}
              onError={fail}
            />
          )}
          {page === 'queue' && (
            <div className="page">
              <span className="eyebrow">YOUR NEXT LISTEN</span>
              <h1>
                Listening queue<span className="heading-dot">.</span>
              </h1>
              <p className="page-description">
                Add books from your library. Queued books continue in order after the current book
                finishes.
              </p>
              <div className="action-row">
                <button
                  className="primary"
                  disabled={!native || !settings.queue.length || playing || busy}
                  onClick={() => {
                    const book = books.find((b) => b.id === settings.queue[0]);
                    if (!book) return;
                    if (selectedVoice?.state !== 'installed') {
                      setPage('voices');
                      setError('Download a voice to listen offline.');
                      return;
                    }
                    setActiveId(book.id);
                    void preferences({ lastBook: book.id });
                    void playBook(book);
                  }}
                >
                  Play queue
                </button>
              </div>
              <div className="workspace-filters">
                <label>
                  Sleep timer
                  <select
                    aria-label="Sleep timer"
                    value={sleepDeadline === null ? 'off' : 'active'}
                    onChange={(e) => {
                      if (e.target.value !== 'active')
                        setSleepDeadline(
                          e.target.value === 'off'
                            ? null
                            : Date.now() + Number(e.target.value) * 60000,
                        );
                    }}
                  >
                    <option value="off">Off</option>
                    {sleepDeadline !== null && (
                      <option value="active">
                        Until{' '}
                        {new Date(sleepDeadline).toLocaleTimeString([], {
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </option>
                    )}
                    {[15, 30, 45, 60].map((minutes) => (
                      <option key={minutes} value={minutes}>
                        {minutes} minutes
                      </option>
                    ))}
                  </select>
                </label>
                <p className="muted">The timer pauses narration wherever you are in the app.</p>
              </div>
              {settings.queue.map((id, index) => {
                const book = books.find((b) => b.id === id);
                if (!book) return null;
                return (
                  <article className="saved-note" key={id}>
                    <span className="eyebrow">
                      {index + 1} · {book.format}
                    </span>
                    <h3>{book.title}</h3>
                    <p>{book.author}</p>
                    <div className="action-row">
                      <button className="text-button" onClick={() => void open(book)}>
                        Open book →
                      </button>
                      <button
                        className="secondary small"
                        disabled={index === 0}
                        onClick={() => void moveQueued(id, -1)}
                      >
                        Move up
                      </button>
                      <button
                        className="secondary small"
                        disabled={index === settings.queue.length - 1}
                        onClick={() => void moveQueued(id, 1)}
                      >
                        Move down
                      </button>
                      <button className="text-button" onClick={() => void queueBook(book)}>
                        Remove
                      </button>
                    </div>
                  </article>
                );
              })}
              {!settings.queue.length && (
                <div className="empty-library">
                  <div>
                    <h3>Your queue is clear</h3>
                    <p>Add books from My Library to line up your next listens.</p>
                  </div>
                </div>
              )}
            </div>
          )}
          {page === 'tools' && (
            <ReadingToolsPage
              settings={settings}
              books={books}
              source={vocabSource}
              onSave={async (patch) => {
                if (patch.pronunciationRules) await stop();
                return preferences(patch);
              }}
              onOpen={(book, passage) => void open(book, passage)}
              onCard={(card) => {
                setStudySeed(card);
                setPage('study');
              }}
            />
          )}
          {page === 'study' && (
            <StudyHub
              books={books}
              seed={studySeed}
              onSeedUsed={() => setStudySeed(null)}
              onOpen={(book, passage) => void open(book, passage)}
              onError={fail}
              onTools={() => setPage('tools')}
              onAi={() => setPage('ai')}
              onNotes={() => {
                setNotesEntry('books');
                setPage('notes');
              }}
            />
          )}

          {page === 'reader' && active && (
            <div className={`reading-layout ${notebook ? '' : 'without-notebook'}`}>
              <ReaderSurface
                key={active.id}
                book={active}
                settings={settings}
                query={query}
                onQuery={setQuery}
                searchRef={search}
                onSeek={seek}
                onSettings={preferences}
                onError={fail}
                onImport={() => void importBook()}
                onPreferences={() => setReaderPreferences(true)}
                fullscreen={readerFullscreen}
                onFullscreen={() => void toggleReaderFullscreen()}
                notebook={notebook}
                onNotebook={() => {
                  if (readerFullscreen) {
                    void toggleReaderFullscreen();
                    setNotebook(true);
                  } else setNotebook(!notebook);
                }}
                bookmarked={annotations.some(
                  (item) =>
                    item.bookId === active.id &&
                    item.kind === 'bookmark' &&
                    item.position === active.position,
                )}
                onBookmark={() => void addAnnotation('bookmark')}
                annotationBusy={savingAnnotation}
                onNote={(selection) => {
                  if (readerFullscreen) void toggleReaderFullscreen();
                  setNoteAnchor(selection.position);
                  setNote((draft) => (draft ? `${draft}\n\n${selection.text}` : selection.text));
                  setNotebook(true);
                }}
                onVocabulary={(selection) => {
                  setVocabSource({
                    bookId: active.id,
                    position: selection.position,
                    word: selection.text.slice(0, 100),
                  });
                  setPage('tools');
                }}
                onFlashcard={(selection) => {
                  setStudySeed({
                    bookId: active.id,
                    position: selection.position,
                    deck: active.title.slice(0, 200),
                    front: '',
                    back: selection.text,
                  });
                  setPage('study');
                }}
                onListen={(selection) => void listenToSelection(selection)}
                onAi={(selection) => {
                  if (readerFullscreen) void toggleReaderFullscreen();
                  setHandoffSeed({ ...selection, key: crypto.randomUUID() });
                  setPage('ai');
                }}
              />
              {notebook && (
                <aside className="notebook">
                  <div className="notebook-title">
                    <Glyph name="note" />
                    <h2>Your notebook</h2>
                    <span>{annotations.filter((item) => item.bookId === active.id).length}</span>
                  </div>
                  <p className="muted">A place for the thoughts worth keeping.</p>
                  <blockquote>
                    {active.chunks[active.position]?.slice(0, 180)}
                    {active.chunks[active.position]?.length > 180 ? '…' : ''}
                    <cite>PASSAGE {active.position + 1}</cite>
                  </blockquote>
                  <label htmlFor="note">Leave a thought</label>
                  <textarea
                    id="note"
                    placeholder="What stays with you?"
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                  />
                  <button
                    className="primary full"
                    disabled={!note.trim() || savingAnnotation}
                    onClick={() => void addAnnotation('note')}
                  >
                    <Glyph name="plus" />
                    Save note
                  </button>
                  <button
                    className="secondary full"
                    disabled={!native}
                    onClick={() => {
                      setStudySeed({
                        bookId: active.id,
                        position: active.position,
                        deck: active.title.slice(0, 200),
                        front: '',
                        back: active.chunks[active.position],
                      });
                      setPage('study');
                    }}
                  >
                    <Glyph name="study" />
                    Create flashcard from passage
                  </button>
                  <div className="notes-list">
                    <button
                      className="secondary full"
                      disabled={!native || busy}
                      onClick={() => void exportPassageAudio()}
                    >
                      <Glyph name="download" />
                      {busy ? 'Preparing audio…' : 'Export passage audio (WAV)'}
                    </button>
                    <button
                      className="secondary full"
                      disabled={!native}
                      onClick={() => {
                        setVocabSource({ bookId: active.id, position: active.position });
                        setPage('tools');
                      }}
                    >
                      <Glyph name="dictionary" />
                      Save vocabulary from passage
                    </button>
                    {annotations
                      .filter((item) => item.bookId === active.id)
                      .map((item) => (
                        <AnnotationCard
                          key={item.id}
                          item={item}
                          onOpen={() => void seek(item.position)}
                          onChanged={refreshAnnotations}
                          onError={fail}
                        />
                      ))}
                    {!annotations.some((item) => item.bookId === active.id) && (
                      <p className="muted notes-empty">
                        Your notes and bookmarks will gather here, anchored to the words that
                        inspired them.
                      </p>
                    )}
                  </div>
                </aside>
              )}
            </div>
          )}

          {page === 'voices' && (
            <div className="page voices-page">
              <span className="eyebrow">LET THE WORDS COME TO YOU</span>
              <h1>
                Voice Studio<span className="heading-dot">.</span>
              </h1>
              <p className="page-description">
                Find a voice for your next chapter. Download once, listen offline.
              </p>
              <div className="voice-intro">
                <Glyph name="headphones" />
                <div>
                  <h2>A good book, in good company.</h2>
                  <p>
                    Studio voices use Kokoro. Lite voices use Piper. Both run entirely on your
                    device after download.
                  </p>
                </div>
              </div>
              <div className="voice-grid">
                {voices.map((voice) => (
                  <article className="voice-card" key={voice.id}>
                    <div
                      className={`voice-avatar ${voice.family === 'Studio' ? 'studio' : ''}`}
                      aria-hidden="true"
                    >
                      <span />
                      <span />
                      <span />
                      <span />
                      <span />
                    </div>
                    <div className="voice-family">
                      {voice.family} <span>{voice.engine}</span>
                    </div>
                    <h2>{voice.name}</h2>
                    <p>English · US · {voice.size}</p>
                    <div className="voice-status">
                      <span
                        className={`status-dot ${voice.state === 'installed' ? '' : 'muted-dot'}`}
                      />
                      {voice.state === 'installed'
                        ? 'Ready for offline listening'
                        : voice.state === 'downloading'
                          ? `Downloading ${voice.total ? Math.round((voice.downloaded / voice.total) * 100) + '%' : (voice.downloaded / 1_048_576).toFixed(1) + ' MB'}`
                          : voice.state === 'validating'
                            ? 'Validating voice package…'
                            : voice.state === 'queued'
                              ? 'Queued for download'
                              : voice.state === 'cancelling'
                                ? 'Cancelling download…'
                                : voice.state === 'failed'
                                  ? 'Download failed'
                                  : voice.state === 'unavailable'
                                    ? 'Available in the desktop app'
                                    : 'Not downloaded'}
                    </div>
                    {['downloading', 'queued', 'validating'].includes(voice.state) && (
                      <progress value={voice.downloaded} max={voice.total || undefined} />
                    )}{' '}
                    {voice.error && <p className="voice-error">{voice.error}</p>}
                    {voice.state === 'installed' ? (
                      <button
                        className={settings.voice === voice.id ? 'primary full' : 'secondary full'}
                        onClick={() => {
                          void stop();
                          void preferences({ voice: voice.id });
                        }}
                      >
                        {settings.voice === voice.id ? (
                          <Glyph name="check" />
                        ) : (
                          <Glyph name="headphones" />
                        )}
                        {settings.voice === voice.id ? 'Selected voice' : 'Use this voice'}
                      </button>
                    ) : ['downloading', 'queued', 'validating', 'cancelling'].includes(
                        voice.state,
                      ) ? (
                      <button
                        className="secondary full"
                        disabled={voice.state === 'cancelling'}
                        onClick={() =>
                          void call('cancel_download', { voiceId: voice.id }).catch(fail)
                        }
                      >
                        Cancel download
                      </button>
                    ) : (
                      <button
                        className="primary full"
                        disabled={!native}
                        onClick={() =>
                          void call('download_voice', { voiceId: voice.id })
                            .then(() => api.voices().then(setVoices))
                            .catch(fail)
                        }
                      >
                        <Glyph name="download" />
                        {voice.state === 'failed'
                          ? 'Try download again'
                          : `Download ${voice.family}`}
                      </button>
                    )}
                  </article>
                ))}
              </div>
              <p className="muted">
                Voices use upstream sherpa-onnx packages. Package licences are retained with the
                downloads.
                <br />
                Highlighting follows passages; these voices do not supply word timing.
              </p>
            </div>
          )}

          {page === 'settings' && (
            <div className="page preferences">
              <span className="eyebrow">MAKE IT YOURS</span>
              <h1>
                A comfortable read<span className="heading-dot">.</span>
              </h1>
              <p className="page-description">Small adjustments. A space that feels like you.</p>
              <div className="settings-shortcuts">
                <button className="secondary" onClick={() => setPage('voices')}>
                  <Glyph name="headphones" />
                  Voices & downloads
                </button>
                <button className="secondary" onClick={() => setPage('queue')}>
                  <Glyph name="queue" />
                  Queue & sleep timer
                </button>
              </div>
              <AppearanceSettings settings={settings} onSave={preferences} />
              <TransferPanel
                books={books}
                beforeAction={beforeTransfer}
                onRestored={restoredLibrary}
              />
            </div>
          )}
        </main>
        {readerPreferences && (
          <Modal title="Reader settings" onClose={() => setReaderPreferences(false)}>
            <AppearanceSettings settings={settings} onSave={preferences} />
          </Modal>
        )}
        {addingText && (
          <TextImportDialog onImport={importText} onClose={() => setAddingText(false)} />
        )}
        {details && (
          <BookDetails
            key={details.id}
            book={details}
            collections={collections}
            onClose={() => setDetails(null)}
            onSave={saveDetails}
            onRemove={removeBook}
          />
        )}
        {active && (
          <footer className="playback-bar" aria-label="Playback controls">
            <div className="now-playing">
              <div className="now-icon">
                <Glyph name="book" />
              </div>
              <div>
                <strong>{active?.title || 'A little reading goes a long way.'}</strong>
                <span>
                  {active ? audioState : 'Import a book to begin your next chapter'}
                  {sleepDeadline !== null
                    ? ` · Sleep at ${new Date(sleepDeadline).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
                    : ''}
                </span>
              </div>
            </div>
            <div className="transport">
              <button
                className="icon-button"
                aria-label="Previous passage"
                disabled={!active}
                onClick={() => active && void seek(active.position - 1)}
              >
                <Glyph name="back" />
              </button>
              <button
                className="play-button"
                aria-label={playing ? 'Pause narration' : 'Play narration'}
                disabled={!active || busy}
                onClick={() => void togglePlay()}
              >
                <Glyph name={playing ? 'pause' : 'play'} />
              </button>
              <button
                className="icon-button"
                aria-label="Next passage"
                disabled={!active}
                onClick={() => active && void seek(active.position + 1)}
              >
                <Glyph name="arrow" />
              </button>
            </div>
            <div className="playback-options">
              <label>
                <span className="sr-only">Narration speed</span>
                <select
                  aria-label="Narration speed"
                  value={settings.rate}
                  onChange={(e) => {
                    void stop();
                    void preferences({ rate: Number(e.target.value) });
                  }}
                >
                  {[0.5, 0.75, 1, 1.25, 1.5, 1.75, 2].map((rate) => (
                    <option key={rate} value={rate}>
                      {rate}×
                    </option>
                  ))}
                </select>
              </label>
              <button className="voice-selector" onClick={() => setPage('voices')}>
                <Glyph name="headphones" />
                <span>
                  {selectedVoice?.name || 'Choose voice'}
                  <small>{selectedVoice?.family || 'OFFLINE VOICES'}</small>
                </span>
              </button>
            </div>
          </footer>
        )}
      </div>
    </div>
  );
}
