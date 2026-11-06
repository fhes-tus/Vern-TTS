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
  cover?: string | null;
};
export type Annotation = {
  id: string;
  bookId: string;
  position: number;
  text: string;
  kind: 'note' | 'bookmark';
};
export type HighlightColor = 'yellow' | 'sage' | 'blue' | 'rose' | `#${string}`;
export type ReadingHighlight = {
  id: string;
  bookId: string;
  position: number;
  endPosition: number;
  color: HighlightColor;
  note: string;
  createdAt: number;
  updatedAt: number;
};
export type HighlightInput = Omit<ReadingHighlight, 'id' | 'createdAt' | 'updatedAt'> & {
  id?: string;
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
  dailyGoalMinutes: number;
  readingIdleSeconds: number;
  lineSpacing: number;
  paperTone: 'theme' | 'white' | 'sepia' | 'dark';
  reduceTransparency: boolean;
  bionicReading: boolean;
  showSectionNumbers: boolean;
  notes: NotesSettings;
  pdfImport: PdfImportSettings;
  aiAssistant: string;
};
export type NotesSettings = {
  paper: 'blank' | 'ruled' | 'grid' | 'dots';
  fontScale: number;
  lineSpacing: number;
  newChecklist: boolean;
  previewLines: number;
  checkedToBottom: boolean;
  grid: boolean;
  sort: 'updated' | 'created' | 'title';
};
export type PdfImportSettings = {
  startPage: number;
  endPage: number;
  cleanupRepeatedLines: boolean;
  removePageNumbers: boolean;
  repairHyphenation: boolean;
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
  dailyGoalMinutes: 0,
  readingIdleSeconds: 120,
  lineSpacing: 1.85,
  paperTone: 'theme',
  reduceTransparency: false,
  bionicReading: false,
  showSectionNumbers: false,
  notes: {
    paper: 'blank',
    fontScale: 1,
    lineSpacing: 1.5,
    newChecklist: false,
    previewLines: 5,
    checkedToBottom: true,
    grid: true,
    sort: 'updated',
  },
  pdfImport: {
    startPage: 1,
    endPage: 0,
    cleanupRepeatedLines: true,
    removePageNumbers: true,
    repairHyphenation: true,
  },
  aiAssistant: 'chatgpt',
};
export type Voice = {
  id: string;
  name: string;
  family: string;
  engine: string;
  size: string;
  language?: string;
  gender?: string;
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
  highlights?: number;
  flashcards: number;
  quizzes: number;
  notes: number;
  notebooks: number;
  sessions: number;
  openedBooks?: number;
  legacyDays?: number;
  legacyBookTimes?: number;
  vocabulary: number;
  pronunciationRules: number;
  sourceFiles?: number;
  source?: string;
  warnings?: string[];
  readingLists?: number;
  noteRevisions?: number;
  noteMedia?: number;
  retainedAndroidBackups?: number;
};
export type OpenedBook = {
  id: string;
  bookId: string | null;
  title: string;
  format: string;
  openedAt: number;
  position: number | null;
  passages: number;
  androidSource?: unknown;
};
export type BackupInspection =
  | { kind: 'desktop'; preview: BackupPreview }
  | { kind: 'android'; token: string; documents: number };
export type AndroidImportSummary = {
  id: string;
  createdAt: number;
  readings: number;
  assets: number;
};
export type ReadingList = {
  id: string;
  title: string;
  description: string;
  createdAt: number;
  updatedAt: number;
  archived: boolean;
  sortMode: 'manual' | 'title' | 'title-desc' | 'newest' | 'oldest';
  items: { bookId: string; addedAt: number }[];
};
export type ReadingListInput = Pick<
  ReadingList,
  'title' | 'description' | 'archived' | 'sortMode'
> & {
  id: string | null;
  bookIds: string[];
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
export type NoteRevision = { id: string; noteId: string; note: PersonalNote };
export type NoteMedia = {
  id: string;
  noteId: string;
  name: string;
  mime: string;
  size: number;
  sha256: string;
  createdAt: number;
};

export type ActivityTarget = { bookId: string; position: number };
export type ActivityEvent = {
  reader: ActivityTarget | null;
  audio: ActivityTarget | null;
  interacting: boolean;
  playbackRequested: boolean;
};
export type ReadingSession = {
  id: string;
  bookId: string | null;
  title: string;
  mode: 'reading' | 'listening';
  startedAt: number;
  updatedAt: number;
  startPosition: number | null;
  endPosition: number | null;
  measuredMs: number;
  correctedMs: number | null;
  excluded: boolean;
  manual: boolean;
  days: { date: string; milliseconds: number }[];
  androidSource?: { record: unknown } | null;
};
export type ActivityReport = {
  today: string;
  days: {
    date: string;
    readingMs: number;
    listeningMs: number;
    legacyMs?: number;
    appUsageMs?: number;
    appOpenCount?: number;
    legacyBookCount?: number;
  }[];
  books: {
    bookId: string | null;
    title: string;
    readingMs: number;
    listeningMs: number;
    sessions: number;
  }[];
  sessions: ReadingSession[];
  totalSessions: number;
  currentStreak: number;
  bestStreak: number;
  legacyDays?: {
    date: string;
    appOpenCount: number;
    usageMs: number;
    readingMs: number;
    recordedBooks: number;
  }[];
  legacyBookTimes?: {
    id: string;
    bookId: string | null;
    title: string;
    milliseconds: number;
    snapshotAt: number;
  }[];
};
