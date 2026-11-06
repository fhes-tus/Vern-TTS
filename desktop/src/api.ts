import { invoke, isTauri } from '@tauri-apps/api/core';
import type {
  Annotation,
  BackupPreview,
  OpenedBook,
  BackupInspection,
  AndroidImportSummary,
  Book,
  CardInput,
  Flashcard,
  ImportFile,
  Quiz,
  QuizInput,
  PersonalNote,
  NoteInput,
  Notebook,
  Settings,
  Voice,
  ActivityEvent,
  ActivityReport,
  ActivityTarget,
  ReadingList,
  ReadingListInput,
  NoteRevision,
  NoteMedia,
  ReadingHighlight,
  HighlightInput,
} from './types';
import { defaultSettings } from './types';
import classics from './data/classics.json';
import type { ParsedBook, SourceInfo } from './types';
import type { AndroidIndex, AndroidIndexInput } from './android-text-index';

export const native = isTauri();
// Browser preview is explicitly read-only: it never owns an alternative database or speech engine.
export async function call<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  if (!native) throw new Error('Open Vern in its desktop window to import, save, or listen.');
  return invoke<T>(command, args);
}
export const api = {
  openedBooks: () => (native ? call<OpenedBook[]>('list_opened_books') : Promise.resolve([])),
  recordReadingOpen: (id: string) => call<void>('record_reading_open', { id }),
  removeOpenedBook: (id: string) => call<void>('remove_opened_book', { id }),
  clearOpenedBooks: () => call<void>('clear_opened_books'),
  openReference: (site: 'wikipedia' | 'wiktionary' | 'translate', text: string) =>
    call<void>('open_reference', { site, text }),
  highlights: (bookId?: string) =>
    native
      ? call<ReadingHighlight[]>('list_highlights', { bookId: bookId ?? null })
      : Promise.resolve([]),
  saveHighlight: (input: HighlightInput) => call<ReadingHighlight>('save_highlight', { input }),
  deleteHighlight: (id: string) => call<void>('delete_highlight', { id }),
  readingLists: () => (native ? call<ReadingList[]>('list_reading_lists') : Promise.resolve([])),
  saveReadingList: (input: ReadingListInput) => call<ReadingList>('save_reading_list', { input }),
  deleteReadingList: (id: string) => call<void>('delete_reading_list', { id }),
  activity: () =>
    native
      ? call<ActivityReport>('get_reading_activity')
      : Promise.resolve<ActivityReport>({
          today: new Date().toLocaleDateString('en-CA'),
          days: [],
          books: [],
          sessions: [],
          totalSessions: 0,
          currentStreak: 0,
          bestStreak: 0,
        }),
  recordActivity: (event: ActivityEvent) => call<void>('record_reading_activity', { event }),
  correctSession: (id: string, correctedMs: number | null, excluded: boolean) =>
    call<void>('correct_reading_session', { id, correctedMs, excluded }),
  addSession: (
    target: ActivityTarget,
    mode: 'reading' | 'listening',
    startedAt: number,
    durationMs: number,
  ) => call<void>('add_reading_session', { target, mode, startedAt, durationMs }),
  exportSessions: () => call<boolean>('export_reading_sessions'),
  personalNotes: () => (native ? call<PersonalNote[]>('list_personal_notes') : Promise.resolve([])),
  noteRevisions: (id: string) => call<NoteRevision[]>('list_note_revisions', { id }),
  noteMedia: (id: string) => call<NoteMedia[]>('list_note_media', { id }),
  attachNoteMedia: (id: string) => call<NoteMedia | null>('attach_note_media', { id }),
  noteMediaUrl: (id: string) => call<string>('get_note_media', { id }),
  deleteNoteMedia: (id: string) => call<void>('delete_note_media', { id }),
  notebooks: () => (native ? call<Notebook[]>('list_notebooks') : Promise.resolve([])),
  savePersonalNote: (input: NoteInput) => call<PersonalNote>('save_personal_note', { input }),
  saveNotebook: (id: string | null, name: string) => call<void>('save_notebook', { id, name }),
  trashPersonalNote: (id: string, trashed: boolean) =>
    call<void>('trash_personal_note', { id, trashed }),
  deletePersonalNote: (id: string) => call<void>('delete_personal_note', { id }),
  deleteNotebook: (id: string) => call<void>('delete_notebook', { id }),
  quizzes: () => (native ? call<Quiz[]>('list_quizzes') : Promise.resolve([])),
  saveQuiz: (input: QuizInput) => call<Quiz>('save_quiz', { input }),
  submitQuiz: (id: string, answers: number[]) => call<Quiz>('submit_quiz', { id, answers }),
  deleteQuiz: (id: string) => call<void>('delete_quiz', { id }),
  importFlashcards: (cards: CardInput[]) => call<number>('import_flashcards', { cards }),
  classics: () =>
    native
      ? call<Classic[]>('list_classics')
      : Promise.resolve(classics.map((book) => ({ ...book, bookId: null }))),
  downloadClassic: (id: string) => call<ImportFile>('download_classic', { id }),
  cancelClassic: (id: string) => call<void>('cancel_classic_download', { id }),
  saveClassic: (id: string, book: ParsedBook, file: ImportFile) =>
    call<Book>('save_classic', { id, book, file }),
  exportBackup: () => call<boolean>('export_backup'),
  inspectBackup: () => call<BackupInspection | null>('inspect_backup'),
  androidBackupText: (token: string) =>
    call<AndroidIndexInput | null>('android_backup_text', { token }),
  acceptAndroidText: (token: string, index: number, converted: AndroidIndex) =>
    call<void>('accept_android_text', { token, index, converted }),
  finishAndroidBackup: (token: string) => call<BackupPreview>('finish_android_backup', { token }),
  androidImports: () =>
    native ? call<AndroidImportSummary[]>('list_android_imports') : Promise.resolve([]),
  exportAndroidImport: (id: string) => call<boolean>('export_android_import', { id }),
  deleteAndroidImport: (id: string) => call<void>('delete_android_import', { id }),
  restoreBackup: (token: string, restoreSettings: boolean) =>
    call<void>('restore_backup', { token, restoreSettings }),
  discardBackup: (token: string) => call<void>('discard_backup', { token }),
  exportBook: (id: string, kind: 'text' | 'notes') => call<boolean>('export_book', { id, kind }),
  exportPassageAudio: (id: string, position: number, voiceId: string, rate: number) =>
    call<boolean>('export_passage_audio', { id, position, voiceId, rate }),
  books: () => (native ? call<Book[]>('list_books') : Promise.resolve([])),
  saveCover: (id: string, cover: string) => call<void>('save_book_cover', { id, cover }),
  settings: async (): Promise<Settings> => {
    const saved = native ? await call<Settings>('get_settings') : defaultSettings;
    return {
      ...defaultSettings,
      ...saved,
      notes: { ...defaultSettings.notes, ...saved.notes },
      pdfImport: { ...defaultSettings.pdfImport, ...saved.pdfImport },
    };
  },
  saveSettings: (settings: Settings) => call<void>('save_settings', { settings }),
  pickBook: () => call<ImportFile | null>('pick_book'),
  importBook: (book: Omit<Book, 'id' | 'updatedAt'>) => call<Book>('save_book', { book }),
  importSource: (book: ParsedBook, file: ImportFile) =>
    call<Book>('save_book_with_source', {
      book,
      file,
      pageStarts: book.sourcePageStarts ?? [],
      textAvailable: book.textAvailable ?? true,
    }),
  sourceInfo: (id: string) => call<SourceInfo | null>('get_source_info', { id }),
  sourceFile: (id: string) => call<ImportFile | null>('get_source_file', { id }),
  sourcePage: (id: string, page: number) => call<void>('save_source_page', { id, page }),
  sourceIndex: (id: string, pages: number[]) => call<void>('save_source_index', { id, pages }),
  progress: (id: string, position: number) => call<void>('save_progress', { id, position }),
  favorite: (id: string) => call<void>('toggle_favorite', { id }),
  updateBook: (id: string, title: string, author: string, collection: string) =>
    call<void>('update_book', { id, title, author, collection }),
  deleteBook: (id: string) => call<void>('delete_book', { id }),
  allAnnotations: () => (native ? call<Annotation[]>('list_all_annotations') : Promise.resolve([])),
  deleteAnnotation: (id: string) => call<void>('delete_annotation', { id }),
  flashcards: () => (native ? call<Flashcard[]>('list_flashcards') : Promise.resolve([])),
  saveFlashcard: (input: CardInput) => call<void>('save_flashcard', { input }),
  reviewFlashcard: (id: string, rating: string) => call<void>('review_flashcard', { id, rating }),
  deleteFlashcard: (id: string) => call<void>('delete_flashcard', { id }),
  annotations: (bookId: string) =>
    native ? call<Annotation[]>('list_annotations', { bookId }) : Promise.resolve([]),
  annotate: (annotation: Annotation) => call<void>('save_annotation', { annotation }),
  voices: () =>
    native
      ? call<Voice[]>('list_voices')
      : Promise.resolve<Voice[]>([
          {
            id: 'piper_en_us_lessac',
            name: 'Lessac',
            family: 'Lite',
            engine: 'Piper',
            size: '~20 MB',
            state: 'unavailable',
            downloaded: 0,
            total: 0,
            error: 'Downloads are available in the desktop app.',
          },
          {
            id: 'kokoro_af_heart',
            name: 'Heart',
            family: 'Studio',
            engine: 'Kokoro',
            size: '~126 MB',
            state: 'unavailable',
            downloaded: 0,
            total: 0,
            error: 'Downloads are available in the desktop app.',
          },
        ]),
};
export type Classic = (typeof classics)[number] & { bookId: string | null };
