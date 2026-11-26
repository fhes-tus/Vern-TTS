import { useState, useEffect } from 'react';
import { DialogSurface } from './DialogSurface';
import { api, native } from './api';
import { Glyph } from './Glyph';
import type { CloudVoiceConfig, CloudVoiceProvider, CloudVoiceStatus, CatalogVoice } from './types';
import './cloud-voice.css';

const PROVIDER_METADATA: Record<
  CloudVoiceProvider,
  {
    title: string;
    description: string;
    defaultModel: string;
    defaultVoice: string;
    defaultLanguage: string;
    defaultEndpoint: string;
    docs: string;
    hasCatalog: boolean;
  }
> = {
  ELEVENLABS: {
    title: 'ElevenLabs',
    description: 'High-fidelity generative neural voices with speech-to-dialogue support.',
    defaultModel: 'eleven_v4',
    defaultVoice: 'JBFqnCBsd6RMkjVDRZzb',
    defaultLanguage: '',
    defaultEndpoint: 'https://api.elevenlabs.io/v1/text-to-speech',
    docs: 'https://elevenlabs.io/docs/overview/models',
    hasCatalog: false,
  },
  CARTESIA: {
    title: 'Cartesia',
    description: 'Ultra-low latency streaming Sonic 3.6 speech synthesis.',
    defaultModel: 'sonic-3.6',
    defaultVoice: 'db6b0ed5-d5d3-463d-ae85-518a07d3c2b4',
    defaultLanguage: 'en',
    defaultEndpoint: 'https://api.cartesia.ai/tts/bytes',
    docs: 'https://docs.cartesia.ai/api-reference/tts/bytes',
    hasCatalog: false,
  },
  MINIMAX: {
    title: 'MiniMax',
    description: 'Expressive speech-2.8-hd voice models with hex-encoded streaming audio.',
    defaultModel: 'speech-2.8-hd',
    defaultVoice: 'English_expressive_narrator',
    defaultLanguage: '',
    defaultEndpoint: 'https://api.minimax.io/v1/t2a_v2',
    docs: 'https://platform.minimax.io/docs/api-reference/speech-t2a-http',
    hasCatalog: false,
  },
  KITTEN: {
    title: 'KittenML',
    description: 'OpenAI-compatible lightweight neural voice endpoint.',
    defaultModel: 'kitten-tts-2-latest',
    defaultVoice: '07 — Saoirse — Joyful Irish Female',
    defaultLanguage: '',
    defaultEndpoint: 'https://api.kittenml.com/v1/audio/speech',
    docs: 'https://kittenml.com/',
    hasCatalog: false,
  },
  AZURE: {
    title: 'Microsoft Azure Speech',
    description: '400+ neural voices across 140+ languages with regional cognitive endpoints.',
    defaultModel: 'neural',
    defaultVoice: 'en-US-JennyNeural',
    defaultLanguage: 'en-US',
    defaultEndpoint: '',
    docs: 'https://learn.microsoft.com/azure/ai-services/speech-service/get-started-text-to-speech',
    hasCatalog: true,
  },
  GOOGLE_CLOUD: {
    title: 'Google Cloud TTS',
    description: 'Studio and Neural2 multilingual synthesis powered by DeepMind technology.',
    defaultModel: 'v1',
    defaultVoice: 'en-US-Neural2-F',
    defaultLanguage: 'en-US',
    defaultEndpoint: 'https://texttospeech.googleapis.com/v1/text:synthesize',
    docs: 'https://cloud.google.com/text-to-speech/docs/get-started',
    hasCatalog: true,
  },
  CUSTOM: {
    title: 'Custom OpenAI-Compatible',
    description:
      'Connect any self-hosted or third-party OpenAI-compatible /v1/audio/speech endpoint.',
    defaultModel: 'tts-1',
    defaultVoice: 'alloy',
    defaultLanguage: '',
    defaultEndpoint: 'http://localhost:8000/v1/audio/speech',
    docs: 'https://github.com/debpalash/VoiceStudio',
    hasCatalog: false,
  },
};

type Props = {
  sampleText: string;
  onSelectVoice: (id: string) => Promise<void>;
  selectedVoice: string;
};

export function CloudVoiceSettings(props: Props) {
  const [status, setStatus] = useState<CloudVoiceStatus | null>(null);
  const [draft, setDraft] = useState<CloudVoiceConfig | null>(null);
  const [apiKey, setApiKey] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // Catalog modal state
  const [showCatalog, setShowCatalog] = useState(false);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [catalogVoices, setCatalogVoices] = useState<CatalogVoice[]>([]);
  const [catalogSearch, setCatalogSearch] = useState('');

  useEffect(() => {
    loadStatus();
  }, []);

  async function loadStatus() {
    setLoading(true);
    setError(null);
    try {
      const data = await api.cloudVoiceStatus();
      setStatus(data);
      setDraft(data.config);
    } catch (err) {
      setError(String(err).replace(/^Error: /, ''));
    } finally {
      setLoading(false);
    }
  }

  function handleProviderChange(provider: CloudVoiceProvider) {
    if (!draft) return;
    const meta = PROVIDER_METADATA[provider];
    setDraft({
      ...draft,
      provider,
      endpoint: meta.defaultEndpoint,
      model: meta.defaultModel,
      voice: meta.defaultVoice,
      language: meta.defaultLanguage,
      region: provider === 'AZURE' ? 'eastus' : '',
    });
    setApiKey('');
    setNotice(null);
    setError(null);
  }

  async function handleSave(newConfig?: CloudVoiceConfig) {
    const toSave = newConfig ?? draft;
    if (!toSave) return;
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await api.saveCloudVoiceConfig(toSave, apiKey.trim() || null);
      setStatus(updated);
      setDraft(updated.config);
      setApiKey('');
      setNotice('Cloud voice configuration saved successfully.');
      if (updated.config.enabled && props.selectedVoice !== 'cloud') {
        await props.onSelectVoice('cloud');
      }
    } catch (err) {
      setError(String(err).replace(/^Error: /, ''));
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleEnabled() {
    if (!draft) return;
    const willEnable = !draft.enabled;
    const next = { ...draft, enabled: willEnable };
    setDraft(next);
    await handleSave(next);
  }

  async function handleTestVoice() {
    if (!draft) return;
    setTesting(true);
    setError(null);
    setNotice(null);
    try {
      // First save if key entered
      if (apiKey.trim()) {
        await handleSave();
      }
      const text = props.sampleText.trim() || 'This is a test of your cloud voice configuration.';
      const audio = await api.testCloudVoice(text);
      // Play audio via Web Audio
      const audioCtx = new AudioContext();
      await audioCtx.resume();
      const buffer = audioCtx.createBuffer(1, audio.samples.length, audio.sampleRate);
      buffer.copyToChannel(Float32Array.from(audio.samples), 0);
      const source = audioCtx.createBufferSource();
      source.buffer = buffer;
      source.connect(audioCtx.destination);
      source.start();
      setNotice('Synthesis succeeded! Playing audio test preview.');
      // Refresh usage counter
      const updatedStatus = await api.cloudVoiceStatus();
      setStatus(updatedStatus);
    } catch (err) {
      setError(String(err).replace(/^Error: /, ''));
    } finally {
      setTesting(false);
    }
  }

  async function handleOpenCatalog() {
    if (!draft) return;
    setShowCatalog(true);
    setCatalogLoading(true);
    setCatalogVoices([]);
    setError(null);
    try {
      if (apiKey.trim()) {
        await handleSave();
      }
      const voices = await api.listCloudCatalogVoices();
      setCatalogVoices(voices);
    } catch (err) {
      setError(String(err).replace(/^Error: /, ''));
      setShowCatalog(false);
    } finally {
      setCatalogLoading(false);
    }
  }

  async function handleResetUsage() {
    if (!draft) return;
    if (!confirm(`Reset recorded cloud voice usage statistics for ${draft.provider}?`)) return;
    try {
      await api.clearCloudVoiceUsage(draft.provider);
      await loadStatus();
      setNotice('Usage statistics cleared.');
    } catch (err) {
      setError(String(err).replace(/^Error: /, ''));
    }
  }

  if (loading || !draft) {
    return (
      <div className="cloud-voice-loading">
        <p>Loading cloud voice settings…</p>
      </div>
    );
  }

  const currentMeta = PROVIDER_METADATA[draft.provider];
  const isCloudSelected = props.selectedVoice === 'cloud';
  const estimatedCost =
    status && draft.pricePerMillion > 0
      ? ((status.usage.characters / 1_000_000) * draft.pricePerMillion).toFixed(3)
      : '0.000';

  const filteredCatalog = catalogVoices.filter(
    (v) =>
      v.label.toLowerCase().includes(catalogSearch.toLowerCase()) ||
      v.id.toLowerCase().includes(catalogSearch.toLowerCase()) ||
      v.language.toLowerCase().includes(catalogSearch.toLowerCase()),
  );

  return (
    <div className="cloud-voice-container">
      {/* Header Banner */}
      <div className="cloud-voice-header-banner">
        <div className="cloud-voice-header-info">
          <h2>Personal Cloud Voice Streaming</h2>
          <p>
            Stream high-fidelity narration directly from personal cloud AI speech APIs (ElevenLabs,
            Cartesia, MiniMax, KittenML, Azure, Google Cloud, or custom OpenAI servers).
          </p>
        </div>
        <div className="cloud-voice-header-actions">
          <button
            className={`cloud-voice-enable-btn ${draft.enabled ? 'is-enabled' : ''}`}
            onClick={handleToggleEnabled}
            disabled={saving || !native}
          >
            <Glyph name={draft.enabled ? 'check' : 'sound'} />
            {draft.enabled ? 'Cloud Voice Active' : 'Enable Cloud Voice'}
          </button>
        </div>
      </div>

      {/* Alerts */}
      {error && (
        <div className="cloud-alert cloud-alert-error" role="alert">
          <span>{error}</span>
          <button className="icon-button" onClick={() => setError(null)}>
            <Glyph name="close" />
          </button>
        </div>
      )}
      {notice && (
        <div className="cloud-alert cloud-alert-success" role="status">
          <span>{notice}</span>
          <button className="icon-button" onClick={() => setNotice(null)}>
            <Glyph name="close" />
          </button>
        </div>
      )}

      {/* Provider Selector Cards */}
      <section className="cloud-section">
        <h3 className="cloud-section-title">Select Speech Provider</h3>
        <div className="cloud-provider-chips">
          {(Object.keys(PROVIDER_METADATA) as CloudVoiceProvider[]).map((p) => {
            const meta = PROVIDER_METADATA[p];
            const isCurrent = draft.provider === p;
            return (
              <button
                key={p}
                className={`cloud-provider-chip ${isCurrent ? 'selected' : ''}`}
                onClick={() => handleProviderChange(p)}
              >
                <span className="cloud-provider-chip-name">{meta.title}</span>
                <span className="cloud-provider-chip-model">{meta.defaultModel}</span>
              </button>
            );
          })}
        </div>
      </section>

      {/* Main Configuration Form */}
      <div className="cloud-config-grid">
        {/* Left Column: Credentials & Settings */}
        <div className="cloud-config-card">
          <div className="cloud-card-header">
            <h4>{currentMeta.title} Configuration</h4>
            <a href={currentMeta.docs} target="_blank" rel="noreferrer" className="cloud-docs-link">
              Docs <Glyph name="external" />
            </a>
          </div>

          <p className="cloud-provider-desc">{currentMeta.description}</p>

          {/* API Key */}
          <div className="cloud-form-field">
            <label htmlFor="cloud-api-key">
              API Key{' '}
              {status?.hasKey && (
                <span className="cloud-key-saved-badge">
                  <Glyph name="check" /> Configured in Rust SQLite
                </span>
              )}
            </label>
            <div className="cloud-key-input-row">
              <input
                id="cloud-api-key"
                type={showKey ? 'text' : 'password'}
                placeholder={
                  status?.hasKey
                    ? '•••••••••••••••••••••••• (leave blank to keep existing key)'
                    : `Enter your ${currentMeta.title} API key`
                }
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
              />
              <button
                type="button"
                className="secondary small"
                onClick={() => setShowKey(!showKey)}
              >
                {showKey ? 'Hide' : 'Show'}
              </button>
            </div>
            <span className="cloud-field-hint">
              Keys are stored locally in the desktop SQLite database. They are never sent to
              third-party telemetry.
            </span>
          </div>

          {/* Model & Voice */}
          <div className="cloud-form-row">
            <div className="cloud-form-field">
              <label htmlFor="cloud-model">Model ID</label>
              <input
                id="cloud-model"
                type="text"
                value={draft.model}
                onChange={(e) => setDraft({ ...draft, model: e.target.value })}
              />
            </div>
            <div className="cloud-form-field">
              <label htmlFor="cloud-voice-id">Voice ID / Speaker</label>
              <input
                id="cloud-voice-id"
                type="text"
                value={draft.voice}
                onChange={(e) => setDraft({ ...draft, voice: e.target.value })}
              />
            </div>
          </div>

          {/* Azure Region / Language / Catalog button */}
          {draft.provider === 'AZURE' && (
            <div className="cloud-form-row">
              <div className="cloud-form-field">
                <label htmlFor="cloud-region">Azure Region</label>
                <input
                  id="cloud-region"
                  type="text"
                  placeholder="e.g. eastus, westeurope"
                  value={draft.region}
                  onChange={(e) => setDraft({ ...draft, region: e.target.value })}
                />
              </div>
              <div className="cloud-form-field">
                <label htmlFor="cloud-language">Language Code</label>
                <input
                  id="cloud-language"
                  type="text"
                  placeholder="e.g. en-US"
                  value={draft.language}
                  onChange={(e) => setDraft({ ...draft, language: e.target.value })}
                />
              </div>
            </div>
          )}

          {currentMeta.hasCatalog && (
            <div className="cloud-catalog-btn-row">
              <button
                type="button"
                className="secondary small"
                onClick={handleOpenCatalog}
                disabled={catalogLoading}
              >
                <Glyph name="search" /> Browse {currentMeta.title} Voice Catalog
              </button>
            </div>
          )}

          {/* Custom Endpoint */}
          {draft.provider === 'CUSTOM' && (
            <div className="cloud-form-field">
              <label htmlFor="cloud-endpoint">Custom Speech Endpoint</label>
              <input
                id="cloud-endpoint"
                type="url"
                placeholder="https://your-api.com/v1/audio/speech"
                value={draft.endpoint}
                onChange={(e) => setDraft({ ...draft, endpoint: e.target.value })}
              />
            </div>
          )}

          {/* Actions */}
          <div className="cloud-card-actions">
            <button className="primary" onClick={() => handleSave()} disabled={saving || !native}>
              {saving ? 'Saving…' : 'Save Configuration'}
            </button>
            <button
              className="secondary"
              onClick={handleTestVoice}
              disabled={testing || saving || !native}
            >
              <Glyph name="sound" /> {testing ? 'Testing…' : 'Test Voice Audio'}
            </button>
            {draft.enabled && !isCloudSelected && (
              <button className="secondary" onClick={() => props.onSelectVoice('cloud')}>
                Set as Active Reader Voice
              </button>
            )}
          </div>
        </div>

        {/* Right Column: Limits, Pricing & Usage */}
        <div className="cloud-config-card">
          <div className="cloud-card-header">
            <h4>Usage & Connection Limits</h4>
          </div>

          <div className="cloud-metrics-box">
            <div className="cloud-metric">
              <span className="cloud-metric-label">Characters Submitted</span>
              <span className="cloud-metric-value">
                {status?.usage.characters.toLocaleString() ?? '0'}
              </span>
            </div>
            <div className="cloud-metric">
              <span className="cloud-metric-label">Requests Sent</span>
              <span className="cloud-metric-value">
                {status?.usage.requests.toLocaleString() ?? '0'}
              </span>
            </div>
            {draft.pricePerMillion > 0 && (
              <div className="cloud-metric">
                <span className="cloud-metric-label">Estimated Cost</span>
                <span className="cloud-metric-value">${estimatedCost}</span>
              </div>
            )}
          </div>

          <button
            type="button"
            className="text-button small cloud-reset-usage-btn"
            onClick={handleResetUsage}
          >
            Reset usage statistics
          </button>

          <hr className="cloud-divider" />

          {/* Advanced Limits */}
          <div className="cloud-form-field">
            <label htmlFor="cloud-timeout">Connection Timeout: {draft.timeoutSeconds}s</label>
            <input
              id="cloud-timeout"
              type="range"
              min={5}
              max={120}
              step={5}
              value={draft.timeoutSeconds}
              onChange={(e) => setDraft({ ...draft, timeoutSeconds: Number(e.target.value) })}
            />
          </div>

          <div className="cloud-form-row">
            <div className="cloud-form-field">
              <label htmlFor="cloud-retries">Retries on 429</label>
              <select
                id="cloud-retries"
                value={draft.retries}
                onChange={(e) => setDraft({ ...draft, retries: Number(e.target.value) })}
              >
                <option value={0}>0 (None)</option>
                <option value={1}>1 Retry</option>
                <option value={2}>2 Retries</option>
              </select>
            </div>
            <div className="cloud-form-field">
              <label htmlFor="cloud-price">Price / 1M chars ($)</label>
              <input
                id="cloud-price"
                type="number"
                min={0}
                step={0.5}
                placeholder="0.0"
                value={draft.pricePerMillion || ''}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    pricePerMillion: Math.max(0, parseFloat(e.target.value) || 0),
                  })
                }
              />
            </div>
          </div>
        </div>
      </div>

      {/* Catalog Voice Picker Modal */}
      {showCatalog && (
        <DialogSurface
          title="Select Cloud Voice"
          className="cloud-modal-dialog"
          onClose={() => setShowCatalog(false)}
          dismissOnBackdrop
        >
          <div className="cloud-modal-header">
            <h3>{currentMeta.title} Voice Catalog</h3>
            <button
              type="button"
              className="icon-button"
              aria-label="Close voice catalog"
              data-dialog-dismiss
            >
              <Glyph name="close" />
            </button>
          </div>
          <div className="cloud-modal-search">
            <label className="search">
              <Glyph name="search" />
              <input
                type="search"
                placeholder="Filter by name or language (e.g. en-US, de-DE)..."
                value={catalogSearch}
                onChange={(e) => setCatalogSearch(e.target.value)}
                data-dialog-autofocus
              />
            </label>
          </div>
          <div className="cloud-modal-list">
            {catalogLoading ? (
              <p className="cloud-modal-empty">Discovering voices from cloud catalog…</p>
            ) : filteredCatalog.length === 0 ? (
              <p className="cloud-modal-empty">No voices match your filter.</p>
            ) : (
              filteredCatalog.map((v) => (
                <button
                  key={`${v.id}-${v.language}`}
                  type="button"
                  data-dialog-dismiss
                  className={`cloud-catalog-item ${draft.voice === v.id ? 'selected' : ''}`}
                  onClick={() => {
                    setDraft({ ...draft, voice: v.id, language: v.language });
                    setNotice(`Selected voice ${v.label} (${v.language}).`);
                  }}
                >
                  <div>
                    <span className="cloud-catalog-name">{v.label}</span>
                    <span className="cloud-catalog-id">{v.id}</span>
                  </div>
                  <span className="cloud-catalog-lang">{v.language}</span>
                </button>
              ))
            )}
          </div>
        </DialogSurface>
      )}
    </div>
  );
}
