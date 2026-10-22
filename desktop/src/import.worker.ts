import { parseBook } from './import';
self.onmessage = async (event: MessageEvent<{ name: string; bytes: Uint8Array }>) => {
  try {
    self.postMessage({
      type: 'vern:import:result',
      book: await parseBook(event.data.name, event.data.bytes),
    });
  } catch (error) {
    self.postMessage({ type: 'vern:import:result', error: String(error) });
  }
};
