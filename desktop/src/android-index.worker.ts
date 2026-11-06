import { indexAndroidText, type AndroidIndexInput } from './android-text-index';
self.onmessage = (event: MessageEvent<{ id: number; input: AndroidIndexInput }>) => {
  try {
    self.postMessage({ id: event.data.id, result: indexAndroidText(event.data.input) });
  } catch (error) {
    self.postMessage({ id: event.data.id, error: String(error).replace(/^Error: /, '') });
  }
};
