import { useState } from 'react';
import { native } from './api';
import { Glyph } from './Glyph';
import type { Voice } from './types';
import './voice-studio.css';

type Props = {
  voices: Voice[];
  loading: boolean;
  selected: string;
  previewing: string;
  onSelect: (id: string) => Promise<void>;
  onPreview: (id: string, text: string) => Promise<void>;
  onStop: () => Promise<void>;
  onRefresh: () => Promise<void>;
  onDownload: (id: string) => Promise<void>;
  onCancel: (id: string) => Promise<void>;
  onWindowsSettings: () => Promise<void>;
};
export function VoiceStudio(props: Props) {
  const [family, setFamily] = useState('All');
  const [query, setQuery] = useState('');
  const [sample, setSample] = useState(
    'A quiet page, a familiar voice. Take your time. The next chapter is waiting.',
  );
  const [error, setError] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const term = query.trim().toLowerCase();
  const visible = props.voices.filter(
    (voice) =>
      (family === 'All' || voice.family === family) &&
      `${voice.name} ${voice.language ?? 'en-US'} ${voice.language === 'en-GB' ? 'British English UK' : voice.language === 'en-US' ? 'American English US' : ''} ${voice.gender ?? ''} ${voice.engine}`
        .toLowerCase()
        .includes(term),
  );
  async function act(action: () => Promise<unknown>) {
    setError('');
    try {
      await action();
    } catch (error) {
      setError(String(error).replace(/^Error: /, ''));
    }
  }
  const families = ['Windows', 'Studio', 'Lite'];
  return (
    <div className="page voices-page">
      <div className="voice-page-heading">
        <div>
          <h1>
            Voice Studio<span className="heading-dot">.</span>
          </h1>
        </div>
        <button
          className="secondary small"
          disabled={!native || props.loading || refreshing}
          onClick={() =>
            void act(async () => {
              setRefreshing(true);
              try {
                await props.onRefresh();
              } finally {
                setRefreshing(false);
              }
            })
          }
        >
          {refreshing ? 'Refreshing…' : 'Refresh voices'}
        </button>
      </div>
      <div className="voice-preview-text">
        <label htmlFor="voice-sample">Try your own words</label>
        <textarea
          id="voice-sample"
          maxLength={600}
          rows={2}
          value={sample}
          onChange={(event) => setSample(event.target.value)}
        />
        <span>Preview uses the current narration speed.</span>
      </div>
      <div className="voice-browser-tools">
        <div className="tabs" aria-label="Voice source">
          {[
            'All',
            ...families.filter(
              (name) =>
                name !== 'Windows' || props.voices.some((voice) => voice.family === 'Windows'),
            ),
          ].map((name) => (
            <button
              key={name}
              aria-label={`${name} voices`}
              className={name === family ? 'active' : ''}
              aria-pressed={family === name}
              onClick={() => setFamily(name)}
            >
              {name}
              <span>
                {name === 'All'
                  ? props.voices.length
                  : props.voices.filter((voice) => voice.family === name).length}
              </span>
            </button>
          ))}
        </div>
        <label className="search">
          <Glyph name="search" />
          <input
            type="search"
            aria-label="Search voices"
            placeholder="Name, accent or language…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
      </div>
      {error && (
        <div className="voice-inline-error" role="alert">
          <span>{error}</span>
          <button
            className="icon-button"
            aria-label="Dismiss voice error"
            onClick={() => setError('')}
          >
            <Glyph name="close" />
          </button>
        </div>
      )}
      {native && !props.loading && !props.voices.some((voice) => voice.id === props.selected) && (
        <p role="status" className="voice-footnote">
          Your saved voice is not available on this computer. Choose a voice below.
        </p>
      )}
      {families
        .filter((name) => family === 'All' || family === name)
        .map((name) => {
          const group = visible.filter((voice) => voice.family === name);
          if (!group.length) return null;
          return (
            <section className="voice-source-section" key={name} aria-label={`${name} voices`}>
              <div className="voice-section-heading">
                <div>
                  <h2>{name === 'Windows' ? 'On this PC' : `${name} voices`}</h2>
                  <p>
                    {name === 'Windows'
                      ? 'Installed Microsoft voices. Ready to use without a Vern download.'
                      : name === 'Studio'
                        ? '28 English speakers share one ~126 MB Kokoro v1.0 download. American and British accents.'
                        : 'A compact Piper voice for offline listening.'}
                  </p>
                </div>
                {name === 'Windows' && (
                  <button
                    className="text-button"
                    disabled={!native}
                    onClick={() => void act(props.onWindowsSettings)}
                  >
                    Manage Windows voices <Glyph name="external" />
                  </button>
                )}
              </div>
              <div className="voice-choice-grid">
                {group.map((voice) => {
                  const installed = voice.state === 'installed';
                  const working = ['downloading', 'queued', 'validating', 'cancelling'].includes(
                    voice.state,
                  );
                  return (
                    <article
                      className={`voice-choice ${props.selected === voice.id ? 'voice-is-selected' : ''}`}
                      key={voice.id}
                    >
                      <div className="voice-choice-heading">
                        <h3>{voice.name}</h3>
                        {props.selected === voice.id && (
                          <span className="voice-selected-label">
                            <Glyph name="check" />
                            Selected
                          </span>
                        )}
                      </div>
                      <p className="voice-language">
                        {voice.language === 'en-GB'
                          ? 'English · UK'
                          : voice.language === 'en-US' || !voice.language
                            ? 'English · US'
                            : voice.language}
                        {voice.gender ? ` · ${voice.gender}` : ''}
                      </p>
                      <span className="voice-engine-label">
                        {voice.engine} ·{' '}
                        {name === 'Windows'
                          ? 'Installed on this PC'
                          : installed
                            ? 'Ready offline'
                            : voice.size}
                      </span>
                      {working && (
                        <div className="voice-download-state" role="status">
                          {voice.state === 'downloading'
                            ? `Downloading shared ${name} package${voice.total ? ` · ${Math.round((voice.downloaded / voice.total) * 100)}%` : ''}`
                            : voice.state === 'validating'
                              ? 'Validating voice package…'
                              : voice.state === 'cancelling'
                                ? 'Cancelling…'
                                : 'Queued for download'}
                          <progress
                            aria-label={`Download progress for ${voice.name}`}
                            value={voice.downloaded}
                            max={voice.total || undefined}
                          />
                        </div>
                      )}
                      {voice.error && <p className="voice-error">{voice.error}</p>}
                      <div className="voice-choice-actions">
                        {installed ? (
                          <>
                            <button
                              className="secondary small"
                              aria-label={`${props.previewing === voice.id ? 'Stop preview of' : 'Preview'} ${voice.name}`}
                              disabled={!native || !sample.trim()}
                              onClick={() =>
                                void act(() =>
                                  props.previewing === voice.id
                                    ? props.onStop()
                                    : props.onPreview(voice.id, sample),
                                )
                              }
                            >
                              <Glyph name={props.previewing === voice.id ? 'pause' : 'play'} />
                              {props.previewing === voice.id ? 'Stop' : 'Preview'}
                            </button>
                            <button
                              className={
                                props.selected === voice.id ? 'text-button' : 'primary small'
                              }
                              aria-label={`Use ${voice.name}`}
                              aria-pressed={props.selected === voice.id}
                              disabled={!native || props.selected === voice.id}
                              onClick={() => void act(() => props.onSelect(voice.id))}
                            >
                              {props.selected === voice.id ? 'Selected' : 'Use voice'}
                            </button>
                          </>
                        ) : working ? (
                          <button
                            className="secondary small"
                            disabled={voice.state === 'cancelling'}
                            onClick={() => void act(() => props.onCancel(voice.id))}
                          >
                            Cancel download
                          </button>
                        ) : name === 'Windows' ? (
                          <button
                            className="secondary small"
                            disabled={!native}
                            onClick={() => void act(props.onRefresh)}
                          >
                            Retry detection
                          </button>
                        ) : (
                          <button
                            className="secondary small"
                            disabled={!native}
                            onClick={() => void act(() => props.onDownload(voice.id))}
                          >
                            <Glyph name="download" />
                            {voice.state === 'failed' ? 'Retry download' : `Download ${name}`}
                          </button>
                        )}
                      </div>
                    </article>
                  );
                })}
              </div>
            </section>
          );
        })}
      {!visible.length && (
        <p className="voice-empty">
          {props.loading
            ? 'Loading voices…'
            : 'No voices match this search. Try a name, language code or accent.'}
        </p>
      )}
      <p className="voice-footnote">
        All listed voices synthesize on your device. Windows voices depend on the speech languages
        installed on this PC; Narrator-only and online voices may not be available to other apps.
        Studio and Lite packages retain their upstream licences. Highlighting follows passages.
      </p>
    </div>
  );
}
