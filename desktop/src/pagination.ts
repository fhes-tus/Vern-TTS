export type PagePart = { position: number; start: number; end: number; text: string };
export type FlowPage = { parts: PagePart[] };

// Each piece keeps its source offsets: page turns never rewrite the speech/note index.
export async function paginate(
  chunks: string[],
  width: number,
  height: number,
  fontSize: number,
  measure: (text: string) => number,
  cancelled: () => boolean,
): Promise<FlowPage[]> {
  const pages: FlowPage[] = [];
  let parts: PagePart[] = [];
  let used = 0;
  const lineHeight = fontSize * 1.85;
  const nextPage = () => {
    if (parts.length) pages.push({ parts });
    parts = [];
    used = 0;
    if (pages.length > 20000)
      throw new Error('Too many pages at this size. Reduce the text size or use Scroll view.');
  };
  const graphemes = new Intl.Segmenter(undefined, { granularity: 'grapheme' });
  for (let position = 0; position < chunks.length; position++) {
    if (position % 150 === 0) {
      await new Promise((resolve) => setTimeout(resolve, 0));
      if (cancelled()) return [];
    }
    const text = chunks[position];
    let offset = 0;
    let start = 0;
    let line = '';
    if (used && used + lineHeight + fontSize * 0.65 > height) nextPage();
    used += lineHeight + (used ? fontSize * 0.65 : 0);
    const tokens = text.match(/\S+\s*|\s+/gu) ?? [];
    const units = tokens.flatMap((token) =>
      measure(token) > width ? Array.from(graphemes.segment(token), (g) => g.segment) : [token],
    );
    for (const token of units) {
      if (line && measure(line + token) > width) {
        if (used + lineHeight > height) {
          if (offset > start)
            parts.push({ position, start, end: offset, text: text.slice(start, offset) });
          nextPage();
          start = offset;
          used = lineHeight;
        } else used += lineHeight;
        line = '';
      }
      line += token;
      offset += token.length;
    }
    if (offset > start)
      parts.push({ position, start, end: offset, text: text.slice(start, offset) });
  }
  nextPage();
  return pages;
}

export function pageForPosition(pages: FlowPage[], position: number, current = 0) {
  if (pages[current]?.parts.some((p) => p.position === position)) return current;
  return Math.max(
    0,
    pages.findIndex((page) => page.parts.some((p) => p.position === position)),
  );
}
