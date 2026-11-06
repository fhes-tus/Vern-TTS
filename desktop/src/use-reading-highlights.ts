import { useEffect, type RefObject } from 'react';
import { passageMatcher } from './passage-ranges';
import { highlightsAtPositions } from './reading-highlights';
import type { Book, ReadingHighlight } from './types';

export const highlightPresets = [
  { value: 'yellow', label: 'Yellow' },
  { value: 'sage', label: 'Sage' },
  { value: 'blue', label: 'Blue' },
  { value: 'rose', label: 'Rose' },
] as const;

export function highlightTone(color: string): string | null {
  if (/^#[0-9a-f]{6}$/i.test(color)) return color.toLowerCase();
  if (highlightPresets.some((item) => item.value === color)) return `var(--highlight-${color})`;
  return null;
}

export function useReadingHighlights(
  viewport: RefObject<HTMLElement | null>,
  book: Book,
  items: ReadingHighlight[],
  pageStarts?: number[],
) {
  useEffect(() => {
    const root = viewport.current;
    if (!root || typeof CSS === 'undefined' || !CSS.highlights || typeof Highlight === 'undefined')
      return;
    const names = new Set<string>();
    const style = document.createElement('style');
    document.head.append(style);
    let frame = 0;
    const clear = () => {
      for (const name of names) CSS.highlights.delete(name);
      names.clear();
    };
    const paint = () => {
      frame = 0;
      const matcher = passageMatcher(root, { pageStarts, length: book.chunks.length });
      const chosen = highlightsAtPositions(
        items.filter((item) => item.bookId === book.id),
        matcher.positions(),
      );
      const groups = new Map<string, { tone: string; ranges: Range[] }>();
      for (const [position, item] of chosen) {
        const tone = highlightTone(item.color);
        if (!tone) continue;
        const name = `vern-saved-${item.color.replace('#', 'hex-').toLowerCase()}`;
        const group = groups.get(name) ?? { tone, ranges: [] };
        group.ranges.push(...matcher.ranges({ position, text: book.chunks[position] }, true));
        groups.set(name, group);
      }
      clear();
      const css: string[] = [];
      for (const [name, group] of groups) {
        CSS.highlights.set(name, new Highlight(...group.ranges));
        names.add(name);
        css.push(
          `::highlight(${name}) { background-color: color-mix(in srgb, ${group.tone} var(--reading-highlight-mix), transparent); }`,
        );
      }
      const sheet = css.join('\n');
      if (style.textContent !== sheet) style.textContent = sheet;
    };
    const schedule = () => {
      if (!frame) frame = requestAnimationFrame(paint);
    };
    paint();
    const observer = new MutationObserver((mutations) => {
      if (mutations.some((mutation) => mutation.target !== style)) schedule();
    });
    observer.observe(root, { childList: true, subtree: true });
    return () => {
      observer.disconnect();
      if (frame) cancelAnimationFrame(frame);
      clear();
      style.remove();
    };
  }, [viewport, book.id, book.chunks, items, pageStarts]);
}
