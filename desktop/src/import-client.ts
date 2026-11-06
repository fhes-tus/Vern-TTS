import type { ParsedBook, PdfImportSettings } from './types';
export function importInWorker(
  name: string,
  bytes: Uint8Array,
  pdfOptions?: PdfImportSettings,
): Promise<ParsedBook> {
  return runWorker(name, bytes, { pdfOptions });
}
export function extractCoverInWorker(name: string, bytes: Uint8Array): Promise<{ cover?: string }> {
  return runWorker(name, bytes, { coverOnly: true });
}
function runWorker<T>(
  name: string,
  bytes: Uint8Array,
  options: { pdfOptions?: PdfImportSettings; coverOnly?: boolean },
): Promise<T> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./import.worker.ts', import.meta.url), { type: 'module' });
    const timer = setTimeout(() => {
      worker.terminate();
      reject(new Error('Import timed out. Try a smaller book.'));
    }, 120_000);
    const stop = () => {
      clearTimeout(timer);
      worker.terminate();
    };
    worker.onmessage = ({ data }) => {
      if (data?.type !== 'vern:import:result') return;
      stop();
      if (data.error) {
        const error = new Error(data.error);
        if (data.stack) error.stack = data.stack;
        reject(error);
      } else resolve(data.book);
    };
    worker.onerror = (event) => {
      stop();
      reject(new Error(event.message || 'Import worker failed.'));
    };
    worker.postMessage({ name, bytes, ...options }, [bytes.buffer]);
  });
}
