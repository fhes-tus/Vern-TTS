import { useState } from 'react';
import { api, native } from './api';

export function ReferenceTools({ initialText = '' }: { initialText?: string }) {
  const [text, setText] = useState(initialText.slice(0, 3500));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  async function open(site: 'wikipedia' | 'wiktionary' | 'translate') {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      await api.openReference(site, text);
      setMessage('Sent to your browser. Review the result there.');
    } catch (failure) {
      setError(String(failure).replace(/^Error: /, ''));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="settings-card" aria-labelledby="reference-heading">
      <h2 id="reference-heading">Look up or translate</h2>
      <p className="muted" id="reference-privacy">
        Open a reference site in your browser. The text below is sent to that site only when you
        choose an action. Choose translation languages in Google Translate.
      </p>
      <div className="workspace-form">
        <label htmlFor="reference-text">
          Text to look up or translate
          <textarea
            id="reference-text"
            rows={3}
            maxLength={3500}
            value={text}
            aria-describedby="reference-privacy"
            onChange={(event) => {
              setText(event.target.value);
              setMessage('');
              setError('');
            }}
          />
        </label>
        {!native && <p className="muted">Open the desktop app to use these browser handoffs.</p>}
        <div className="action-row" aria-busy={busy}>
          {(
            [
              ['wikipedia', 'Open Wikipedia'],
              ['wiktionary', 'Open Wiktionary'],
              ['translate', 'Open Google Translate'],
            ] as const
          ).map(([site, label]) => (
            <button
              key={site}
              type="button"
              className="secondary"
              disabled={!native || busy || !text.trim()}
              onClick={() => void open(site)}
            >
              {label}
            </button>
          ))}
        </div>
        {error && <p role="alert">{error}</p>}
        {message && <p role="status">{message}</p>}
      </div>
    </section>
  );
}
