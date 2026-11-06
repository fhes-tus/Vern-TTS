import { archiveXml, resolve, xml } from './import';

// PDF.js's browser canvas factory creates DOM canvases for embedded images.
// Covers run in a worker, so every intermediate canvas must stay offscreen too.
class CoverCanvasFactory {
  create(width: number, height: number) {
    this.check(width, height);
    const canvas = new OffscreenCanvas(Math.ceil(width), Math.ceil(height));
    return { canvas, context: canvas.getContext('2d')! };
  }
  reset(target: { canvas: OffscreenCanvas }, width: number, height: number) {
    this.check(width, height);
    target.canvas.width = Math.ceil(width);
    target.canvas.height = Math.ceil(height);
  }
  destroy(target: {
    canvas: OffscreenCanvas | null;
    context: OffscreenCanvasRenderingContext2D | null;
  }) {
    if (target.canvas) {
      target.canvas.width = 0;
      target.canvas.height = 0;
    }
    target.canvas = null;
    target.context = null;
  }
  private check(width: number, height: number) {
    if (
      !Number.isFinite(width * height) ||
      width <= 0 ||
      height <= 0 ||
      width > 8192 ||
      height > 8192 ||
      Math.ceil(width) * Math.ceil(height) > 8_000_000
    )
      throw new Error('PDF cover image exceeds the pixel limit.');
  }
}

function dimensions(bytes: Uint8Array): [number, number] | null {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (bytes.length > 24 && bytes[0] === 137 && bytes[1] === 80)
    return [view.getUint32(16), view.getUint32(20)];
  if (bytes.length > 10 && String.fromCharCode(...bytes.subarray(0, 3)) === 'GIF')
    return [view.getUint16(6, true), view.getUint16(8, true)];
  if (bytes[0] === 255 && bytes[1] === 216) {
    let n = 2;
    while (n + 9 < bytes.length) {
      if (bytes[n] !== 255) break;
      const marker = bytes[n + 1];
      const size = view.getUint16(n + 2);
      if (size < 2 || n + 2 + size > bytes.length) break;
      if ([192, 193, 194, 195, 197, 198, 199, 201, 202, 203, 205, 206, 207].includes(marker))
        return [view.getUint16(n + 7), view.getUint16(n + 5)];
      n += 2 + size;
    }
  }
  return null;
}
async function jpeg(canvas: OffscreenCanvas) {
  const bytes = new Uint8Array(
    await (await canvas.convertToBlob({ type: 'image/jpeg', quality: 0.82 })).arrayBuffer(),
  );
  if (bytes.length > 256 * 1024) return undefined;
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return `data:image/jpeg;base64,${btoa(binary)}`;
}
async function imageCover(bytes: Uint8Array) {
  const size = dimensions(bytes);
  if (!size || size.some((n) => n < 1 || n > 8192) || size[0] * size[1] > 8_000_000)
    return undefined;
  const image = await createImageBitmap(new Blob([Uint8Array.from(bytes)]));
  try {
    const ratio = Math.min(400 / image.width, 600 / image.height, 1);
    const canvas = new OffscreenCanvas(
      Math.max(1, Math.round(image.width * ratio)),
      Math.max(1, Math.round(image.height * ratio)),
    );
    const context = canvas.getContext('2d')!;
    context.fillStyle = 'white';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    return await jpeg(canvas);
  } finally {
    image.close();
  }
}
export async function extractBookCover(
  name: string,
  bytes: Uint8Array,
): Promise<string | undefined> {
  if (bytes.length > 32 * 1024 * 1024) throw new Error('Cover source exceeds 32 MB.');
  const format = name.split('.').pop()?.toLowerCase();
  if (format === 'pdf') {
    const pdfjs = await import('pdfjs-dist');
    pdfjs.GlobalWorkerOptions.workerSrc = new URL(
      'pdfjs-dist/build/pdf.worker.min.mjs',
      import.meta.url,
    ).href;
    const assetRoot = globalThis.location.origin;
    const task = pdfjs.getDocument({
      data: bytes,
      CanvasFactory: CoverCanvasFactory,
      disableFontFace: true,
      useWorkerFetch: true,
      maxImageSize: 8_000_000,
      canvasMaxAreaInBytes: 32_000_000,
      cMapUrl: `${assetRoot}/pdfjs/cmaps/`,
      cMapPacked: true,
      standardFontDataUrl: `${assetRoot}/pdfjs/standard_fonts/`,
      wasmUrl: `${assetRoot}/pdfjs/wasm/`,
    });
    try {
      const pdf = await task.promise;
      if (pdf.numPages > 3000) throw new Error('PDF exceeds the page limit.');
      let best: OffscreenCanvas | undefined;
      let bestScore = 1;
      for (let n = 1; n <= Math.min(5, pdf.numPages); n++) {
        const page = await pdf.getPage(n);
        const natural = page.getViewport({ scale: 1 });
        if (
          !Number.isFinite(natural.width / natural.height) ||
          natural.width <= 0 ||
          natural.height <= 0
        )
          continue;
        const viewport = page.getViewport({
          scale: Math.min(400 / natural.width, 600 / natural.height),
        });
        const canvas = new OffscreenCanvas(
          Math.max(1, Math.min(400, Math.ceil(viewport.width))),
          Math.max(1, Math.min(600, Math.ceil(viewport.height))),
        );
        const context = canvas.getContext('2d')!;
        await page.render({
          canvas: canvas as unknown as HTMLCanvasElement,
          canvasContext: context as unknown as CanvasRenderingContext2D,
          viewport,
        }).promise;
        const data = context.getImageData(0, 0, canvas.width, canvas.height).data;
        let sum = 0,
          squares = 0,
          count = 0,
          nonWhite = 0;
        for (let at = 0; at < data.length; at += 64) {
          const value = data[at] * 0.299 + data[at + 1] * 0.587 + data[at + 2] * 0.114;
          sum += value;
          squares += value * value;
          count++;
          if (value < 245) nonWhite++;
        }
        let score =
          Math.sqrt(Math.max(0, squares / count - (sum / count) ** 2)) *
          (1 + (nonWhite / count) * 2);
        if (n === 1 && score > 2) score *= 1.6;
        if (score > bestScore) {
          best = canvas;
          bestScore = score;
        }
        page.cleanup();
      }
      return best ? await jpeg(best) : undefined;
    } finally {
      await task.destroy();
    }
  }
  if (!['epub', 'docx', 'pptx'].includes(format ?? '')) return undefined;
  const files = archiveXml(bytes, true);
  if (format === 'epub') {
    const decode = (path: string) => xml(new TextDecoder().decode(files[path]));
    if (!files['META-INF/container.xml']) return undefined;
    const opfPath = decode('META-INF/container.xml')
      .getElementsByTagName('rootfile')[0]
      ?.getAttribute('full-path');
    if (!opfPath || !files[opfPath]) return undefined;
    const opf = decode(opfPath);
    const base = opfPath.slice(0, opfPath.lastIndexOf('/') + 1);
    const coverId = Array.from(opf.getElementsByTagName('meta'))
      .find((meta) => meta.getAttribute('name') === 'cover')
      ?.getAttribute('content');
    const items = Array.from(opf.getElementsByTagName('item'));
    const item =
      items.find((item) => item.getAttribute('id') === coverId) ??
      items.find((item) => item.getAttribute('properties')?.split(/\s+/).includes('cover-image')) ??
      items.find(
        (item) =>
          /cover/i.test(`${item.getAttribute('id')} ${item.getAttribute('href')}`) &&
          /^image\//.test(item.getAttribute('media-type') ?? ''),
      );
    const href = item?.getAttribute('href');
    const path = href ? resolve(base, href) : '';
    return files[path] ? await imageCover(files[path]) : undefined;
  }
  const thumbnail = Object.keys(files).find((path) =>
    /^docProps\/thumbnail\.(png|jpe?g)$/i.test(path),
  );
  const firstImage = Object.keys(files).find((path) =>
    /^(word|ppt)\/media\/image1\.(png|jpe?g)$/i.test(path),
  );
  const path = thumbnail ?? firstImage;
  return path ? await imageCover(files[path]) : undefined;
}
