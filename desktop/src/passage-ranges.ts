export type PassageTarget = { position: number; text: string };
const compact = (text: string) =>
  text
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[\s\-\u00ad]/gu, '');

export function passageMatcher(
  root: HTMLElement,
  source?: { pageStarts?: number[]; length: number },
) {
  const contents = [
    ...root.querySelectorAll<HTMLElement>('[data-passage], .textLayer, .source-content'),
  ];
  const cache = new Map<
    HTMLElement,
    {
      value: string;
      points: { node: Text; offset: number }[];
    }
  >();
  const bounds = (content: HTMLElement) => {
    if (content.hasAttribute('data-passage')) {
      const position = Number(content.dataset.passage);
      return [position, position + 1];
    }
    const pageElement = content.closest<HTMLElement>('[data-source-page]');
    if (!source?.pageStarts || !pageElement) return null;
    const page = Number(pageElement.dataset.sourcePage);
    const first = source.pageStarts[page];
    return Number.isSafeInteger(first)
      ? [first, source.pageStarts[page + 1] ?? source.length]
      : null;
  };
  const textPoints = (content: HTMLElement) => {
    const existing = cache.get(content);
    if (existing) return existing;
    const walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT);
    const points: { node: Text; offset: number }[] = [];
    let value = '';
    let node: Node | null;
    while ((node = walker.nextNode()) && points.length < 250_000) {
      const text = node as Text;
      for (let i = 0; i < text.length && points.length < 250_000; i++) {
        const normalized = compact(text.data[i]);
        value += normalized;
        for (let n = 0; n < normalized.length; n++) points.push({ node: text, offset: i });
      }
    }
    const result = { value, points };
    cache.set(content, result);
    return result;
  };
  return {
    positions() {
      const result = new Set<number>();
      for (const content of contents) {
        const range = bounds(content);
        if (!range) continue;
        for (
          let at = Math.max(0, range[0]);
          at < Math.min(range[1], source?.length ?? range[1]);
          at++
        ) {
          result.add(at);
        }
      }
      return [...result];
    },
    ranges(passage: PassageTarget | null, uniqueOriginal = false) {
      const ranges: Range[] = [];
      const target = compact(passage?.text ?? '');
      if (!target || !passage) return ranges;
      for (const content of contents) {
        const range = bounds(content);
        if (range && (passage.position < range[0] || passage.position >= range[1])) continue;
        const { value, points } = textPoints(content);
        let start = value.indexOf(target);
        let length = target.length;
        if (start < 0 && content.hasAttribute('data-passage') && target.includes(value)) {
          start = 0;
          length = value.length;
        }
        if (start < 0 || !length || !points[start + length - 1]) continue;
        // Repeated original-page text must not mark the wrong occurrence.
        if (
          uniqueOriginal &&
          !content.hasAttribute('data-passage') &&
          value.indexOf(target, start + 1) >= 0
        )
          continue;
        const first = points[start];
        const last = points[start + length - 1];
        const painted = document.createRange();
        painted.setStart(first.node, first.offset);
        painted.setEnd(last.node, last.offset + 1);
        ranges.push(painted);
      }
      return ranges;
    },
  };
}
