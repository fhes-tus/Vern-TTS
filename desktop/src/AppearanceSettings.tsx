import { themeFamilies, extraPalettes, paletteData } from './themes';
import { Glyph } from './Glyph';
import type { Settings } from './types';
import { useEffect, useState } from 'react';
import { readerFonts, readerPaperStyle } from './reader-appearance';

export function AppearanceSettings({
  settings,
  onSave,
  readerOnly = false,
}: {
  settings: Settings;
  onSave: (patch: Partial<Settings>) => Promise<boolean>;
  readerOnly?: boolean;
}) {
  const [systemDark, setSystemDark] = useState(
    () => window.matchMedia('(prefers-color-scheme: dark)').matches,
  );
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const changed = () => setSystemDark(media.matches);
    media.addEventListener?.('change', changed);
    return () => media.removeEventListener?.('change', changed);
  }, []);
  const fixed =
    settings.colorTheme === 'neon' || extraPalettes.some(([id]) => id === settings.colorTheme);
  const mode = fixed ? (paletteData[settings.colorTheme].dark ? 'dark' : 'light') : settings.theme;
  return (
    <section
      className="settings-card appearance-settings"
      aria-label="Appearance and reading settings"
    >
      <div className="section-heading">
        <Glyph name="palette" />
        <div>
          <h2>{readerOnly ? 'Reader appearance' : 'Display & theme'}</h2>
          <p>Colour, comfort and a little personality.</p>
        </div>
      </div>
      {!readerOnly && (
        <>
          <fieldset className="appearance-mode">
            <legend>Appearance</legend>
            {(['light', 'dark', 'system'] as const).map((mode) => (
              <button
                key={mode}
                type="button"
                aria-pressed={
                  (fixed
                    ? paletteData[settings.colorTheme].dark
                      ? 'dark'
                      : 'light'
                    : settings.theme) === mode
                }
                disabled={
                  fixed && mode !== (paletteData[settings.colorTheme].dark ? 'dark' : 'light')
                }
                onClick={() => void onSave({ theme: mode })}
              >
                <Glyph name={mode === 'light' ? 'sun' : mode === 'dark' ? 'moon' : 'window'} />
                {mode[0].toUpperCase() + mode.slice(1)}
              </button>
            ))}
          </fieldset>
          <fieldset className="theme-picker">
            <legend>Theme colour</legend>
            <div className="theme-grid">
              {themeFamilies.map((family) => {
                const p =
                  paletteData[
                    settings.theme === 'dark' || (settings.theme === 'system' && systemDark)
                      ? family.dark
                      : family.light
                  ];
                return (
                  <button
                    key={family.id}
                    type="button"
                    className="theme-choice"
                    aria-pressed={settings.colorTheme === family.id}
                    onClick={() => void onSave({ colorTheme: family.id, accentColor: '' })}
                  >
                    <span className="palette-preview" style={{ background: p.background }}>
                      <i style={{ background: p.primary }} />
                      <i style={{ background: p.primaryContainer }} />
                      <i style={{ background: p.tertiary }} />
                    </span>
                    <span>{family.name}</span>
                    {settings.colorTheme === family.id && <Glyph name="check" />}
                  </button>
                );
              })}
            </div>
          </fieldset>
          {fixed && (
            <p className="muted">
              This preset uses a {mode} canvas. Choose another colour family for light/dark/system
              options.
            </p>
          )}
          <div className="settings-pair">
            <label>
              More palettes
              <select
                aria-label="More palettes"
                value={
                  extraPalettes.some(([id]) => id === settings.colorTheme)
                    ? settings.colorTheme
                    : ''
                }
                onChange={(e) => {
                  if (e.target.value) void onSave({ colorTheme: e.target.value, accentColor: '' });
                }}
              >
                <option value="">Choose a preset…</option>
                {extraPalettes.map(([id, name]) => (
                  <option key={id} value={id}>
                    {name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Custom accent
              <span className="accent-control">
                <input
                  aria-label="Custom accent colour"
                  type="color"
                  value={settings.accentColor || '#c07318'}
                  onChange={(e) => void onSave({ accentColor: e.target.value })}
                />
                <span>{settings.accentColor || 'From palette'}</span>
                <button
                  type="button"
                  className="text-button"
                  onClick={() => void onSave({ accentColor: '' })}
                >
                  Reset
                </button>
              </span>
            </label>
          </div>
        </>
      )}
      <div className="settings-pair">
        <label>
          Reader typeface
          <select
            aria-label="Reader typeface"
            value={settings.readerFont}
            onChange={(e) => void onSave({ readerFont: e.target.value })}
          >
            {readerFonts.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Text size <span>{settings.fontSize} px</span>
          <input
            aria-label="Text size"
            type="range"
            min="14"
            max="32"
            value={settings.fontSize}
            onChange={(e) => void onSave({ fontSize: Number(e.target.value) })}
          />
        </label>
      </div>
      <div className="settings-pair">
        <label>
          Line spacing
          <select
            aria-label="Reader line spacing"
            value={settings.lineSpacing}
            onChange={(event) => void onSave({ lineSpacing: Number(event.target.value) })}
          >
            {[1.2, 1.4, 1.6, 1.85, 2, 2.2, 2.4].map((value) => (
              <option key={value} value={value}>
                {value}×
              </option>
            ))}
          </select>
        </label>
        <label>
          Paper tone
          <select
            value={settings.paperTone}
            onChange={(event) =>
              void onSave({ paperTone: event.target.value as Settings['paperTone'] })
            }
          >
            <option value="theme">Active theme</option>
            <option value="white">Natural white</option>
            <option value="sepia">Warm sepia</option>
            <option value="dark">Dark paper</option>
          </select>
        </label>
      </div>
      <div className="settings-pair">
        <label>
          Page layout
          <select
            aria-label="Default page layout"
            value={settings.readerLayout}
            onChange={(e) =>
              void onSave({ readerLayout: e.target.value as Settings['readerLayout'] })
            }
          >
            <option value="single">Single page</option>
            <option value="double">Two pages</option>
            <option value="scroll">Scroll</option>
          </select>
        </label>
        <label>
          Reader view
          <select
            aria-label="Default reader view"
            value={settings.readerView}
            onChange={(e) => void onSave({ readerView: e.target.value as Settings['readerView'] })}
          >
            <option value="flow">Reading</option>
            <option value="original">Original document</option>
          </select>
        </label>
      </div>
      <label className="toggle-setting">
        <input
          type="checkbox"
          checked={settings.reduceMotion}
          onChange={(e) => void onSave({ reduceMotion: e.target.checked })}
        />
        <span>
          Reduce motion
          <small>
            Keep page turns and app transitions still. Your system preference is respected too.
          </small>
        </span>
      </label>
      <div
        className="type-preview"
        style={{ ...readerPaperStyle(settings), lineHeight: settings.lineSpacing }}
      >
        There is no friend as loyal as a book.
      </div>
      <p className="field-hint">
        Typeface, text size, spacing and paper tone apply to Reading view. Original document view
        keeps the document’s typography and uses zoom.
      </p>
    </section>
  );
}
