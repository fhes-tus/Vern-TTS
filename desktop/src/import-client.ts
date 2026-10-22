import type { ParsedBook } from './types';
export function importInWorker(name: string, bytes: Uint8Array): Promise<ParsedBook> {
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
      if (data.error) reject(new Error(data.error));
      else resolve(data.book);
    };
    worker.onerror = (event) => {
      stop();
      reject(new Error(event.message || 'Import worker failed.'));
    };
    worker.postMessage({ name, bytes }, [bytes.buffer]);
  });
}
