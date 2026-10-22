import type { ReactNode } from 'react';

// One visual language across navigation, reader tools and workspace actions.
export function Glyph({ name }: { name: string }) {
  const paths: Record<string, ReactNode> = {
    pin: <path d="m8 3 8 0-1 5 3 4v2H6v-2l3-4-1-5ZM12 14v7" />,
    home: (
      <>
        <path d="m3 10 9-7 9 7v11H3Z" />
        <path d="M9 21v-8h6v8" />
      </>
    ),
    compass: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="m16 8-2 6-6 2 2-6Z" />
      </>
    ),
    palette: (
      <>
        <path d="M21 12a9 9 0 1 0-9 9h2a2 2 0 0 0 0-4h-1a2 2 0 0 1 0-4h6a2 2 0 0 0 2-1Z" />
        <path d="M7 8h.01M11 6h.01M16 8h.01M6 13h.01" />
      </>
    ),
    sparkles: <path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5ZM20 2v4m-2-2h4" />,
    moon: <path d="M20 15A9 9 0 0 1 9 4a9 9 0 1 0 11 11Z" />,
    grid: (
      <>
        <rect x="3" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="3" width="7" height="7" rx="1" />
        <rect x="3" y="14" width="7" height="7" rx="1" />
        <rect x="14" y="14" width="7" height="7" rx="1" />
      </>
    ),
    list: <path d="M8 5h13M8 12h13M8 19h13M3 5h.01M3 12h.01M3 19h.01" />,
    external: <path d="M14 3h7v7m0-7L10 14M10 3H3v18h18v-7" />,
    book: (
      <>
        <path d="M12 5C8 2 3 4 3 4v15s5-2 9 1c4-3 9-1 9-1V4s-5-2-9 1Z" />
        <path d="M12 5v15" />
      </>
    ),
    headphones: (
      <>
        <path d="M4 14v-3a8 8 0 0 1 16 0v3" />
        <rect x="3" y="12" width="4" height="8" rx="2" />
        <rect x="17" y="12" width="4" height="8" rx="2" />
      </>
    ),
    search: (
      <>
        <circle cx="10" cy="10" r="6" />
        <path d="m15 15 6 6" />
      </>
    ),
    plus: <path d="M12 5v14M5 12h14" />,
    settings: (
      <>
        <path d="M4 7h16M4 17h16" />
        <circle cx="9" cy="7" r="3" />
        <circle cx="16" cy="17" r="3" />
      </>
    ),
    arrow: <path d="m9 5 7 7-7 7" />,
    back: <path d="m15 5-7 7 7 7" />,
    note: <path d="M5 3h14v18H5zM8 8h8M8 12h8M8 16h5" />,
    bookmark: <path d="M6 3h12v18l-6-4-6 4Z" />,
    play: <path d="m8 5 11 7-11 7Z" />,
    pause: <path d="M8 5v14M16 5v14" />,
    heart: <path d="M12 20 3.8 12A5 5 0 0 1 12 5a5 5 0 0 1 8.2 7Z" />,
    close: <path d="m6 6 12 12M18 6 6 18" />,
    download: <path d="M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4" />,
    check: <path d="m5 12 4 4L19 6" />,
    study: (
      <>
        <rect x="6" y="6" width="15" height="15" rx="2" />
        <path d="M17 3H5a2 2 0 0 0-2 2v12M10 12h7m-7 4h4" />
      </>
    ),
    dictionary: (
      <>
        <path d="M4 4h16v16H4zM7 16l3-8 3 8m-5-3h4m3-5h2m-2 4h2m-2 4h2" />
      </>
    ),
    queue: (
      <>
        <path d="M3 5h15M3 10h15M3 15h8m4-1 6 4-6 4Z" />
      </>
    ),
    fullscreen: <path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5" />,
    window: (
      <>
        <rect x="4" y="5" width="16" height="14" rx="2" />
        <path d="M4 9h16" />
      </>
    ),
    copy: (
      <>
        <rect x="8" y="8" width="13" height="13" rx="2" />
        <path d="M16 3H5a2 2 0 0 0-2 2v11" />
      </>
    ),
    edit: <path d="m15 4 5 5M4 20l5-1L21 7a2 2 0 0 0-5-5L4 14Z" />,
    trash: <path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7" />,
    save: (
      <>
        <path d="M4 3h13l4 4v14H3V3ZM7 3v6h10V3M7 21v-8h10v8" />
      </>
    ),
    sun: (
      <>
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1 1m12 12 1 1M5 19l1-1M18 6l1-1" />
      </>
    ),
    type: <path d="M3 6h12M9 6v15M6 21h6m2-6 4-9 4 9m-6-3h4" />,
  };
  return (
    <svg
      className="glyph"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {paths[name] ?? paths.book}
    </svg>
  );
}
