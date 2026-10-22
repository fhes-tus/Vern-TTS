import { useEffect, useRef, useState } from 'react';
import { TextLayer, type PDFDocumentProxy } from 'pdfjs-dist';
import 'pdfjs-dist/web/pdf_viewer.css';

export function PdfPage({
  document,
  page,
  width,
  zoom,
  onError,
}: {
  document: PDFDocumentProxy;
  page: number;
  width: number;
  zoom: number;
  onError: (e: unknown) => void;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const text = useRef<HTMLDivElement>(null);
  const [rendered, setRendered] = useState(-1);
  const [size, setSize] = useState({ width: 0, height: 0 });
  useEffect(() => {
    let cancelled = false;
    let render: ReturnType<Awaited<ReturnType<PDFDocumentProxy['getPage']>>['render']> | undefined;
    let layer: TextLayer | undefined;
    async function paint() {
      const pdfPage = await document.getPage(page + 1);
      if (cancelled) return;
      const natural = pdfPage.getViewport({ scale: 1 });
      if (
        !Number.isFinite(natural.width / natural.height) ||
        natural.width <= 0 ||
        natural.height <= 0 ||
        natural.width / natural.height > 30 ||
        natural.height / natural.width > 30
      )
        throw new Error('This PDF page has unsupported dimensions.');
      const scale = Math.max(0.1, (width / natural.width) * zoom);
      const viewport = pdfPage.getViewport({ scale });
      const ratio = Math.min(
        window.devicePixelRatio || 1,
        2,
        Math.sqrt(8_000_000 / (viewport.width * viewport.height)),
      );
      const staging = window.document.createElement('canvas');
      staging.width = Math.max(1, Math.ceil(viewport.width * ratio));
      staging.height = Math.max(1, Math.ceil(viewport.height * ratio));
      const context = staging.getContext('2d')!;
      render = pdfPage.render({
        canvas: staging,
        canvasContext: context,
        viewport,
        transform: [ratio, 0, 0, ratio, 0, 0],
      });
      await render.promise;
      if (cancelled) return;
      const stagingText = window.document.createElement('div');
      layer = new TextLayer({
        textContentSource: await pdfPage.getTextContent(),
        container: stagingText,
        viewport,
      });
      await layer.render();
      if (cancelled || !canvas.current || !text.current) return;
      canvas.current.width = staging.width;
      canvas.current.height = staging.height;
      canvas.current.getContext('2d')!.drawImage(staging, 0, 0);
      text.current.replaceChildren(...stagingText.childNodes);
      text.current.style.setProperty('--total-scale-factor', String(scale));
      text.current.style.setProperty('--scale-factor', String(scale));
      setSize({ width: viewport.width, height: viewport.height });
      setRendered(page);
      staging.width = 0;
      staging.height = 0;
    }
    void paint().catch((e) => {
      if (!cancelled) onError(e);
    });
    return () => {
      cancelled = true;
      render?.cancel();
      layer?.cancel();
    };
  }, [document, page, width, zoom, onError]);
  return (
    <div
      className="pdf-page original-paper"
      data-source-page={page}
      data-rendered-page={rendered}
      aria-label={`Original page ${page + 1}`}
      aria-busy={rendered !== page}
      style={{ width: size.width || width, height: size.height || width * 1.4 }}
    >
      <canvas ref={canvas} aria-hidden="true" style={{ width: size.width, height: size.height }} />
      <div ref={text} className="textLayer" />
      {rendered !== page && (
        <span className="pdf-loading" role="status">
          Rendering page {page + 1}…
        </span>
      )}
    </div>
  );
}
