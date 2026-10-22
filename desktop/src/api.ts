import { invoke, isTauri } from '@tauri-apps/api/core';
import type {
  Annotation,
  BackupPreview,
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
} from './types';
import { defaultSettings } from './types';
import classics from './data/classics.json';
import type { ParsedBook, SourceInfo } from './types';

export const native = isTauri();
// Browser preview is explicitly read-only: it never owns an alternative database or speech engine.
export async function call<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  if (!native) throw new Error('Open Vern in its desktop window to import, save, or listen.');
  return invoke<T>(command, args);
}
export const api = {
  personalNotes: () => (native ? call<PersonalNote[]>('list_personal_notes') : Promise.resolve([])),
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
  inspectBackup: () => call<BackupPreview | null>('inspect_backup'),
  restoreBackup: (token: string, restoreSettings: boolean) =>
    call<void>('restore_backup', { token, restoreSettings }),
  discardBackup: (token: string) => call<void>('discard_backup', { token }),
  exportBook: (id: string, kind: 'text' | 'notes') => call<boolean>('export_book', { id, kind }),
  exportPassageAudio: (id: string, position: number, voiceId: string, rate: number) =>
    call<boolean>('export_passage_audio', { id, position, voiceId, rate }),
  books: () => (native ? call<Book[]>('list_books') : Promise.resolve([])),
  settings: () => (native ? call<Settings>('get_settings') : Promise.resolve(defaultSettings)),
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
