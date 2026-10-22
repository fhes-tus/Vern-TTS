// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { defaultSettings, type Book } from './types';

const store = vi.hoisted(() => ({
  books: [] as Book[],
  progress: vi.fn(),
  annotate: vi.fn(),
  notes: [] as unknown[],
  settings: vi.fn(),
}));
vi.mock('./api', () => ({
  native: true,
  call: vi.fn(),
  api: {
    books: async () => store.books,
    settings: async () => defaultSettings,
    voices: async () => [],
    allAnnotations: async () => store.notes,
    flashcards: async () => [],
    personalNotes: async () => [],
    notebooks: async () => [],
    quizzes: async () => [],
    annotations: async () => store.notes,
    sourceInfo: async () => null,
    saveSettings: (...args: unknown[]) => {
      store.settings(...args);
      return Promise.resolve();
    },
    pickBook: async () => ({
      name: 'Alice.txt',
      bytes: [...new TextEncoder().encode('Down the rabbit hole. A second passage.')],
    }),
    importBook: async (input: Book) => {
      const book = { ...input, id: 'alice', updatedAt: 1 };
      store.books = [book];
      return book;
    },
    importSource: async (input: Book) => {
      const book = { ...input, id: 'alice', updatedAt: 1 };
      store.books = [book];
      return book;
    },
    progress: (...args: unknown[]) => {
      store.progress(...args);
      return Promise.resolve();
    },
    annotate: (annotation: unknown) => {
      store.annotate(annotation);
      store.notes = [annotation];
      return Promise.resolve();
    },
  },
}));
vi.mock('./import-client', async () => {
  const { parseBook } = await import('./import');
  return { importInWorker: parseBook };
});
import App from './App';
beforeEach(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      constructor(
        private callback: (entries: { contentRect: { width: number; height: number } }[]) => void,
      ) {}
      observe() {
        this.callback([{ contentRect: { width: 700, height: 500 } }]);
      }
      disconnect() {}
    },
  );
  Object.defineProperty(document, 'fonts', {
    configurable: true,
    value: { ready: Promise.resolve() },
  });
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: () => ({ matches: true }),
  });
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(
    () =>
      ({
        font: '',
        measureText: (text: string) => ({ width: text.length * 8 }),
      }) as unknown as CanvasRenderingContext2D,
  );
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  store.books = [];
  store.notes = [];
});
describe('reader interaction with a mocked native transport', () => {
  it('opens imported text, saves the selected passage and anchors notes to it', async () => {
    Element.prototype.scrollIntoView = vi.fn();
    render(<App />);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Import book' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Import book' }));
    await screen.findByText('A second passage.', {}, { timeout: 10000 });
    fireEvent.click(screen.getByText('A second passage.'), { detail: 1 });
    await waitFor(() => expect(store.progress).toHaveBeenLastCalledWith('alice', 1));
    fireEvent.click(screen.getByRole('button', { name: 'Toggle notebook' }));
    fireEvent.change(screen.getByLabelText('Leave a thought'), {
      target: { value: 'Remember this passage.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save note' }));
    await waitFor(() =>
      expect(store.annotate).toHaveBeenCalledWith(
        expect.objectContaining({
          bookId: 'alice',
          position: 1,
          text: 'Remember this passage.',
          kind: 'note',
        }),
      ),
    );
    expect(await screen.findByText('Remember this passage.')).toBeVisible();
  });
});
