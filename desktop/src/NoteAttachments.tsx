import { useEffect, useState } from 'react';
import { api, native } from './api';
import { Glyph } from './Glyph';
import type { NoteMedia } from './types';

function attachmentUrl(data: string) {
  const match =
    /^data:(image\/jpeg|image\/png|audio\/wav|audio\/mpeg|audio\/ogg);base64,([A-Za-z0-9+/=]+)$/.exec(
      data,
    );
  if (!match || data.length > 12 * 1024 * 1024) throw new Error('Unsupported attachment preview.');
  const bytes = Uint8Array.from(atob(match[2]), (character) => character.charCodeAt(0));
  return URL.createObjectURL(new Blob([bytes], { type: match[1] }));
}
export function NoteAttachments({
  noteId,
  disabled,
  prepare,
  onBusy,
}: {
  noteId?: string;
  disabled: boolean;
  prepare: () => Promise<string>;
  onBusy: (busy: boolean) => void;
}) {
  const [opened, setOpened] = useState(false);
  const [items, setItems] = useState<NoteMedia[]>([]);
  const [error, setError] = useState('');
  const [deleting, setDeleting] = useState('');
  const [preview, setPreview] = useState<{ id: string; url: string } | null>(null);
  useEffect(
    () => () => {
      if (preview) URL.revokeObjectURL(preview.url);
    },
    [preview],
  );
  useEffect(() => {
    if (!opened || !noteId) return;
    let live = true;
    api
      .noteMedia(noteId)
      .then((value) => {
        if (live) setItems(value);
      })
      .catch((e) => {
        if (live) setError(String(e));
      });
    return () => {
      live = false;
    };
  }, [noteId, opened]);
  async function run(action: () => Promise<void>) {
    onBusy(true);
    setError('');
    try {
      await action();
    } catch (e) {
      setError(String(e).replace(/^Error: /, ''));
    } finally {
      onBusy(false);
    }
  }
  return (
    <section className="note-attachments">
      <button
        type="button"
        className="text-button"
        aria-expanded={opened}
        onClick={() => {
          setOpened(!opened);
          setPreview(null);
        }}
      >
        Images & recordings
      </button>
      {opened && (
        <>
          <p className="muted">
            Keep images and recordings with this note. JPEG, PNG, WAV, MP3 or OGG; up to 8 MB each.
          </p>
          <ul>
            {items.map((item) => (
              <li key={item.id}>
                <div className="note-media-heading">
                  <span>
                    <Glyph name={item.mime.startsWith('audio/') ? 'headphones' : 'file'} />
                    {item.name}
                    <small>{(item.size / 1024).toFixed(0)} KB</small>
                  </span>
                  <div className="action-row">
                    <button
                      type="button"
                      className="text-button"
                      disabled={disabled}
                      onClick={() => {
                        if (preview?.id === item.id) {
                          setPreview(null);
                          return;
                        }
                        void run(async () => {
                          const url = attachmentUrl(await api.noteMediaUrl(item.id));
                          setPreview({ id: item.id, url });
                        });
                      }}
                    >
                      {preview?.id === item.id ? 'Close preview' : 'Preview'}
                    </button>
                    <button
                      type="button"
                      className="icon-button"
                      aria-label={'Remove attachment ' + item.name}
                      disabled={disabled}
                      onClick={() => setDeleting(item.id)}
                    >
                      <Glyph name="trash" />
                    </button>
                  </div>
                </div>
                {preview?.id === item.id &&
                  (item.mime.startsWith('image/') ? (
                    <img
                      className="note-media-image"
                      src={preview.url}
                      alt={item.name}
                      decoding="async"
                    />
                  ) : (
                    <audio controls preload="metadata" src={preview.url} aria-label={item.name} />
                  ))}
                {deleting === item.id && (
                  <div className="note-media-confirm">
                    <p>Remove this attachment from the note?</p>
                    <button
                      type="button"
                      className="secondary small"
                      disabled={disabled}
                      onClick={() =>
                        void run(async () => {
                          await api.deleteNoteMedia(item.id);
                          setItems((old) => old.filter((value) => value.id !== item.id));
                          setDeleting('');
                          if (preview?.id === item.id) setPreview(null);
                        })
                      }
                    >
                      Remove attachment
                    </button>
                    <button
                      type="button"
                      className="text-button"
                      disabled={disabled}
                      onClick={() => setDeleting('')}
                    >
                      Keep attachment
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
          {!items.length && <p className="muted">No attachments yet.</p>}
          <button
            type="button"
            className="secondary"
            disabled={disabled || !native || items.length >= 20}
            onClick={() =>
              void run(async () => {
                const id = await prepare();
                const item = await api.attachNoteMedia(id);
                if (item) setItems(await api.noteMedia(id));
              })
            }
          >
            <Glyph name="plus" />
            Save & add attachment…
          </button>
        </>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
