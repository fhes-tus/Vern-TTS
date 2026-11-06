import type { ReactNode } from 'react';
import { Glyph } from './Glyph';
export function Notice({
  children,
  onDismiss,
  error = false,
}: {
  children: ReactNode;
  onDismiss: () => void;
  error?: boolean;
}) {
  return (
    <div
      className={`notice-banner ${error ? 'notice-error' : ''}`}
      role={error ? 'alert' : 'status'}
    >
      <div>{children}</div>
      <button
        type="button"
        className="icon-button"
        aria-label={error ? 'Dismiss error' : 'Dismiss notification'}
        title="Dismiss"
        onClick={onDismiss}
      >
        <Glyph name="close" />
      </button>
    </div>
  );
}
