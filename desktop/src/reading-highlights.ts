import type { ReadingHighlight } from './types';

// One colour per visible passage: the newest overlapping mark wins. Expired
// entries leave the heap lazily, so a large notebook is not scanned per passage.
export function highlightsAtPositions(
  highlights: ReadingHighlight[],
  positions: number[],
): Map<number, ReadingHighlight> {
  const ordered = [...highlights]
    .filter(
      (item) =>
        Number.isSafeInteger(item.position) &&
        item.position >= 0 &&
        Number.isSafeInteger(item.endPosition) &&
        item.endPosition >= item.position,
    )
    .sort((a, b) => a.position - b.position);
  const visible = [...new Set(positions.filter((p) => Number.isSafeInteger(p) && p >= 0))].sort(
    (a, b) => a - b,
  );
  const heap: ReadingHighlight[] = [];
  const newer = (a: ReadingHighlight, b: ReadingHighlight) =>
    a.updatedAt !== b.updatedAt ? a.updatedAt > b.updatedAt : a.id > b.id;
  const push = (item: ReadingHighlight) => {
    heap.push(item);
    let at = heap.length - 1;
    while (at > 0) {
      const parent = (at - 1) >> 1;
      if (!newer(heap[at], heap[parent])) break;
      [heap[at], heap[parent]] = [heap[parent], heap[at]];
      at = parent;
    }
  };
  const pop = () => {
    const last = heap.pop();
    if (!heap.length || !last) return;
    heap[0] = last;
    let at = 0;
    while (true) {
      const left = at * 2 + 1;
      if (left >= heap.length) break;
      const right = left + 1;
      const child = right < heap.length && newer(heap[right], heap[left]) ? right : left;
      if (!newer(heap[child], heap[at])) break;
      [heap[at], heap[child]] = [heap[child], heap[at]];
      at = child;
    }
  };
  const result = new Map<number, ReadingHighlight>();
  let cursor = 0;
  for (const position of visible) {
    while (cursor < ordered.length && ordered[cursor].position <= position) {
      if (ordered[cursor].endPosition >= position) push(ordered[cursor]);
      cursor++;
    }
    while (heap.length && heap[0].endPosition < position) pop();
    if (heap[0]) result.set(position, heap[0]);
  }
  return result;
}
