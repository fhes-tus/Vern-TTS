import type { PdfImportSettings } from './types';
export function cleanPdfPages(pages: string[], options: PdfImportSettings): string[] {
  const { startPage, endPage } = options;
  if (
    !Number.isInteger(startPage) ||
    !Number.isInteger(endPage) ||
    startPage < 1 ||
    startPage > pages.length ||
    endPage < 0 ||
    endPage > 3000 ||
    (endPage !== 0 && endPage < startPage)
  )
    throw new Error(
      'The PDF page range is outside this document. Adjust PDF import tools in Settings.',
    );
  const last = Math.min(endPage || pages.length, pages.length);
  const selected = pages
    .slice(startPage - 1, last)
    .map((page) => page.replace(/\r\n?/g, '\n').split('\n'));
  const normalize = (line: string) => line.trim().replace(/\s+/g, ' ');
  const occurrences = new Map<string, number>();
  if (options.cleanupRepeatedLines)
    for (const lines of selected) {
      if (lines.length < 3) continue;
      for (const line of new Set([normalize(lines[0]), normalize(lines.at(-1) ?? '')])) {
        if (line && line.length <= 160) occurrences.set(line, (occurrences.get(line) ?? 0) + 1);
      }
    }
  return pages.map((_, index) => {
    if (index < startPage - 1 || index >= last) return '';
    const lines = selected[index - startPage + 1];
    let text = lines
      .filter((line, n) => {
        const edge = n === 0 || n === lines.length - 1;
        const cleaned = normalize(line);
        if (
          options.removePageNumbers &&
          /^(?:page\s+)?\d{1,5}(?:\s*(?:of|\/)\s*\d{1,5})?$/i.test(cleaned)
        )
          return false;
        return !(
          edge &&
          lines.length >= 3 &&
          (occurrences.get(cleaned) ?? 0) >= Math.max(3, Math.ceil(selected.length * 0.6))
        );
      })
      .join('\n');
    if (options.repairHyphenation) text = text.replace(/(\p{L})-\s*\n\s*(?=\p{L})/gu, '$1');
    return text;
  });
}
