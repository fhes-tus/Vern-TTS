import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';

export const pages = [
  'home',
  'library',
  'classics',
  'lists',
  'history',
  'reader',
  'notes',
  'study',
  'ai',
  'queue',
  'tools',
  'voices',
  'settings',
  'settings-reader',
  'settings-notes',
  'settings-accessibility',
  'settings-pronunciation',
  'settings-ai',
  'settings-pdf',
  'settings-transfer',
  'settings-about',
  'insights',
] as const;
export type Page = (typeof pages)[number];
type Entry = {
  key: string;
  page: Page;
  bookId?: string;
  scroll: number;
  focus?: HTMLElement | null;
};
type Journal = { entries: Entry[]; index: number; visited: Page[] };
type Options = { bookId?: string; replace?: boolean };
function fromHash(): Pick<Entry, 'page' | 'bookId'> {
  const [name, search] = location.hash.slice(1, 512).split('?');
  const page = pages.includes(name as Page) ? (name as Page) : 'home';
  const bookId = new URLSearchParams(search).get('book');
  return { page, bookId: page === 'reader' && bookId && bookId.length <= 100 ? bookId : undefined };
}
const url = (entry: Entry) =>
  `#${entry.page}${entry.bookId ? `?book=${encodeURIComponent(entry.bookId)}` : ''}`;
function capture(entry: Entry): Entry {
  return {
    ...entry,
    scroll: document.querySelector('main')?.scrollTop ?? 0,
    focus: document.activeElement instanceof HTMLElement ? document.activeElement : null,
  };
}
function closeDialog() {
  const dialog = document.querySelector<HTMLDialogElement>('dialog[open]');
  if (!dialog) return false;
  dialog.dispatchEvent(new Event('cancel', { cancelable: true }));
  return true;
}
export function useNavigation() {
  const travelling = useRef(false);
  const [journal, setJournal] = useState<Journal>(() => {
    const entry = { ...fromHash(), key: crypto.randomUUID(), scroll: 0 };
    return { entries: [entry], index: 0, visited: [entry.page] };
  });
  const route = journal.entries[journal.index];
  const settingsOrigin = journal.entries
    .slice(0, journal.index)
    .reduce((last, entry, index) => (entry.page.startsWith('settings') ? last : index), -1);
  useEffect(() => {
    if (history.state?.vernRoute !== route.key)
      history.replaceState({ vernRoute: route.key }, '', url(route));
  }, [route]);
  useEffect(() => {
    const pop = (event: PopStateEvent) => {
      travelling.current = false;
      const current = journal;
      const entries = current.entries.map((entry, i) =>
        i === current.index ? capture(entry) : entry,
      );
      const index = entries.findIndex((entry) => entry.key === event.state?.vernRoute);
      if (index >= 0) {
        setJournal({ ...current, entries, index });
        return;
      }
      const entry = { ...fromHash(), key: crypto.randomUUID(), scroll: 0 };
      history.replaceState({ vernRoute: entry.key }, '', url(entry));
      setJournal({
        entries: [entry],
        index: 0,
        visited: [...new Set([...current.visited, entry.page])],
      });
    };
    window.addEventListener('popstate', pop);
    return () => window.removeEventListener('popstate', pop);
  }, [journal]);
  useLayoutEffect(() => {
    const main = document.querySelector('main');
    if (!main) return;
    const frame = requestAnimationFrame(() => {
      main.scrollTop = route.scroll;
      if (route.focus?.isConnected && route.focus.getClientRects().length)
        route.focus.focus({ preventScroll: true });
      else if (journal.index > 0) {
        const heading = [...main.querySelectorAll<HTMLElement>('h1')].find(
          (element) => element.getClientRects().length > 0,
        );
        const target = heading ?? main;
        target.tabIndex = -1;
        target.focus({ preventScroll: true });
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [route, journal.index]);
  const navigate = useCallback(
    (page: Page, options: Options = {}) => {
      if (travelling.current) return;
      const current = journal.entries[journal.index];
      if (current.page === page && current.bookId === options.bookId) return;
      const entries = journal.entries.map((entry, i) =>
        i === journal.index ? capture(entry) : entry,
      );
      const previousVisit = [...entries]
        .reverse()
        .find((entry) => entry.page === page && entry.bookId === options.bookId);
      const entry: Entry = {
        key: options.replace ? current.key : crypto.randomUUID(),
        page,
        bookId: options.bookId,
        scroll: previousVisit?.scroll ?? 0,
      };
      const next = options.replace
        ? entries.map((old, i) => (i === journal.index ? entry : old))
        : [...entries.slice(0, journal.index + 1), entry].slice(-200);
      if (options.replace) history.replaceState({ vernRoute: entry.key }, '', url(entry));
      else history.pushState({ vernRoute: entry.key }, '', url(entry));
      setJournal({
        entries: next,
        index: options.replace ? journal.index : next.length - 1,
        visited: [...new Set([...journal.visited, page])],
      });
    },
    [journal],
  );
  const back = useCallback(() => {
    if (!travelling.current && !closeDialog() && journal.index > 0) {
      travelling.current = true;
      history.back();
    }
  }, [journal.index]);
  const forward = useCallback(() => {
    if (
      !travelling.current &&
      !document.querySelector('dialog[open]') &&
      journal.index < journal.entries.length - 1
    ) {
      travelling.current = true;
      history.forward();
    }
  }, [journal.index, journal.entries.length]);
  const closeSettings = useCallback(() => {
    if (travelling.current || closeDialog()) return;
    if (settingsOrigin >= 0) {
      travelling.current = true;
      history.go(settingsOrigin - journal.index);
    } else navigate('home', { replace: true });
  }, [journal.index, settingsOrigin, navigate]);
  useEffect(() => {
    const keyboard = (event: KeyboardEvent) => {
      if (!event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
      if (event.key === 'ArrowLeft') {
        event.preventDefault();
        back();
      }
      if (event.key === 'ArrowRight') {
        event.preventDefault();
        forward();
      }
    };
    window.addEventListener('keydown', keyboard);
    return () => window.removeEventListener('keydown', keyboard);
  }, [back, forward]);
  return {
    route,
    previousPage: route.page.startsWith('settings')
      ? (journal.entries[settingsOrigin]?.page ?? 'home')
      : (journal.entries[journal.index - 1]?.page ?? 'home'),
    closeSettings,
    navigate,
    back,
    forward,
    canBack: journal.index > 0,
    canForward: journal.index < journal.entries.length - 1,
    visited: journal.visited,
  };
}
