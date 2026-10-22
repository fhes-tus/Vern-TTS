import { parseSourceDocument } from './source-document';
self.onmessage = ({ data }: MessageEvent<{ name: string; bytes: Uint8Array }>) => {
  try {
    self.postMessage({ document: parseSourceDocument(data.name, data.bytes) });
  } catch (error) {
    self.postMessage({ error: String(error) });
  }
};
