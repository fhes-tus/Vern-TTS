import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type RefObject,
} from 'react';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import { api } from './api';
import { Glyph } from './Glyph';
import {
  documentInWorker,
  type DocumentBlock,
  type DocumentPage,
  type SourceDocument,
} from './document-model';
import { paginate, pageForPosition, type FlowPage } from './pagination';
import type { Book, Settings, SourceInfo } from './types';
import { readerFontFamily, readerPaperStyle } from './reader-appearance';
import { useNarrationHighlight, type SpokenPassage } from './narration-highlight';
import { ReadingHighlights } from './ReadingHighlights';

const PdfPage = lazy(() => import('./PdfPage').then((module) => ({ default: module.PdfPage })));
export type ReaderSelection = {
  bookId: string;
  text: string;
  position: number;
  endPosition?: number;
  highlightable?: boolean;
};
type Props = {
  book: Book;
  spoken: SpokenPassage | null;
  settings: Settings;
  query: string;
  onQuery: (value: string) => void;
  searchRef: RefObject<HTMLInputElement | null>;
  onSeek: (position: number) => Promise<void>;
  onSettings: (patch: Partial<Settings>) => Promise<boolean>;
  onError: (e: unknown) => void;
  onImport: () => void;
  onPreferences: () => void;
  onBack: () => void;
  onForward: () => void;
  canForward: boolean;
  fullscreen: boolean;
  onFullscreen: () => void;
  notebook: boolean;
  onNotebook: () => void;
  bookmarked: boolean;
  onBookmark: () => void;
  annotationBusy: boolean;
  onNote: (selection: ReaderSelection) => void;
  onVocabulary: (selection: ReaderSelection) => void;
  onFlashcard: (selection: ReaderSelection) => void;
  onAi: (selection: ReaderSelection) => void;
  onListen: (selection: ReaderSelection) => void;
};

function SourceBlock({ block }: { block: DocumentBlock }) {
  if (block.kind === 'image')
    return <img src={block.src} alt={block.text || 'Embedded document image'} draggable={false} />;
  if (block.kind === 'table')
    return (
      <table>
        <tbody>
          {block.cells?.map((row, index) => (
            <tr key={index}>
              {row.map((cell, n) => (
                <td key={n}>{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    );
  return (
    <p
      className={block.level ? `source-heading source-heading-${block.level}` : ''}
      style={{ textAlign: block.align }}
    >
      {block.runs?.length
        ? block.runs.map((run, index) => (
            <span
              key={index}
              style={{
                fontWeight: run.bold ? 700 : undefined,
                fontStyle: run.italic ? 'italic' : undefined,
                fontSize: run.size,
                color: run.color,
              }}
            >
              {run.text}
            </span>
          ))
        : block.text}
    </p>
  );
}
function SourcePage({
  page,
  index,
  width,
  zoom,
}: {
  page: DocumentPage;
  index: number;
  width: number;
  zoom: number;
}) {
  const scale = (width / page.width) * zoom;
  return (
    <div
      className={`source-sheet original-paper ${page.fixed ? 'fixed-source' : ''}`}
      data-source-page={index}
      aria-label={`Original page ${index + 1}`}
      style={{ width: page.width * scale, height: page.height * scale }}
    >
      <div
        className="source-content"
        style={{
          width: page.width,
          minHeight: page.height,
          height: page.fixed ? page.height : undefined,
          transform: `scale(${scale})`,
        }}
      >
        {page.blocks.map((block, n) => (
          <div
            key={n}
            className="source-block"
            style={
              block.box
                ? {
                    position: 'absolute',
                    left: block.box.x,
                    top: block.box.y,
                    width: block.box.width,
                    height: block.kind === 'image' ? block.box.height : undefined,
                    minHeight: block.box.height,
                  }
                : undefined
            }
          >
            <SourceBlock block={block} />
          </div>
        ))}
      </div>
    </div>
  );
}
const normalize = (text: string) =>
  text
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[\s\-\u00ad]/gu, '');
function ReadingText({ text, bionic }: { text: string; bionic: boolean }) {
  if (!bionic) return text;
  return text.split(/(\p{L}[\p{L}\p{M}]*)/u).map((word, n) => {
    if (!/^\p{L}/u.test(word)) return word;
    const letters = Array.from(word);
    const length = Math.ceil(letters.length / 2);
    return (
      <span key={n}>
        <b>{letters.slice(0, length).join('')}</b>
        {letters.slice(length).join('')}
      </span>
    );
  });
}
function pageAt(starts: number[], position: number) {
  let page = 0;
  for (let n = 0; n < starts.length; n++) {
    if (starts[n] > position) break;
    page = n;
  }
  return page;
}

export function ReaderSurface(props: Props) {
  const { book, settings, onError } = props;
  const root = useRef<HTMLDivElement>(null);
  const viewport = useRef<HTMLDivElement>(null);
  useNarrationHighlight(viewport, props.spoken);
  const clickSeek = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(clickSeek.current), []);
  function selectPassage(detail: number, position: number) {
    clearTimeout(clickSeek.current);
    if (detail !== 1 || !window.getSelection()?.isCollapsed) return;
    // A double click selects a word; its first click must not stop narration.
    clickSeek.current = setTimeout(() => {
      if (window.getSelection()?.isCollapsed) void props.onSeek(position);
    }, 300);
  }
  const stage = useRef<HTMLDivElement>(null);
  const [dimensions, setDimensions] = useState({ width: 700, height: 500 });
  const [pages, setPages] = useState<FlowPage[]>([]);
  const [page, setPage] = useState(0);
  const [paginating, setPaginating] = useState(false);
  const [source, setSource] = useState<SourceInfo | null>(null);
  const [document, setDocument] = useState<SourceDocument | null>(null);
  const [pdf, setPdf] = useState<PDFDocumentProxy | null>(null);
  const [sourceError, setSourceError] = useState('');
  const [sourceLoading, setSourceLoading] = useState(false);
  const [zoom, setZoom] = useState(1);
  const [selection, setSelection] = useState<ReaderSelection | null>(null);
  const [selectionAtTop, setSelectionAtTop] = useState(false);
  const [highlightsOpen, setHighlightsOpen] = useState(false);
  const [highlightRequest, setHighlightRequest] = useState<ReaderSelection | null>(null);
  const highlightButton = useRef<HTMLButtonElement>(null);
  const [copied, setCopied] = useState('');
  const [controls, setControls] = useState(true);
  const [jump, setJump] = useState('1');
  const [searchOpen, setSearchOpen] = useState(() => !!props.query);
  const pageRef = useRef(0);
  const latestPosition = useRef(book.position);
  latestPosition.current = book.position;
  const localTurns = useRef(0);
  const pendingTurn = useRef(0);
  const direction = useRef(1);
  const wheel = useRef({ amount: 0, last: 0, changed: 0 });
  const pinch = useRef({ amount: 0, last: 0 });
  const readingSize = useRef(settings.fontSize);
  readingSize.current = settings.fontSize;
  const pointer = useRef<{ x: number; y: number; id: number; allowed: boolean } | null>(null);
  const hideTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const sourceWrites = useRef<Promise<void>>(Promise.resolve());
  const view = book.textAvailable === false ? 'original' : settings.readerView;
  const columns = settings.readerLayout === 'double' ? 2 : 1;
  const count = view === 'flow' ? pages.length : (pdf?.numPages ?? document?.pages.length ?? 0);
  const start = Math.min(page, Math.max(0, count - 1));
  const pageWidth = Math.max(
    120,
    columns === 2 ? (dimensions.width - 14) / 2 : Math.min(1000, dimensions.width),
  );

  useLayoutEffect(() => {
    const element = viewport.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect;
      if (width > 0 && height > 0)
        setDimensions({ width: Math.floor(width), height: Math.floor(height) });
    });
    observer.observe(element);
    const header = root.current?.querySelector<HTMLElement>('.reader-header');
    const layout = root.current?.parentElement;
    const toolbarObserver = new ResizeObserver(() => {
      if (header) layout?.style.setProperty('--reader-toolbar-height', `${header.offsetHeight}px`);
    });
    if (header) toolbarObserver.observe(header);
    return () => {
      observer.disconnect();
      toolbarObserver.disconnect();
      layout?.style.removeProperty('--reader-toolbar-height');
    };
  }, []);
  useEffect(() => {
    let cancelled = false;
    api
      .sourceInfo(book.id)
      .then((info) => {
        if (!cancelled) setSource(info);
      })
      .catch((e) => {
        if (!cancelled) setSourceError(String(e));
      });
    return () => {
      cancelled = true;
    };
  }, [book.id]);
  useEffect(() => {
    if (view !== 'flow') return;
    let cancelled = false;
    setPaginating(true);
    async function layout() {
      await window.document.fonts.load(
        `${settings.fontSize}px ${readerFontFamily(settings.readerFont)}`,
      );
      await window.document.fonts.ready;
      const context = window.document.createElement('canvas').getContext('2d')!;
      context.font = `${settings.bionicReading ? '700 ' : ''}${settings.fontSize}px ${readerFontFamily(settings.readerFont)}`;
      const next = await paginate(
        book.chunks,
        Math.max(75, pageWidth - 58),
        Math.max(100, dimensions.height - 76),
        settings.fontSize,
        (text) => context.measureText(text).width,
        () => cancelled,
        settings.lineSpacing,
      );
      if (cancelled) return;
      const target = pageForPosition(next, latestPosition.current);
      setPages(next);
      setPage(target);
      pageRef.current = target;
      setPaginating(false);
    }
    void layout().catch((e) => {
      if (!cancelled) {
        setPaginating(false);
        onError(e);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [
    book.id,
    book.chunks,
    settings.fontSize,
    settings.readerFont,
    settings.lineSpacing,
    settings.bionicReading,
    view,
    pageWidth,
    dimensions.height,
    onError,
  ]);
  useEffect(() => {
    if (view !== 'original' || !source) return;
    let cancelled = false;
    let loaded: PDFDocumentProxy | undefined;
    let task: { destroy: () => Promise<void> } | undefined;
    setSourceLoading(true);
    setSourceError('');
    setDocument(null);
    setPdf(null);
    async function load() {
      const file = await api.sourceFile(book.id);
      if (!file || cancelled) return;
      if (source!.format === 'PDF') {
        const pdfjs = await import('pdfjs-dist');
        if (cancelled) return;
        pdfjs.GlobalWorkerOptions.workerSrc = new URL(
          'pdfjs-dist/build/pdf.worker.min.mjs',
          import.meta.url,
        ).href;
        const loading = pdfjs.getDocument({
          data: Uint8Array.from(file.bytes),
          useSystemFonts: true,
          cMapUrl: '/pdfjs/cmaps/',
          cMapPacked: true,
          standardFontDataUrl: '/pdfjs/standard_fonts/',
          wasmUrl: '/pdfjs/wasm/',
        });
        task = loading;
        loaded = await loading.promise;
        if (cancelled) {
          await loading.destroy();
          return;
        }
        if (loaded.numPages > 3000) throw new Error('Original PDF exceeds 3,000 pages.');
        setPdf(loaded);
      } else {
        const model = await documentInWorker(file.name, Uint8Array.from(file.bytes));
        if (cancelled) return;
        if (!model.pages.length || model.pages.length > 10000)
          throw new Error('Original document has no pages or exceeds the page limit.');
        let cursor = 0;
        const starts = model.pages.map((item, n) => {
          if (!n) return 0;
          const prefix = normalize(item.text).slice(0, 55);
          if (prefix) {
            const found = book.chunks.findIndex(
              (chunk, index) =>
                index >= cursor &&
                (normalize(chunk).includes(prefix) || prefix.includes(normalize(chunk))),
            );
            if (found >= 0) cursor = found;
          }
          return Math.min(cursor, book.chunks.length - 1);
        });
        await api.sourceIndex(book.id, starts);
        if (cancelled) return;
        setSource((info) => (info ? { ...info, pageStarts: starts } : null));
        setDocument(model);
      }
      if (cancelled) return;
      const target = pageAt(source!.pageStarts, latestPosition.current);
      const saved = source!.page;
      const initial =
        source!.pageStarts[saved] === latestPosition.current ? saved : Math.max(0, target);
      setPage(initial);
      pageRef.current = initial;
      setSourceLoading(false);
    }
    void load().catch((e) => {
      if (!cancelled) {
        setSourceError(String(e));
        setSourceLoading(false);
      }
    });
    return () => {
      cancelled = true;
      void task?.destroy().catch(() => {});
    };
  }, [book.id, view, source?.name]);
  useEffect(() => {
    if (pendingTurn.current || selection || !window.getSelection()?.isCollapsed) return;
    if (view === 'flow' && pages.length) {
      const visible = pages
        .slice(start, start + columns)
        .some((p) => p.parts.some((part) => part.position === book.position));
      if (!visible) {
        const target = pageForPosition(pages, book.position);
        direction.current = target >= pageRef.current ? 1 : -1;
        setPage(target);
        pageRef.current = target;
      }
    } else if (view === 'original' && count && source?.pageStarts.length) {
      const nextStart = source.pageStarts[start + columns] ?? book.chunks.length;
      if (
        book.position < source.pageStarts[start] ||
        (book.position >= nextStart && nextStart > source.pageStarts[start])
      ) {
        const target = pageAt(source.pageStarts, book.position);
        direction.current = target >= pageRef.current ? 1 : -1;
        setPage(target);
        pageRef.current = target;
      }
    }
  }, [
    book.position,
    view,
    pages,
    count,
    source?.pageStarts,
    columns,
    start,
    book.chunks.length,
    selection,
  ]);
  useEffect(() => {
    if (view !== 'flow' || settings.readerLayout !== 'scroll' || selection) return;
    viewport.current?.querySelector('[aria-current="location"]')?.scrollIntoView({
      block: 'nearest',
      behavior:
        settings.reduceMotion || window.matchMedia('(prefers-reduced-motion: reduce)').matches
          ? 'instant'
          : 'smooth',
    });
  }, [book.position, view, settings.readerLayout, selection, settings.reduceMotion]);
  useEffect(() => {
    setJump(String(start + 1));
    if (!settings.reduceMotion && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      stage.current?.animate(
        [
          {
            opacity: 0.6,
            transform: `perspective(1500px) translateX(${direction.current * 18}px) rotateY(${direction.current * 4}deg)`,
          },
          { opacity: 1, transform: 'perspective(1500px) translateX(0) rotateY(0)' },
        ],
        { duration: 230, easing: 'cubic-bezier(.2,.7,.2,1)' },
      );
    }
  }, [start, view, columns, settings.reduceMotion]);
  const revealControls = useCallback(() => {
    setControls(true);
    clearTimeout(hideTimer.current);
    hideTimer.current = setTimeout(() => setControls(false), 2300);
  }, []);
  useEffect(() => {
    revealControls();
    if (props.fullscreen) viewport.current?.focus({ preventScroll: true });
    return () => clearTimeout(hideTimer.current);
  }, [props.fullscreen, revealControls]);
  function clearSelection() {
    window.getSelection()?.removeAllRanges();
    setSelection(null);
    setCopied('');
  }
  function go(target: number) {
    if (!count || !Number.isFinite(target)) return;
    const safe = Math.floor(Math.max(0, Math.min(count - 1, target)));
    setJump(String(safe + 1));
    if (safe === pageRef.current) return;
    direction.current = safe > pageRef.current ? 1 : -1;
    pageRef.current = safe;
    setPage(safe);
    clearSelection();
    const position = view === 'flow' ? pages[safe]?.parts[0]?.position : source?.pageStarts[safe];
    const request = ++localTurns.current;
    pendingTurn.current = request;
    if (view === 'original')
      sourceWrites.current = sourceWrites.current
        .catch(() => {})
        .then(() => api.sourcePage(book.id, safe));
    void Promise.all([
      position === undefined ? Promise.resolve() : props.onSeek(position),
      sourceWrites.current,
    ])
      .catch(onError)
      .finally(() => {
        if (pendingTurn.current === request) pendingTurn.current = 0;
      });
  }
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (event.defaultPrevented) return;
      if (
        (event.ctrlKey || event.metaKey) &&
        event.key.toLowerCase() === 'f' &&
        !window.document.querySelector('dialog[open]')
      ) {
        event.preventDefault();
        setSearchOpen(true);
        requestAnimationFrame(() => {
          props.searchRef.current?.focus();
          props.searchRef.current?.select();
        });
        return;
      }
      if (
        event.defaultPrevented ||
        window.document.querySelector('dialog[open]') ||
        (event.target as HTMLElement).closest('input,textarea,select,[contenteditable]')
      )
        return;
      if (
        (event.ctrlKey || event.metaKey) &&
        event.key.toLowerCase() === 'a' &&
        viewport.current?.contains(event.target as Node)
      ) {
        const range = window.document.createRange();
        range.selectNodeContents(stage.current ?? viewport.current);
        const selection = window.getSelection();
        selection?.removeAllRanges();
        selection?.addRange(range);
        event.preventDefault();
        return;
      }
      if (
        event.shiftKey &&
        ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)
      ) {
        const selected = window.getSelection();
        if (!selected?.rangeCount || !root.current?.contains(selected.anchorNode)) {
          const content = viewport.current?.querySelector(
            '[data-passage], .textLayer, .source-content',
          );
          const text = content
            ? window.document.createTreeWalker(content, NodeFilter.SHOW_TEXT).nextNode()
            : null;
          if (text) selected?.setPosition(text, 0);
        }
        selected?.modify(
          'extend',
          ['ArrowLeft', 'ArrowUp'].includes(event.key) ? 'backward' : 'forward',
          event.ctrlKey || event.metaKey
            ? 'word'
            : ['ArrowUp', 'ArrowDown'].includes(event.key)
              ? 'line'
              : 'character',
        );
        event.preventDefault();
        return;
      }
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (['PageDown', 'PageUp', 'ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
        if (['ArrowLeft', 'ArrowRight'].includes(event.key) && !window.getSelection()?.isCollapsed)
          return;
        event.preventDefault();
        go(
          event.key === 'Home'
            ? 0
            : event.key === 'End'
              ? count - 1
              : pageRef.current +
                (['PageUp', 'ArrowLeft'].includes(event.key) ? -columns : columns),
        );
      }
      if (event.key === 'Escape' && selection) {
        event.preventDefault();
        clearSelection();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  });
  useEffect(() => {
    const element = viewport.current;
    if (!element) return;
    const handler = (event: WheelEvent) => {
      if (event.ctrlKey) {
        event.preventDefault();
        const delta =
          event.deltaY *
          (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? dimensions.height : 1);
        if (view === 'original') {
          setZoom((current) =>
            Math.max(0.5, Math.min(2, Math.round(current * Math.exp(-delta * 0.005) * 100) / 100)),
          );
        } else {
          const now = performance.now();
          if (now - pinch.current.last > 250) pinch.current.amount = 0;
          pinch.current.last = now;
          pinch.current.amount += delta;
          const steps = Math.trunc(pinch.current.amount / 36);
          if (steps) {
            pinch.current.amount -= steps * 36;
            const fontSize = Math.max(14, Math.min(32, readingSize.current - steps));
            if (fontSize !== readingSize.current) {
              readingSize.current = fontSize;
              void props.onSettings({ fontSize });
            }
          }
        }
        return;
      }
      if (zoom > 1 || !window.getSelection()?.isCollapsed || settings.readerLayout === 'scroll')
        return;
      const horizontal = event.shiftKey
        ? event.deltaY || event.deltaX
        : Math.abs(event.deltaX) > Math.abs(event.deltaY) * 1.25
          ? event.deltaX
          : 0;
      if (!horizontal) return;
      event.preventDefault();
      const now = performance.now();
      if (now - wheel.current.changed < 450) return;
      if (
        now - wheel.current.last > 180 ||
        Math.sign(horizontal) !== Math.sign(wheel.current.amount)
      )
        wheel.current.amount = 0;
      wheel.current.last = now;
      wheel.current.amount += horizontal;
      if (Math.abs(wheel.current.amount) >= 65) {
        go(pageRef.current + Math.sign(wheel.current.amount) * columns);
        wheel.current.amount = 0;
        wheel.current.changed = now;
      }
    };
    element.addEventListener('wheel', handler, { passive: false });
    return () => element.removeEventListener('wheel', handler);
  });
  useEffect(() => {
    const changed = () => {
      const selected = window.getSelection();
      if (
        !selected?.rangeCount ||
        selected.isCollapsed ||
        !viewport.current?.contains(selected.anchorNode) ||
        !viewport.current.contains(selected.focusNode)
      ) {
        setSelection(null);
        return;
      }
      const text = selected.toString().trim().slice(0, 10000);
      if (!text) {
        setSelection(null);
        return;
      }
      const range = selected.getRangeAt(0);
      setSelectionAtTop(
        range.getBoundingClientRect().top < viewport.current.getBoundingClientRect().top + 110,
      );
      const anchor =
        range.startContainer.nodeType === 1
          ? (range.startContainer as Element)
          : range.startContainer.parentElement;
      let position = Number(anchor?.closest('[data-passage]')?.getAttribute('data-passage') ?? -1);
      if (position < 0) {
        const page = Number(
          anchor?.closest('[data-source-page]')?.getAttribute('data-source-page') ?? start,
        );
        const first = source?.pageStarts[page] ?? 0;
        const end = source?.pageStarts[page + 1] ?? book.chunks.length;
        const found = book.chunks.findIndex(
          (chunk, index) =>
            index >= first &&
            index < Math.max(first + 1, end) &&
            normalize(chunk).includes(normalize(text).slice(0, 55)),
        );
        position = found >= 0 ? found : first;
      }
      let endPosition = position;
      let highlightable = false;
      const intersected = [...viewport.current.querySelectorAll<HTMLElement>('[data-passage]')]
        .filter((element) => {
          if (!range.intersectsNode(element)) return false;
          const portion = range.cloneRange();
          const block = window.document.createRange();
          block.selectNodeContents(element);
          if (portion.compareBoundaryPoints(Range.START_TO_START, block) < 0)
            portion.setStart(block.startContainer, block.startOffset);
          if (portion.compareBoundaryPoints(Range.END_TO_END, block) > 0)
            portion.setEnd(block.endContainer, block.endOffset);
          return !!portion.toString().trim();
        })
        .map((element) => Number(element.dataset.passage))
        .filter((at) => Number.isSafeInteger(at) && at >= 0 && at < book.chunks.length);
      if (intersected.length) {
        position = Math.min(...intersected);
        endPosition = Math.max(...intersected);
        highlightable = true;
      } else {
        const page = Number(
          anchor?.closest('[data-source-page]')?.getAttribute('data-source-page') ?? start,
        );
        const first = source?.pageStarts[page] ?? 0;
        const end = source?.pageStarts[page + 1] ?? book.chunks.length;
        const candidates = book.chunks.flatMap((chunk, index) =>
          index >= first && index < end && normalize(chunk).includes(normalize(text))
            ? [index]
            : [],
        );
        if (candidates.length === 1) {
          position = candidates[0];
          endPosition = position;
          highlightable = true;
        }
      }
      setSelection({ bookId: book.id, text, position, endPosition, highlightable });
      setCopied('');
      revealControls();
    };
    window.document.addEventListener('selectionchange', changed);
    return () => window.document.removeEventListener('selectionchange', changed);
  }, [book.id, book.chunks, source?.pageStarts, start, revealControls]);

  const visible = Array.from({ length: columns }, (_, index) => start + index);
  const renderFlow = (index: number) => (
    <article
      className="flow-paper"
      aria-label={`Reading page ${index + 1}`}
      style={readerPaperStyle(settings)}
    >
      <div className="flow-page-body">
        {pages[index]?.parts.map((part) => (
          <p
            key={`${part.position}-${part.start}`}
            data-passage={part.position}
            data-start={part.start}
            data-section={settings.showSectionNumbers ? part.position + 1 : undefined}
            aria-current={book.position === part.position ? 'location' : undefined}
            onClick={(event) => {
              if (!pointer.current) selectPassage(event.detail, part.position);
            }}
          >
            <ReadingText text={part.text} bionic={settings.bionicReading} />
          </p>
        ))}
      </div>
      <span className="paper-page-number">{index + 1}</span>
    </article>
  );
  return (
    <section
      ref={root}
      className={`reader-surface ${props.fullscreen ? 'reader-is-fullscreen' : ''} ${controls ? 'controls-visible' : ''}`}
      aria-label="Reading room"
      onPointerMove={revealControls}
      onKeyDown={revealControls}
    >
      <header className="reader-header reader-hud">
        <button
          className="icon-button reader-back"
          aria-label="Go back"
          title="Back from this book"
          onClick={props.onBack}
        >
          <Glyph name="back" />
        </button>
        <button
          className="icon-button reader-back"
          aria-label="Go forward"
          title="Forward (Alt+Right)"
          disabled={!props.canForward}
          onClick={props.onForward}
        >
          <Glyph name="arrow" />
        </button>
        <div className="reader-title">
          <h1 title={`${book.title}${book.author ? ` — ${book.author}` : ''}`}>{book.title}</h1>
          <p>{book.author}</p>
        </div>
        <div className="reader-page-controls reader-inline-pages">
          <button
            className="secondary small"
            aria-label="Previous page"
            disabled={!count || start === 0}
            onClick={() => go(start - columns)}
          >
            <Glyph name="back" />
            <span className="reader-direction-label">Previous</span>
          </button>
          <button
            className="secondary small"
            aria-label="Next page"
            disabled={!count || start + columns >= count}
            onClick={() => go(start + columns)}
          >
            <span className="reader-direction-label">Next</span>
            <Glyph name="arrow" />
          </button>
          <form
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              if (jump.trim()) go(Number(jump) - 1);
              else setJump(String(start + 1));
            }}
          >
            <label>
              Page{' '}
              <input
                aria-label="Go to page"
                title="Type a page number and press Enter"
                type="number"
                min={1}
                max={Math.max(1, count)}
                value={jump}
                onChange={(e) => setJump(e.target.value)}
                onFocus={(event) => event.currentTarget.select()}
              />
            </label>
            <span aria-live="polite">
              of {count || '…'}
              {columns === 2 && count > start + 1
                ? ` · showing ${start + 1}–${Math.min(count, start + 2)}`
                : ''}
            </span>
            <button type="submit" className="sr-only" disabled={!count} tabIndex={-1}>
              Jump to page
            </button>
          </form>
          <details className="reader-input-help reader-shortcuts">
            <summary aria-label="Page turning shortcuts">
              <span className="reader-shortcut-label">Shortcuts</span>
              <span className="reader-shortcut-symbol" aria-hidden="true">
                ?
              </span>
            </summary>
            <p>
              Select text normally · ←/→ or PgUp/PgDn · Shift + wheel or horizontal trackpad ·
              Alt-drag or drag a page edge
              {document?.notice && view === 'original' ? ` · ${document.notice}` : ''}
            </p>
          </details>
        </div>
        <div className="reader-control-row">
          <label>
            <span className="sr-only">View</span>
            <select
              aria-label="Reader view"
              value={view}
              onChange={(e) => {
                clearSelection();
                void props.onSettings({ readerView: e.target.value as Settings['readerView'] });
              }}
            >
              <option value="flow" disabled={book.textAvailable === false}>
                Reading
              </option>
              <option value="original">Original document</option>
            </select>
          </label>
          <label>
            <span className="sr-only">Pages</span>
            <select
              aria-label="Page layout"
              value={settings.readerLayout}
              onChange={(e) => {
                clearSelection();
                if (e.target.value === 'double') go(Math.floor(pageRef.current / 2) * 2);
                void props.onSettings({ readerLayout: e.target.value as Settings['readerLayout'] });
              }}
            >
              <option value="single">Single page</option>
              <option value="double">Two pages</option>
              <option value="scroll">Scroll</option>
            </select>
          </label>
          {view === 'flow' && (
            <div className="reader-text-size" role="group" aria-label="Reading text size">
              <button
                className="icon-button"
                aria-label="Decrease text size"
                title="Decrease text size"
                disabled={settings.fontSize <= 14}
                onClick={() =>
                  void props.onSettings({ fontSize: Math.max(14, settings.fontSize - 1) })
                }
              >
                A−
              </button>
              <output aria-label="Current text size">{settings.fontSize}</output>
              <button
                className="icon-button"
                aria-label="Increase text size"
                title="Increase text size"
                disabled={settings.fontSize >= 32}
                onClick={() =>
                  void props.onSettings({ fontSize: Math.min(32, settings.fontSize + 1) })
                }
              >
                A+
              </button>
            </div>
          )}
          {view === 'original' && (
            <label>
              <span className="sr-only">Zoom</span>
              <select
                aria-label="Document zoom"
                value={zoom}
                onChange={(e) => setZoom(Number(e.target.value))}
              >
                {[...new Set([0.5, 0.75, 1, 1.25, 1.5, 2, zoom])]
                  .sort((a, b) => a - b)
                  .map((value) => (
                    <option key={value} value={value}>
                      {Math.round(value * 100)}%
                    </option>
                  ))}
              </select>
            </label>
          )}
          <button
            className="icon-button"
            aria-label="Search this book"
            title="Search this book (Ctrl+F)"
            aria-expanded={searchOpen}
            onClick={() => {
              setSearchOpen((open) => !open);
              if (searchOpen) props.onQuery('');
              requestAnimationFrame(() => props.searchRef.current?.focus());
            }}
          >
            <Glyph name="search" />
          </button>
          <button
            className="icon-button"
            title={props.bookmarked ? 'Remove bookmark' : 'Bookmark passage'}
            aria-label={
              props.bookmarked ? 'Remove bookmark from current passage' : 'Bookmark current passage'
            }
            aria-pressed={props.bookmarked}
            disabled={props.annotationBusy}
            onClick={props.onBookmark}
          >
            <Glyph name="bookmark" />
            <span className="sr-only">Bookmark</span>
          </button>
          <button
            className="icon-button"
            aria-label="Toggle notebook"
            title="Notebook"
            aria-pressed={props.notebook}
            onClick={props.onNotebook}
          >
            <Glyph name="note" />
            <span className="sr-only">Notebook</span>
          </button>
          <button
            ref={highlightButton}
            className="icon-button"
            aria-label="Show reading highlights"
            title="Reading highlights"
            aria-expanded={highlightsOpen}
            onClick={() => {
              setHighlightRequest(null);
              setHighlightsOpen(true);
            }}
          >
            <Glyph name="edit" />
          </button>
          <button
            className="icon-button"
            aria-label={props.fullscreen ? 'Exit fullscreen reading' : 'Fullscreen reading'}
            title={props.fullscreen ? 'Exit fullscreen (F11)' : 'Fullscreen (F11)'}
            onClick={props.onFullscreen}
          >
            <Glyph name={props.fullscreen ? 'window' : 'fullscreen'} />
            <span className="sr-only">{props.fullscreen ? 'Exit fullscreen' : 'Fullscreen'}</span>
          </button>
          <button
            className="icon-button"
            aria-label="Reader settings"
            title="Reader settings"
            onClick={props.onPreferences}
          >
            <Glyph name="settings" />
          </button>
        </div>
        {searchOpen && (
          <div
            className="reader-search-panel"
            onKeyDown={(event) => {
              if (event.key === 'Escape') {
                event.preventDefault();
                setSearchOpen(false);
                props.onQuery('');
              }
            }}
          >
            <label className="search">
              <input
                ref={props.searchRef}
                type="search"
                aria-label="Search this book"
                placeholder="Search this book…"
                value={props.query}
                onChange={(e) => props.onQuery(e.target.value)}
              />
            </label>
            <button
              className="icon-button"
              aria-label="Close book search"
              title="Close search"
              onClick={() => {
                setSearchOpen(false);
                props.onQuery('');
              }}
            >
              <Glyph name="close" />
            </button>
            {props.query && (
              <div className="reader-match-list">
                {book.chunks
                  .map((text, position) => ({ text, position }))
                  .filter((part) => part.text.toLowerCase().includes(props.query.toLowerCase()))
                  .slice(0, 30)
                  .map((part) => (
                    <button
                      key={part.position}
                      onClick={() => {
                        void props.onSeek(part.position);
                        props.onQuery('');
                        setSearchOpen(false);
                      }}
                    >
                      <small>Passage {part.position + 1}</small>
                      {part.text.slice(0, 160)}
                    </button>
                  ))}
              </div>
            )}
          </div>
        )}
      </header>
      {props.fullscreen && (
        <button
          className="reader-exit-focus"
          onClick={props.onFullscreen}
          aria-label="Leave fullscreen reading"
        >
          Exit fullscreen · Esc
        </button>
      )}
      {selection && (
        <div
          className="selection-actions"
          data-placement={selectionAtTop ? 'bottom' : 'top'}
          role="toolbar"
          aria-label="Selected text actions"
          onMouseDown={(event) => event.preventDefault()}
        >
          <span title={selection.text}>
            {selection.text.slice(0, 70)}
            {selection.text.length > 70 ? '…' : ''}
          </span>
          <button
            onClick={() =>
              void navigator.clipboard.writeText(selection.text).then(
                () => setCopied('Copied'),
                () => setCopied('Use Ctrl/Cmd C to copy'),
              )
            }
          >
            <Glyph name="copy" />
            Copy
          </button>
          <button onClick={() => props.onListen(selection)}>
            <Glyph name="headphones" />
            Listen
          </button>
          <button onClick={() => props.onNote(selection)}>
            <Glyph name="note" />
            Add to note
          </button>
          <button
            disabled={!selection.highlightable}
            title={
              selection.highlightable
                ? 'Highlight the selected passages'
                : 'Choose an exact passage in Reading view to highlight it'
            }
            onClick={() => {
              setHighlightRequest(selection);
              setHighlightsOpen(true);
            }}
          >
            Highlight passages
          </button>
          <button onClick={() => props.onVocabulary(selection)}>
            <Glyph name="dictionary" />
            Word tools
          </button>
          <button onClick={() => props.onFlashcard(selection)}>
            <Glyph name="study" />
            Flashcard
          </button>
          <button onClick={() => props.onAi(selection)}>
            <Glyph name="sparkles" />
            Ask AI
          </button>
          <button onClick={clearSelection}>
            <Glyph name="close" />
            Clear selection
          </button>
          {copied && <small role="status">{copied}</small>}
        </div>
      )}
      <div
        ref={viewport}
        tabIndex={0}
        className={`reader-viewport ${settings.readerLayout === 'scroll' ? 'reader-scroll-mode' : ''}`}
        aria-label="Book pages"
        onPointerDown={(event) => {
          const edge = (event.target as HTMLElement).closest('[data-page-edge]');
          const allowed = event.pointerType === 'touch' || event.altKey || !!edge;
          pointer.current = { x: event.clientX, y: event.clientY, id: event.pointerId, allowed };
          if (allowed && event.pointerType !== 'touch') {
            event.currentTarget.setPointerCapture(event.pointerId);
            event.preventDefault();
          }
        }}
        onPointerUp={(event) => {
          const down = pointer.current;
          pointer.current = null;
          if (
            !down ||
            down.id !== event.pointerId ||
            !down.allowed ||
            !window.getSelection()?.isCollapsed
          )
            return;
          const dx = event.clientX - down.x;
          const dy = event.clientY - down.y;
          if (Math.abs(dx) >= 55 && Math.abs(dx) > Math.abs(dy) * 1.5) {
            go(pageRef.current + (dx < 0 ? columns : -columns));
            event.preventDefault();
          }
        }}
        onPointerCancel={() => {
          pointer.current = null;
        }}
      >
        {view === 'original' && !source && (
          <div className="reader-placeholder">
            <h2>Attach the original file</h2>
            <p>
              This older import contains extracted text only. Reimport the same file to retain its
              source while keeping saved notes and progress.
            </p>
            <button className="primary" onClick={props.onImport}>
              Reimport original document
            </button>
            {sourceError && <p role="alert">{sourceError}</p>}
          </div>
        )}
        {sourceLoading && (
          <p className="reader-working" role="status">
            Opening original document…
          </p>
        )}
        {sourceError && source && (
          <p className="reader-working" role="alert">
            {sourceError}
          </p>
        )}
        {view === 'flow' && paginating && (
          <p className="reader-working" role="status">
            Laying out pages…
          </p>
        )}
        {view === 'flow' && settings.readerLayout === 'scroll' ? (
          <div className="flow-scroll" style={readerPaperStyle(settings)}>
            <button
              className="text-button"
              disabled={book.position < 35}
              onClick={() => void props.onSeek(Math.max(0, book.position - 60))}
            >
              Earlier passages ↑
            </button>
            {book.chunks
              .slice(Math.max(0, book.position - 35), Math.max(0, book.position - 35) + 90)
              .map((text, index) => {
                const position = Math.max(0, book.position - 35) + index;
                return (
                  <p
                    key={position}
                    data-passage={position}
                    data-section={settings.showSectionNumbers ? position + 1 : undefined}
                    aria-current={position === book.position ? 'location' : undefined}
                    onClick={(e) => {
                      selectPassage(e.detail, position);
                    }}
                  >
                    <ReadingText text={text} bionic={settings.bionicReading} />
                  </p>
                );
              })}
            <button
              className="text-button"
              disabled={book.position + 55 >= book.chunks.length}
              onClick={() =>
                void props.onSeek(Math.min(book.chunks.length - 1, book.position + 55))
              }
            >
              Later passages ↓
            </button>
          </div>
        ) : (
          <div
            ref={stage}
            className="reader-spread"
            data-layout={columns === 2 ? 'double' : 'single'}
            data-page-start={start + 1}
            style={{ gridTemplateColumns: `repeat(${columns}, minmax(0,1fr))` }}
          >
            {visible.map((index, slot) => (
              <div className="page-slot" key={slot}>
                {index < count ? (
                  view === 'flow' ? (
                    renderFlow(index)
                  ) : pdf ? (
                    <Suspense fallback={<p>Preparing page…</p>}>
                      <PdfPage
                        document={pdf}
                        page={index}
                        width={pageWidth}
                        zoom={zoom}
                        onError={onError}
                      />
                    </Suspense>
                  ) : document?.pages[index] ? (
                    <SourcePage
                      page={document.pages[index]}
                      index={index}
                      width={pageWidth}
                      zoom={zoom}
                    />
                  ) : null
                ) : count > 0 ? (
                  <div className="blank-paper" aria-hidden="true" />
                ) : null}
              </div>
            ))}
          </div>
        )}
        <div
          className="reader-edge reader-edge-left"
          data-page-edge="previous"
          aria-hidden="true"
        />
        <div className="reader-edge reader-edge-right" data-page-edge="next" aria-hidden="true" />
      </div>
      <ReadingHighlights
        key={book.id}
        book={book}
        viewport={viewport}
        pageStarts={source?.pageStarts}
        open={highlightsOpen}
        request={highlightRequest}
        onSeek={props.onSeek}
        onClose={() => {
          setHighlightsOpen(false);
          setHighlightRequest(null);
          revealControls();
          requestAnimationFrame(() => highlightButton.current?.focus());
        }}
      />
    </section>
  );
}
