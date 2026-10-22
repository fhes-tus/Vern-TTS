export type Book = {
  id: string;
  title: string;
  author: string;
  format: string;
  chunks: string[];
  position: number;
  favorite: boolean;
  collection?: string;
  textAvailable?: boolean;
  updatedAt: number;
};
export type Annotation = {
  id: string;
  bookId: string;
  position: number;
  text: string;
  kind: 'note' | 'bookmark';
};
export type Settings = {
  theme: 'light' | 'dark' | 'system';
  colorTheme: string;
  accentColor: string;
  reduceMotion: boolean;
  fontSize: number;
  readerFont: string;
  voice: string;
  rate: number;
  lastBook: string;
  queue: string[];
  pronunciationRules: PronunciationRule[];
  vocabulary: Vocabulary[];
  readerLayout: 'single' | 'double' | 'scroll';
  readerView: 'flow' | 'original';
};
export const defaultSettings: Settings = {
  theme: 'light',
  colorTheme: 'sage',
  accentColor: '',
  reduceMotion: false,
  fontSize: 21,
  readerFont: 'Bitter',
  voice: 'piper_en_us_lessac',
  rate: 1,
  lastBook: '',
  queue: [],
  pronunciationRules: [],
  vocabulary: [],
  readerLayout: 'single',
  readerView: 'flow',
};
export type Voice = {
  id: string;
  name: string;
  family: string;
  engine: string;
  size: string;
  state: string;
  downloaded: number;
  total: number;
  error: string | null;
};
export type ImportFile = { name: string; bytes: number[] };
export type ParsedBook = Omit<Book, 'id' | 'updatedAt'> & {
  sourcePageStarts?: number[];
  textAvailable?: boolean;
};
export type SourceInfo = {
  name: string;
  format: string;
  pageStarts: number[];
  page: number;
  textAvailable: boolean;
};
export type Flashcard = {
  id: string;
  bookId: string | null;
  position: number | null;
  deck: string;
  front: string;
  back: string;
  recall: string;
  dueAt: number;
  intervalDays: number;
  repetitions: number;
  ease: number;
};
export type CardInput = Pick<Flashcard, 'bookId' | 'position' | 'deck' | 'front' | 'back'> & {
  id?: string;
};
export type BackupPreview = {
  token: string;
  books: number;
  annotations: number;
  flashcards: number;
  quizzes: number;
  notes: number;
  notebooks: number;
  vocabulary: number;
  pronunciationRules: number;
};
export type PronunciationRule = { id: string; find: string; replaceWith: string; enabled: boolean };
export type Vocabulary = {
  id: string;
  word: string;
  definition: string;
  bookId: string | null;
  position: number | null;
};
export type QuizQuestion = {
  question: string;
  options: string[];
  answer: string;
  explanation: string;
};
export type Quiz = {
  id: string;
  title: string;
  bookId: string | null;
  questions: QuizQuestion[];
  bestScore: number | null;
  lastScore: number | null;
  attempts: number;
  createdAt: number;
};
export type QuizInput = Pick<Quiz, 'title' | 'bookId' | 'questions'> & { id?: string };
export type Notebook = { id: string; name: string; createdAt: number };
export type PersonalNote = {
  id: string;
  title: string;
  content: string;
  color: string;
  pinned: boolean;
  isChecklist: boolean;
  labelIds: string[];
  createdAt: number;
  updatedAt: number;
  deletedAt: number | null;
};
export type NoteInput = Omit<PersonalNote, 'id' | 'createdAt' | 'updatedAt' | 'deletedAt'> & {
  id?: string;
};
