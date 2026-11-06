import { parseBook } from './import';
import type { PdfImportSettings } from './types';
import { extractBookCover } from './book-cover-extraction';
self.onmessage = async (
  event: MessageEvent<{
    name: string;
    bytes: Uint8Array;
    pdfOptions?: PdfImportSettings;
    coverOnly?: boolean;
  }>,
) => {
  try {
    const cover = await extractBookCover(event.data.name, event.data.bytes.slice()).catch(
      (error) => {
        if (event.data.coverOnly) throw error;
        return undefined;
      },
    );
    self.postMessage({
      type: 'vern:import:result',
      book: event.data.coverOnly
        ? { cover }
        : { ...(await parseBook(event.data.name, event.data.bytes, event.data.pdfOptions)), cover },
    });
  } catch (error) {
    self.postMessage({
      type: 'vern:import:result',
      error: String(error),
      stack: error instanceof Error ? error.stack : undefined,
    });
  }
};
