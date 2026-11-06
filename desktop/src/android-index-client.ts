import type { AndroidIndex, AndroidIndexInput } from './android-text-index';
export class AndroidIndexer {
  private worker: Worker;
  private next = 0;
  private pending: {
    id: number;
    resolve: (result: AndroidIndex) => void;
    reject: (error: Error) => void;
    stop: () => void;
  } | null = null;
  constructor() {
    this.worker = new Worker(new URL('./android-index.worker.ts', import.meta.url), {
      type: 'module',
    });
    this.worker.onmessage = ({ data }) => {
      const pending = this.pending;
      if (!pending || pending.id !== data?.id) return;
      this.pending = null;
      pending.stop();
      if (data.error) pending.reject(new Error(data.error));
      else pending.resolve(data.result);
    };
    this.worker.onerror = (event) =>
      this.close(new Error(event.message || 'Android backup indexing failed.'));
  }
  index(input: AndroidIndexInput, signal?: AbortSignal): Promise<AndroidIndex> {
    if (this.pending) return Promise.reject(new Error('Another reading is already being indexed.'));
    if (signal?.aborted) return Promise.reject(new Error('Backup conversion cancelled.'));
    return new Promise((resolve, reject) => {
      const id = ++this.next;
      const aborted = () => this.close(new Error('Backup conversion cancelled.'));
      const timer = setTimeout(
        () => this.close(new Error('Backup conversion timed out. Try a smaller backup.')),
        120000,
      );
      signal?.addEventListener('abort', aborted, { once: true });
      this.pending = {
        id,
        resolve,
        reject,
        stop: () => {
          clearTimeout(timer);
          signal?.removeEventListener('abort', aborted);
        },
      };
      this.worker.postMessage({ id, input });
    });
  }
  close(error = new Error('Backup conversion closed.')) {
    this.worker.terminate();
    if (this.pending) {
      const pending = this.pending;
      this.pending = null;
      pending.stop();
      pending.reject(error);
    }
  }
}
