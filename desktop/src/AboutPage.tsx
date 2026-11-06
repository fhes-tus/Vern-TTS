import { useEffect, useState } from 'react';
import { call, native } from './api';
import { Glyph } from './Glyph';
import { Notice } from './Notice';
import metadata from '../package.json';
const fontLicenses = [
  ['Outfit', 'OFL-outfit.txt'],
  ['Bitter', 'OFL-bitter.txt'],
  ['Atkinson Hyperlegible', 'OFL-atkinsonhyperlegible.txt'],
];
export function AboutPage() {
  const [error, setError] = useState('');
  const [notices, setNotices] = useState<Record<string, string>>({});
  useEffect(() => {
    let active = true;
    void Promise.all(
      fontLicenses.map(async ([name, file]) => {
        const response = await fetch(`/licenses/${file}`);
        if (!response.ok) throw new Error('The bundled font notices could not be loaded.');
        return [name, await response.text()];
      }),
    )
      .then((entries) => {
        if (active) setNotices(Object.fromEntries(entries));
      })
      .catch((error) => {
        if (active) setError(String(error));
      });
    return () => {
      active = false;
    };
  }, []);
  const open = (id: string) =>
    void call('open_app_link', { id }).catch((error) => setError(String(error)));
  return (
    <div className="about-page">
      <section className="about-hero">
        <div className="about-brand">
          <img src="/vern.png" alt="Vern" />
          <div>
            <h2>Vern</h2>
          </div>
        </div>
        <p>Offline reading and speech.</p>
        <span className="about-version">Desktop preview · Version {metadata.version}</span>
        <svg className="about-spines" viewBox="0 0 140 280" aria-hidden="true">
          <rect x="8" y="10" width="30" height="250" rx="12" />
          <rect x="48" y="-10" width="30" height="270" rx="12" />
          <rect x="88" y="30" width="30" height="230" rx="12" />
        </svg>
      </section>
      <section className="about-community">
        <h3>Made better together</h3>
        {[
          ['contact', 'Contact the developer', 'A question, an idea, or just hello.', 'edit'],
          ['support', 'Support Vern', 'Help this little reading space grow.', 'heart'],
          ['issues', 'Report an issue', 'Tell us what could work better.', 'info'],
        ].map(([id, title, detail, icon]) => (
          <button key={id} disabled={!native} onClick={() => open(id)}>
            <Glyph name={icon} />
            <span>
              <strong>{title}</strong>
              <small>{detail}</small>
            </span>
            <Glyph name="external" />
          </button>
        ))}
      </section>
      <section className="about-online">
        <h3>Find Vern online</h3>
        <div>
          {[
            ['website', 'Website'],
            ['github', 'GitHub'],
            ['telegram', 'Telegram'],
          ].map(([id, label]) => (
            <button className="secondary" key={id} disabled={!native} onClick={() => open(id)}>
              {label}
              <Glyph name="external" />
            </button>
          ))}
        </div>
      </section>
      <details className="about-licenses">
        <summary>Open source licenses</summary>
        <p>
          Vern uses React, Tauri, SQLite, PDF.js, sherpa-onnx, Piper and Kokoro. The application
          retains bundled dependency and voice-package notices. Outfit, Bitter and Atkinson
          Hyperlegible are licensed under the SIL Open Font License 1.1.
        </p>
        {fontLicenses.map(([name]) => (
          <details key={name}>
            <summary>{name}</summary>
            <pre>{notices[name] ?? 'Loading bundled font notice…'}</pre>
          </details>
        ))}
      </details>
      {error && (
        <Notice error onDismiss={() => setError('')}>
          {error}
        </Notice>
      )}
      <p className="about-privacy">
        Your library stays on your device. Online features are optional.
      </p>
      <p className="field-hint">
        Desktop parity work is ongoing. Automatic updates are not enabled in this preview.
      </p>
    </div>
  );
}
