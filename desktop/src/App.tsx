import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api, call, native } from './api';
import { importInWorker, extractCoverInWorker } from './import-client';
import { LibraryShelf } from './LibraryShelf';
import { ReadingLists } from './ReadingLists';
import { VoiceStudio } from './VoiceStudio';
import type { SpokenPassage } from './narration-highlight';
import { Playback } from './playback';
import { TransferPanel } from './TransferPanel';
import { ReadingToolsPage } from './ReadingToolsPage';
import { ReaderSurface, type ReaderSelection } from './ReaderSurface';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { Glyph } from './Glyph';
import { HomePage } from './HomePage';
import { InsightsPage } from './InsightsPage';
import { ReadingHistoryPage } from './ReadingHistoryPage';
import { useReadingActivity } from './reading-activity';
import { useNavigation, type Page } from './navigation';
import { WorkspaceScreen } from './WorkspaceScreen';
import './reader-workspace.css';
import { ClassicsPage } from './ClassicsPage';
import { AppearanceSettings } from './AppearanceSettings';
import {
  SettingsLayout,
  AccessibilityPreferences,
  NotesPreferences,
  PdfImportPreferences,
  AiStudyPreferences,
} from './DesktopSettings';
import { AboutPage } from './AboutPage';
import { AiHandoff } from './AiHandoff';
import { StudyHub } from './StudyHub';
import { NotesWorkspace } from './NotesWorkspace';
import { useTheme } from './themes';
import {
  defaultSettings,
  type Annotation,
  type Book,
  type CardInput,
  type Settings,
  type Voice,
  type ActivityTarget,
} from './types';
import { AnnotationCard, BookDetails, TextImportDialog, Modal } from './WorkspacePages';

const pageNames: Record<Page, string> = {
  home: 'Home',
  insights: 'Reading insights',
  library: 'Library',
  classics: 'Classics',
  lists: 'Reading lists',
  history: 'Reading history',
  reader: 'Reading room',
  notes: 'Notes',
  study: 'Study Hub',
  ai: 'AI handoff',
  queue: 'Listening queue',
  tools: 'Vocabulary & pronunciation',
  voices: 'Voice Studio',
  settings: 'Settings',
  'settings-reader': 'Reader appearance',
  'settings-notes': 'Notes settings',
  'settings-accessibility': 'Accessibility',
  'settings-pronunciation': 'Pronunciation rules',
  'settings-ai': 'AI & study mode',
  'settings-pdf': 'PDF import tools',
  'settings-transfer': 'Sync & backup center',
  'settings-about': 'About',
};
const player = new Playback();
type ReaderDraft = { note: string; anchor: number | null; query: string };
const emptyReaderDraft: ReaderDraft = { note: '', anchor: null, query: '' };
export default function App() {
  const {
    route,
    previousPage,
    closeSettings,
    navigate: setPage,
    back,
    forward,
    canBack,
    canForward,
    visited,
  } = useNavigation();
  const page = route.page;
  const [readerPreferences, setReaderPreferences] = useState(false);
  const [books, setBooks] = useState<Book[]>([]);
  const coverAttempts = useRef(new Set<string>());
  const coverQueue = useRef(new Map<string, Book>());
  const coverProcessing = useRef(false);
  const [activeId, setActiveId] = useState('');
  const currentBookId = page === 'reader' && route.bookId ? route.bookId : activeId;
  const [readerDrafts, setReaderDrafts] = useState<Record<string, ReaderDraft>>({});
  const readerDraft = readerDrafts[currentBookId] ?? emptyReaderDraft;
  const note = readerDraft.note;
  const noteAnchor = readerDraft.anchor;
  const readerQuery = readerDraft.query;
  function updateReaderDraft(patch: Partial<ReaderDraft>) {
    setReaderDrafts((drafts) => ({
      ...drafts,
      [currentBookId]: { ...(drafts[currentBookId] ?? emptyReaderDraft), ...patch },
    }));
  }
  function setNote(value: string | ((previous: string) => string)) {
    setReaderDrafts((drafts) => {
      const draft = drafts[currentBookId] ?? emptyReaderDraft;
      return {
        ...drafts,
        [currentBookId]: {
          ...draft,
          note: typeof value === 'function' ? value(draft.note) : value,
        },
      };
    });
  }
  const setNoteAnchor = (anchor: number | null) => updateReaderDraft({ anchor });
  const setReaderQuery = (query: string) => updateReaderDraft({ query });
  const [settings, setSettings] = useState<Settings>(defaultSettings);
  useTheme(settings);
  const [voices, setVoices] = useState<Voice[]>([]);
  const [previewVoice, setPreviewVoice] = useState('');
  useEffect(() => {
    if (!native) return;
    for (const book of books) {
      if (
        !book.cover &&
        ['PDF', 'EPUB', 'DOCX', 'PPTX'].includes(book.format) &&
        !coverAttempts.current.has(book.id)
      )
        coverQueue.current.set(book.id, book);
    }
    if (coverProcessing.current) return;
    coverProcessing.current = true;
    void (async () => {
      while (coverQueue.current.size) {
        const book = coverQueue.current.values().next().value!;
        coverQueue.current.delete(book.id);
        if (coverAttempts.current.has(book.id)) continue;
        coverAttempts.current.add(book.id);
        try {
          const file = await api.sourceFile(book.id);
          if (!file) continue;
          const { cover } = await extractCoverInWorker(file.name, Uint8Array.from(file.bytes));
          if (cover) {
            await api.saveCover(book.id, cover);
            setBooks((items) =>
              items.map((item) => (item.id === book.id ? { ...item, cover } : item)),
            );
          }
        } catch {
          /* A cover is optional; keep the generated jacket when extraction fails. */
        }
      }
      coverProcessing.current = false;
    })();
  }, [books]);
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [details, setDetails] = useState<Book | null>(null);
  const [addingText, setAddingText] = useState(false);
  const [studySeed, setStudySeed] = useState<CardInput | null>(null);
  const [notesEntry, setNotesEntry] = useState<'personal' | 'books'>('personal');
  const [notesEntryVersion, setNotesEntryVersion] = useState(0);
  const [handoffSeed, setHandoffSeed] = useState<(ReaderSelection & { key: string }) | undefined>();
  const [vocabSource, setVocabSource] = useState<{
    key: string;
    bookId: string;
    position: number;
    word?: string;
    text?: string;
  } | null>(null);
  const [readerFullscreen, setReaderFullscreen] = useState(false);
  const [savingAnnotation, setSavingAnnotation] = useState(false);
  const [notebook, setNotebook] = useState(false);
  const [busy, setBusy] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [audioTarget, setAudioTarget] = useState<ActivityTarget | null>(null);
  const [spoken, setSpoken] = useState<(SpokenPassage & { bookId: string }) | null>(null);
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
  const active = books.find((book) => book.id === currentBookId);
  const selectedVoice = voices.find((voice) => voice.id === settings.voice);
  const fail = useCallback(
    (reason: unknown) => setError(String(reason).replace(/^Error: /, '')),
    [],
  );
  const readingTarget = useMemo(
    () => (page === 'reader' && active ? { bookId: active.id, position: active.position } : null),
    [page, active],
  );
  useReadingActivity(readingTarget, audioTarget, playing, fail);
  const recentReaderId = page === 'reader' ? (active?.id ?? null) : null;
  const recentReaderKey = page === 'reader' ? route.key : null;
  const recentAudioId = playing ? (audioTarget?.bookId ?? null) : null;
  const seenRecentAudio = useRef<string | null>(null);
  useEffect(() => {
    if (native && ready && recentReaderId) void api.recordReadingOpen(recentReaderId).catch(fail);
  }, [ready, recentReaderId, recentReaderKey, fail]);
  useEffect(() => {
    const changed = recentAudioId !== seenRecentAudio.current;
    seenRecentAudio.current = recentAudioId;
    if (native && ready && changed && recentAudioId && recentAudioId !== recentReaderId)
      void api.recordReadingOpen(recentAudioId).catch(fail);
  }, [ready, recentAudioId, recentReaderId, fail]);

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
  const downloadingVoice = voices.some((voice) =>
    ['queued', 'downloading', 'validating', 'cancelling'].includes(voice.state),
  );
  useEffect(() => {
    if (page !== 'voices' && !downloadingVoice) return;
    const timer = setInterval(
      () => {
        api.voices().then(setVoices).catch(fail);
      },
      downloadingVoice ? 1000 : 15000,
    );
    return () => clearInterval(timer);
  }, [fail, page, downloadingVoice]);
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

  const preferences = useCallback(
    async (patch: Partial<Settings>) => {
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
    },
    [fail],
  );
  const stop = useCallback(async () => {
    setPreviewVoice('');
    setAudioTarget(null);
    setSpoken(null);
    ++playbackRun.current;
    setPlaying(false);
    setAudioState('Paused');
    if (native) await player.stop().catch(fail);
  }, [fail]);
  useEffect(() => {
    if (previewVoice && page !== 'voices') void stop();
  }, [page, previewVoice, stop]);
  useEffect(() => {
    if (!ready || page !== 'reader' || !route.bookId || route.bookId === activeId) return;
    if (!books.some((book) => book.id === route.bookId)) {
      setPage('library', { replace: true });
      setNotice('That book is no longer in your library.');
      return;
    }
    void stop();
    setAnnotations([]);
    setActiveId(route.bookId);
    void preferences({ lastBook: route.bookId });
  }, [ready, page, route.bookId, activeId, books, setPage, stop, preferences]);
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
    setPage('reader', { bookId: book.id });
    await preferences({ lastBook: book.id });
  }
  async function importBook() {
    setError('');
    setBusy(true);
    try {
      const file = await api.pickBook();
      if (!file) return;
      const parsed = await importInWorker(
        file.name,
        Uint8Array.from(file.bytes),
        settingsRef.current.pdfImport,
      );
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
      setError('Choose an installed Windows voice or download a Studio or Lite voice to listen.');
      return;
    }
    await playBook(active);
  }
  async function preview(id: string, text: string) {
    const stopping = stop();
    const request = playbackRun.current;
    await stopping;
    if (request !== playbackRun.current) return;
    const run = ++playbackRun.current;
    setPreviewVoice(id);
    try {
      await player.start(
        id,
        [text.trim().slice(0, 600)],
        0,
        settingsRef.current.rate,
        async () => {},
        (state) => {
          if (run === playbackRun.current)
            setAudioState(state === 'Playing offline' ? 'Previewing voice' : state);
        },
      );
    } finally {
      if (run === playbackRun.current) {
        setPreviewVoice('');
        setAudioState('Paused');
      }
    }
  }
  async function playBook(first: Book) {
    setPreviewVoice('');
    const run = ++playbackRun.current;
    setPlaying(true);
    setAudioTarget(null);
    setSpoken(null);
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
          (index) => {
            if (run === playbackRun.current) {
              setAudioTarget(index === null ? null : { bookId: current.id, position: index });
              setSpoken(
                index === null
                  ? null
                  : { bookId: current.id, position: index, text: current.chunks[index] },
              );
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
          if (page === 'reader') setPage('reader', { bookId: book.id, replace: true });
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
        setAudioTarget(null);
        setSpoken(null);
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
    setPreviewVoice('');
    if (!active || selection.bookId !== active.id) return;
    if (selectedVoice?.state !== 'installed') {
      setPage('voices');
      setError('Choose an installed Windows voice or download a Studio or Lite voice to listen.');
      return;
    }
    await seek(selection.position);
    const run = ++playbackRun.current;
    setPlaying(true);
    setAudioTarget(null);
    setSpoken(null);
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
        (index) => {
          if (run === playbackRun.current) {
            const text = index === null ? '' : parsed.chunks[index];
            const compact = (value: string) =>
              value
                .normalize('NFKC')
                .toLowerCase()
                .replace(/[\s\-\u00ad]/gu, '');
            const found = text
              ? active.chunks.findIndex(
                  (chunk, n) => n >= selection.position && compact(chunk).includes(compact(text)),
                )
              : -1;
            const anchor = found >= 0 ? found : selection.position;
            setAudioTarget(
              index === null || !active ? null : { bookId: active.id, position: anchor },
            );
            setSpoken(index === null ? null : { bookId: active.id, position: anchor, text });
          }
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
  async function removeBooks(ids: string[]) {
    const removing = new Set(ids);
    if (removing.has(activeId)) {
      ++navigation.current;
      await stop();
    }
    await progressQueue.current.catch(() => {});
    await settingsQueue.current.catch(() => {});
    let deletionError: unknown;
    try {
      for (const id of removing) await api.deleteBook(id);
    } catch (error) {
      deletionError = error;
    }
    const remaining = await api.books();
    const prefs = await api.settings();
    settingsRef.current = prefs;
    persistedSettings.current = prefs;
    setSettings(prefs);
    setBooks(remaining);
    setReaderDrafts((drafts) =>
      Object.fromEntries(
        Object.entries(drafts).filter(([id]) => remaining.some((book) => book.id === id)),
      ),
    );
    if (!remaining.some((book) => book.id === activeId)) {
      setActiveId(remaining[0]?.id ?? '');
      setAnnotations([]);
      await preferences({
        lastBook: remaining[0]?.id ?? '',
        queue: settingsRef.current.queue.filter((id) => remaining.some((book) => book.id === id)),
      });
    }
    setDetails(null);
    if (deletionError) throw deletionError;
  }
  // Global shortcuts ignore editable controls and preserve native text editing.
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.defaultPrevented) return;
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
      if ((event.ctrlKey || event.metaKey) && event.key === 'f') {
        const visibleSearch = [
          search.current,
          ...document.querySelectorAll<HTMLInputElement>(
            'main input[type="search"], main .search input, main input[placeholder^="Search"]',
          ),
        ].find((input) => input && input.getClientRects().length > 0);
        if (visibleSearch) {
          event.preventDefault();
          visibleSearch.focus();
          visibleSearch.select();
        }
        return;
      }
      if ((event.target as HTMLElement).closest('input,textarea,select,button,[contenteditable]'))
        return;
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
  const sectionPages: Page[] = ['library', 'classics', 'lists', 'history'].includes(page)
    ? ['library', 'classics', 'lists', 'history']
    : ['study', 'tools', 'ai'].includes(page)
      ? ['study', 'tools', 'ai']
      : ['queue', 'voices'].includes(page)
        ? ['queue', 'voices']
        : [];

  return (
    <div
      className={`app-shell ${page === 'reader' ? 'reader-workspace' : ''} ${readerFullscreen && page === 'reader' ? 'reader-focus' : ''}`}
    >
      <aside className="sidebar">
        <a
          className="brand"
          aria-label="Vern home"
          href="#home"
          onClick={(event) => {
            event.preventDefault();
            setPage('home');
          }}
        >
          <img src="/vern.png" alt="" />
          <span>
            vern<span className="brand-dot">.</span>
          </span>
        </a>
        <nav aria-label="Main navigation">
          {(
            [
              { id: 'home', icon: 'home', name: 'Home' },
              { id: 'library', icon: 'book', name: 'Library' },
              { id: 'study', icon: 'study', name: 'Study' },
              { id: 'notes', icon: 'note', name: 'Notes' },
            ] as const
          ).map((item) => {
            const current = page.startsWith('settings') ? previousPage : page;
            const selected =
              item.id === 'library'
                ? ['library', 'classics', 'lists', 'history', 'reader'].includes(current)
                : item.id === 'study'
                  ? ['study', 'tools', 'ai'].includes(current)
                  : item.id === 'home'
                    ? ['home', 'insights', 'queue', 'voices'].includes(current)
                    : item.id === current;
            return (
              <button
                key={item.id}
                aria-label={item.name}
                title={item.name}
                className={selected ? 'nav-item selected' : 'nav-item'}
                aria-current={selected ? 'page' : undefined}
                onClick={() => {
                  if (item.id === 'notes') {
                    setNotesEntry('personal');
                    setNotesEntryVersion((version) => version + 1);
                  }
                  setPage(item.id);
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
          <span className="version">Vern desktop</span>
        </div>
      </aside>
      <div className="workspace">
        {page !== 'reader' && (
          <header className="topbar">
            <div className="window-navigation">
              <button
                className="icon-button"
                aria-label="Go back"
                title="Back (Alt+Left)"
                aria-keyshortcuts="Alt+ArrowLeft"
                disabled={!canBack}
                onClick={back}
              >
                <Glyph name="back" />
              </button>
              <button
                className="icon-button"
                aria-label="Go forward"
                title="Forward (Alt+Right)"
                aria-keyshortcuts="Alt+ArrowRight"
                disabled={!canForward}
                onClick={forward}
              >
                <Glyph name="arrow" />
              </button>
              <div className="breadcrumb">
                Your space <span>/</span> <strong>{pageNames[page]}</strong>
              </div>
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
                aria-label={page.startsWith('settings') ? 'Close settings' : 'Settings'}
                title="Settings"
                aria-pressed={page.startsWith('settings')}
                onClick={() => {
                  if (page.startsWith('settings')) {
                    closeSettings();
                  } else {
                    setPage('settings');
                  }
                }}
              >
                <Glyph name="settings" />
              </button>
            </div>
          </header>
        )}
        {!!sectionPages.length && (
          <nav
            className="section-nav"
            aria-label={
              ['library', 'classics', 'lists', 'history'].includes(page)
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
                }}
              >
                <Glyph
                  name={
                    item === 'library'
                      ? 'book'
                      : item === 'history'
                        ? 'history'
                        : item === 'lists'
                          ? 'list'
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
                  : item === 'history'
                    ? 'History'
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
          <WorkspaceScreen page="home" current={page} visited={visited}>
            <HomePage
              books={books}
              active={active}
              onOpen={(book) => void open(book)}
              onNavigate={setPage}
              onImport={() => void importBook()}
              onError={fail}
            />
          </WorkspaceScreen>
          <WorkspaceScreen page="insights" current={page} visited={visited}>
            <InsightsPage
              books={books}
              settings={settings}
              onPreferences={preferences}
              onOpen={(book, position) => void open(book, position)}
            />
          </WorkspaceScreen>
          <WorkspaceScreen page="history" current={page} visited={visited}>
            <ReadingHistoryPage
              books={books}
              active={page === 'history'}
              onOpen={(book) => void open(book)}
            />
          </WorkspaceScreen>
          <WorkspaceScreen page="classics" current={page} visited={visited}>
            <ClassicsPage
              books={books}
              onAdded={async () => {
                setBooks(await api.books());
              }}
              onOpen={(book) => void open(book)}
              onError={fail}
            />
          </WorkspaceScreen>
          <WorkspaceScreen page="library" current={page} visited={visited}>
            <div className="library page">
              <div className="page-heading">
                <div>
                  <h1>
                    Library<span className="heading-dot">.</span>
                  </h1>
                </div>
                <div className="book-count">
                  <strong>{books.length.toString().padStart(2, '0')}</strong>
                  <span>books</span>
                </div>
              </div>
              <LibraryShelf
                books={books}
                queue={settings.queue}
                searchRef={search}
                onOpen={(book, passage) => void open(book, passage)}
                onDetails={setDetails}
                onFavorite={favorite}
                onQueue={(queue) => preferences({ queue })}
                onDelete={removeBooks}
                onPosition={async (book, index) => {
                  if (book.id === activeId) await stop();
                  await position(book, index);
                }}
                onRefresh={async () => setBooks(await api.books())}
              />
            </div>
          </WorkspaceScreen>

          <WorkspaceScreen page="lists" current={page} visited={visited}>
            <ReadingLists
              books={books}
              active={page === 'lists'}
              onOpen={(book) => void open(book)}
            />
          </WorkspaceScreen>

          <WorkspaceScreen page="notes" current={page} visited={visited}>
            <NotesWorkspace
              settings={settings.notes}
              onSettings={(notes) => preferences({ notes })}
              onPreferences={() => setPage('settings-notes')}
              initialView={notesEntry}
              entryVersion={notesEntryVersion}
              books={books}
              onOpen={(book, passage) => void open(book, passage)}
              onError={fail}
            />
          </WorkspaceScreen>
          {page === 'queue' && (
            <div className="page">
              <h1>
                Listening queue<span className="heading-dot">.</span>
              </h1>
              <p className="page-description">Books play in queue order.</p>
              <div className="action-row">
                <button
                  className="primary"
                  disabled={!native || !settings.queue.length || playing || busy}
                  onClick={() => {
                    const book = books.find((b) => b.id === settings.queue[0]);
                    if (!book) return;
                    if (selectedVoice?.state !== 'installed') {
                      setPage('voices');
                      setError(
                        'Choose an installed Windows voice or download a Studio or Lite voice to listen.',
                      );
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
                    <p>Add books from Library to your queue.</p>
                  </div>
                </div>
              )}
            </div>
          )}
          <WorkspaceScreen page="tools" current={page} visited={visited}>
            <ReadingToolsPage
              key={vocabSource?.key ?? 'notebook'}
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
          </WorkspaceScreen>
          <WorkspaceScreen page="study" current={page} visited={visited}>
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
                setNotesEntryVersion((version) => version + 1);
                setPage('notes');
              }}
            />
          </WorkspaceScreen>

          {page === 'reader' && active && (
            <div className={`reading-layout ${notebook ? '' : 'without-notebook'}`}>
              <ReaderSurface
                key={active.id}
                book={active}
                spoken={spoken?.bookId === active.id ? spoken : null}
                settings={settings}
                query={readerQuery}
                onQuery={setReaderQuery}
                searchRef={search}
                onSeek={seek}
                onSettings={preferences}
                onError={fail}
                onImport={() => void importBook()}
                onPreferences={() => setReaderPreferences(true)}
                onBack={() => {
                  if (canBack) back();
                  else setPage('library');
                }}
                onForward={forward}
                canForward={canForward}
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
                    key: crypto.randomUUID(),
                    bookId: active.id,
                    position: selection.position,
                    word: selection.text.slice(0, 100),
                    text: selection.text,
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
                    <button
                      className="icon-button"
                      aria-label="Close notebook"
                      title="Close notebook"
                      onClick={() => setNotebook(false)}
                    >
                      <Glyph name="close" />
                    </button>
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
                        setVocabSource({
                          key: crypto.randomUUID(),
                          bookId: active.id,
                          position: active.position,
                          text: active.chunks[active.position],
                        });
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

          <WorkspaceScreen page="voices" current={page} visited={visited}>
            <VoiceStudio
              voices={voices}
              loading={!ready}
              selected={settings.voice}
              previewing={previewVoice}
              onStop={stop}
              onPreview={preview}
              onSelect={async (voice) => {
                await stop();
                if (!(await preferences({ voice })))
                  throw new Error('The voice selection could not be saved.');
              }}
              onRefresh={async () => setVoices(await api.voices())}
              onDownload={async (voiceId) => {
                await call('download_voice', { voiceId });
                setVoices(await api.voices());
              }}
              onCancel={async (voiceId) => {
                await call('cancel_download', { voiceId });
                setVoices(await api.voices());
              }}
              onWindowsSettings={() => call<void>('open_voice_settings')}
            />
          </WorkspaceScreen>

          {page.startsWith('settings') && (
            <SettingsLayout page={page} onNavigate={setPage}>
              {(page === 'settings' || page === 'settings-reader') && (
                <AppearanceSettings
                  settings={settings}
                  onSave={preferences}
                  readerOnly={page === 'settings-reader'}
                />
              )}
              {page === 'settings-notes' && (
                <NotesPreferences settings={settings} onSave={preferences} />
              )}
              {page === 'settings-accessibility' && (
                <AccessibilityPreferences settings={settings} onSave={preferences} />
              )}
              {page === 'settings-pdf' && (
                <PdfImportPreferences settings={settings} onSave={preferences} />
              )}
              {page === 'settings-ai' && (
                <AiStudyPreferences settings={settings} onSave={preferences} onNavigate={setPage} />
              )}
              {page === 'settings-pronunciation' && (
                <ReadingToolsPage
                  pronunciationOnly
                  settings={settings}
                  books={books}
                  source={null}
                  onSave={preferences}
                  onOpen={(book, position) => void open(book, position)}
                  onCard={(card) => {
                    setStudySeed(card);
                    setPage('study');
                  }}
                />
              )}
              {page === 'settings-transfer' && (
                <>
                  <h2>Sync & backup center</h2>
                  <TransferPanel
                    books={books}
                    beforeAction={beforeTransfer}
                    onRestored={restoredLibrary}
                  />
                </>
              )}
              {page === 'settings-about' && <AboutPage />}
            </SettingsLayout>
          )}
          <section hidden={page !== 'ai'}>
            <AiHandoff
              settings={settings}
              key={handoffSeed?.key ?? 'draft'}
              initial={handoffSeed}
              books={books}
              active={active}
              onError={fail}
              onStudy={() => setPage('study')}
            />
          </section>
        </main>
        {readerPreferences && (
          <Modal title="Reader settings" onClose={() => setReaderPreferences(false)}>
            <AppearanceSettings settings={settings} onSave={preferences} readerOnly />
            <button
              className="secondary"
              onClick={() => {
                setReaderPreferences(false);
                setPage('settings-reader');
              }}
            >
              All settings
            </button>
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
            onRemove={() => removeBooks([details.id])}
          />
        )}
        {active && !readerFullscreen && (
          <footer
            className={page === 'reader' ? 'reader-playback' : 'playback-bar app-playback'}
            aria-label="Playback controls"
          >
            <div className="now-playing">
              <div className="now-icon">
                <Glyph name="book" />
              </div>
              <div>
                <strong>{active?.title || 'Choose a book'}</strong>
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
