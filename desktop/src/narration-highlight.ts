import { useEffect, type RefObject } from 'react';
import { passageMatcher } from './passage-ranges';

export type SpokenPassage = { position: number; text: string };

// CSS ranges paint speech independently of the user's native text selection.
// The PDF text layer remains untouched, so copying and selection still work.
export function useNarrationHighlight(
  viewport: RefObject<HTMLElement | null>,
  spoken: SpokenPassage | null,
) {
  useEffect(() => {
    const root = viewport.current;
    if (!root || typeof CSS === 'undefined' || !CSS.highlights || typeof Highlight === 'undefined')
      return;
    const paint = () => {
      const highlight = new Highlight(...passageMatcher(root).ranges(spoken));
      highlight.priority = 1;
      CSS.highlights.set('vern-speech', highlight);
    };
    paint();
    const observer = new MutationObserver(paint);
    observer.observe(root, { childList: true, subtree: true });
    return () => {
      observer.disconnect();
      CSS.highlights.delete('vern-speech');
    };
  }, [viewport, spoken]);
}
