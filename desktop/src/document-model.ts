export type DocumentRun = {
  text: string;
  bold?: boolean;
  italic?: boolean;
  size?: number;
  color?: string;
};
export type DocumentBlock = {
  kind: 'paragraph' | 'image' | 'table';
  text: string;
  runs?: DocumentRun[];
  level?: number;
  align?: 'left' | 'center' | 'right' | 'justify';
  src?: string;
  cells?: string[][];
  box?: { x: number; y: number; width: number; height: number };
  background?: string;
};
export type DocumentPage = {
  width: number;
  height: number;
  blocks: DocumentBlock[];
  text: string;
  fixed: boolean;
};
export type SourceDocument = { pages: DocumentPage[]; notice: string };

export function documentInWorker(name: string, bytes: Uint8Array): Promise<SourceDocument> {
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./document.worker.ts', import.meta.url), { type: 'module' });
    const finish = () => {
      clearTimeout(timer);
      worker.terminate();
    };
    const timer = setTimeout(() => {
      finish();
      reject(new Error('Document layout timed out. Try a smaller file.'));
    }, 120000);
    worker.onmessage = ({ data }) => {
      finish();
      if (data.error) reject(new Error(data.error));
      else resolve(data.document);
    };
    worker.onerror = (event) => {
      finish();
      reject(new Error(event.message || 'Document layout failed.'));
    };
    worker.postMessage({ name, bytes }, [bytes.buffer]);
  });
}
