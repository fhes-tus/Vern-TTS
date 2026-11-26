import { useState, useEffect } from 'react';
import { DialogSurface } from './DialogSurface';

type TextEditorDialogProps = {
  bookTitle: string;
  passageIndex: number;
  initialText: string;
  onSave: (text: string) => Promise<void>;
  onClose: () => void;
};

export function TextEditorDialog({
  bookTitle,
  passageIndex,
  initialText,
  onSave,
  onClose,
}: TextEditorDialogProps) {
  const [text, setText] = useState(initialText);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setText(initialText);
  }, [initialText]);

  async function handleSave() {
    const trimmed = text.trim();
    if (!trimmed) {
      setError('Passage text cannot be empty.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await onSave(trimmed);
      onClose();
    } catch (e) {
      setError(String(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <DialogSurface
      title="Edit extracted text"
      className="workspace-dialog text-editor-dialog"
      onClose={onClose}
      dismissible={!saving}
      dismissOnBackdrop
    >
      <div className="dialog-heading">
        <div>
          <span className="eyebrow">
            {bookTitle} · Passage {passageIndex + 1}
          </span>
          <h2 style={{ margin: '4px 0' }}>Edit extracted text</h2>
        </div>
        <button
          className="icon-button"
          type="button"
          data-dialog-dismiss
          disabled={saving}
          aria-label="Close"
        >
          ✕
        </button>
      </div>
      <p className="muted" style={{ margin: '0 0 16px 0', fontSize: '0.9rem' }}>
        Repair OCR or scanning inaccuracies in this passage. Changes update your reading immediately
        without resetting progress or losing annotations.
      </p>

      <textarea
        className="text-editor-area"
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          if (error) setError('');
        }}
        onKeyDown={(e) => {
          if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
            e.preventDefault();
            void handleSave();
          }
        }}
        disabled={saving}
        rows={8}
        style={{
          width: '100%',
          boxSizing: 'border-box',
          padding: '12px',
          fontSize: '1rem',
          lineHeight: '1.5',
          borderRadius: '8px',
          border: '1px solid var(--color-hairline, rgba(0,0,0,0.15))',
          background: 'var(--color-surface-1, #fff)',
          color: 'var(--color-ink, inherit)',
          resize: 'vertical',
          fontFamily: 'inherit',
        }}
        data-dialog-autofocus
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          margin: '8px 0 16px 0',
          fontSize: '0.85rem',
        }}
      >
        <span className="muted">{text.length} characters</span>
        <span className="muted">Press Ctrl+Enter to save</span>
      </div>

      {error && (
        <p className="error-notice" role="alert">
          {error}
        </p>
      )}

      <div
        className="dialog-actions"
        style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}
      >
        <button className="secondary" type="button" data-dialog-dismiss disabled={saving}>
          Cancel
        </button>
        <button
          className="primary"
          onClick={() => void handleSave()}
          disabled={saving || !text.trim()}
        >
          {saving ? 'Saving…' : 'Save passage'}
        </button>
      </div>
    </DialogSurface>
  );
}
