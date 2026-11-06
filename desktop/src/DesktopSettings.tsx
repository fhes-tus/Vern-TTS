import type { ReactNode } from 'react';
import { Glyph } from './Glyph';
import type { Page } from './navigation';
import type { NotesSettings, Settings } from './types';

const settingsDestinations = [
  ['settings', 'Display & theme', 'palette'],
  ['settings-reader', 'Reader appearance', 'book'],
  ['settings-notes', 'Notes settings', 'note'],
  ['settings-accessibility', 'Accessibility', 'eye'],
  ['settings-pronunciation', 'Pronunciation rules', 'headphones'],
  ['settings-ai', 'AI & study mode', 'sparkles'],
  ['settings-pdf', 'PDF import tools', 'file'],
  ['settings-transfer', 'Sync & backup center', 'download'],
  ['voices', 'Voice Studio', 'headphones'],
  ['queue', 'Queue & sleep timer', 'queue'],
  ['settings-about', 'About', 'info'],
] as const;
const settingsGroups = [
  ['Reading space', ['settings', 'settings-reader', 'settings-notes', 'settings-accessibility']],
  ['Voice & study', ['voices', 'settings-pronunciation', 'queue', 'settings-ai']],
  ['Library & app', ['settings-pdf', 'settings-transfer', 'settings-about']],
] as const;
export function SettingsLayout({
  page,
  onNavigate,
  children,
}: {
  page: Page;
  onNavigate: (page: Page) => void;
  children: ReactNode;
}) {
  return (
    <div className="page desktop-settings">
      <h1 tabIndex={-1}>Settings</h1>
      <label className="settings-category-picker">
        Settings category
        <select
          aria-label="Settings category"
          value={page}
          onChange={(event) => onNavigate(event.target.value as Page)}
        >
          {settingsGroups.map(([label, ids]) => (
            <optgroup label={label} key={label}>
              {ids.map((id) => (
                <option value={id} key={id}>
                  {settingsDestinations.find(([page]) => page === id)![1]}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
      </label>
      <div className="settings-layout">
        <nav className="settings-navigation" aria-label="Settings categories">
          {settingsGroups.map(([label, ids]) => (
            <div className="settings-nav-group" key={label}>
              <h2>{label}</h2>
              {ids.map((id) => {
                const [, name, icon] = settingsDestinations.find(([page]) => page === id)!;
                return (
                  <button
                    key={id}
                    aria-current={page === id ? 'page' : undefined}
                    onClick={() => onNavigate(id)}
                  >
                    <Glyph name={icon} />
                    <span>{name}</span>
                    <Glyph name="arrow" />
                  </button>
                );
              })}
            </div>
          ))}
        </nav>
        <div className="settings-content">{children}</div>
      </div>
    </div>
  );
}
type Props = { settings: Settings; onSave: (patch: Partial<Settings>) => Promise<boolean> };
export function SettingToggle({
  label,
  detail,
  checked,
  onChange,
}: {
  label: string;
  detail?: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className="settings-switch-row">
      <span>
        <strong>{label}</strong>
        {detail && <small>{detail}</small>}
      </span>
      <input
        type="checkbox"
        role="switch"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
    </label>
  );
}
export function NotesPreferences({ settings, onSave }: Props) {
  const notes = settings.notes;
  const save = (patch: Partial<NotesSettings>) => void onSave({ notes: { ...notes, ...patch } });
  return (
    <section className="settings-card">
      <h2>Notes settings</h2>
      <p className="muted">Defaults for new notes and your writing space.</p>
      <div className="settings-pair">
        <label>
          Paper template
          <select
            value={notes.paper}
            onChange={(e) => save({ paper: e.target.value as NotesSettings['paper'] })}
          >
            {['blank', 'ruled', 'grid', 'dots'].map((value) => (
              <option key={value}>{value}</option>
            ))}
          </select>
        </label>
        <label>
          Editor text size <output>{Math.round(notes.fontScale * 100)}%</output>
          <input
            type="range"
            min="0.85"
            max="1.5"
            step="0.05"
            value={notes.fontScale}
            onChange={(e) => save({ fontScale: Number(e.target.value) })}
          />
        </label>
      </div>
      <div className="settings-pair">
        <label>
          Editor line spacing
          <select
            value={notes.lineSpacing}
            onChange={(e) => save({ lineSpacing: Number(e.target.value) })}
          >
            {[1, 1.25, 1.5, 1.75, 2].map((n) => (
              <option key={n} value={n}>
                {n}×
              </option>
            ))}
          </select>
        </label>
        <label>
          Preview lines
          <input
            type="number"
            min="0"
            max="12"
            value={notes.previewLines}
            onChange={(e) => {
              if (e.target.validity.valid) save({ previewLines: Number(e.target.value) });
            }}
          />
        </label>
      </div>
      <div className="settings-pair">
        <label>
          Default note layout
          <select
            value={notes.grid ? 'grid' : 'list'}
            onChange={(e) => save({ grid: e.target.value === 'grid' })}
          >
            <option value="grid">Grid</option>
            <option value="list">List</option>
          </select>
        </label>
        <label>
          Default note sorting
          <select
            value={notes.sort}
            onChange={(e) => save({ sort: e.target.value as NotesSettings['sort'] })}
          >
            <option value="updated">Last edited</option>
            <option value="created">Date created</option>
            <option value="title">Title</option>
          </select>
        </label>
      </div>
      <SettingToggle
        label="New notes are checklists"
        checked={notes.newChecklist}
        onChange={(newChecklist) => save({ newChecklist })}
      />
      <SettingToggle
        label="Move checked items to the bottom"
        checked={notes.checkedToBottom}
        onChange={(checkedToBottom) => save({ checkedToBottom })}
      />
    </section>
  );
}
export function AccessibilityPreferences({ settings, onSave }: Props) {
  return (
    <section className="settings-card">
      <h2>Accessibility</h2>
      <p className="muted">Adjust focus, contrast and movement to suit your reading.</p>
      <h3 className="settings-subheading">Motion & clarity</h3>
      <SettingToggle
        label="Reduce motion"
        detail="Keep page turns and transitions still. The system motion preference is also respected."
        checked={settings.reduceMotion}
        onChange={(reduceMotion) => void onSave({ reduceMotion })}
      />
      <SettingToggle
        label="Reduce transparency"
        detail="Use solid panels and controls."
        checked={settings.reduceTransparency}
        onChange={(reduceTransparency) => void onSave({ reduceTransparency })}
      />
      <h3 className="settings-subheading">Reading & focus</h3>
      <SettingToggle
        label="Bionic reading"
        detail="Emphasise the opening letters of words in Reading view."
        checked={settings.bionicReading}
        onChange={(bionicReading) => void onSave({ bionicReading })}
      />
      <SettingToggle
        label="Section numbers"
        detail="Show the passage number beside extracted reading text."
        checked={settings.showSectionNumbers}
        onChange={(showSectionNumbers) => void onSave({ showSectionNumbers })}
      />
      <div className="settings-field-group">
        <label>
          High contrast
          <select
            value={
              ['amoled', 'dark_high_contrast', 'white_high_contrast'].includes(settings.colorTheme)
                ? settings.colorTheme
                : ''
            }
            onChange={(e) => void onSave({ colorTheme: e.target.value || 'sage', accentColor: '' })}
          >
            <option value="">Use display theme</option>
            <option value="white_high_contrast">Light high contrast</option>
            <option value="dark_high_contrast">Dark high contrast</option>
            <option value="amoled">AMOLED</option>
          </select>
        </label>
      </div>
      <p className="muted">
        Reader appearance includes Atkinson Hyperlegible, text size, line spacing and paper tones.
        All navigation and playback controls support keyboard focus.
      </p>
    </section>
  );
}
export function PdfImportPreferences({ settings, onSave }: Props) {
  const pdf = settings.pdfImport;
  const save = (patch: Partial<typeof pdf>) => void onSave({ pdfImport: { ...pdf, ...patch } });
  return (
    <section className="settings-card">
      <h2>PDF import tools</h2>
      <p className="muted">
        These defaults apply to the extracted Reading view of your next PDF import. Original pages
        keep their source layout.
      </p>
      <h3 className="settings-subheading">Pages to extract</h3>
      <div className="settings-page-range">
        <div className="settings-pair">
          <label>
            First page
            <input
              type="number"
              min="1"
              max="3000"
              value={pdf.startPage}
              onFocus={(event) => event.currentTarget.select()}
              onChange={(e) => {
                if (e.target.validity.valid && e.target.value) {
                  const startPage = Number(e.target.value);
                  save({
                    startPage,
                    endPage: pdf.endPage && pdf.endPage < startPage ? startPage : pdf.endPage,
                  });
                }
              }}
            />
          </label>
          <label>
            Last page
            <input
              type="number"
              min="1"
              max="3000"
              placeholder="End of document"
              value={pdf.endPage || ''}
              onFocus={(event) => event.currentTarget.select()}
              onChange={(e) => {
                if (!e.target.value) save({ endPage: 0 });
                else if (e.target.validity.valid) {
                  const endPage = Number(e.target.value);
                  save({
                    endPage,
                    startPage: endPage && endPage < pdf.startPage ? endPage : pdf.startPage,
                  });
                }
              }}
            />
          </label>
        </div>
        <p className="field-hint">
          Leave Last page blank to extract through the end of the document.
        </p>
        <button className="text-button" onClick={() => save({ startPage: 1, endPage: 0 })}>
          Use all pages
        </button>
      </div>
      <h3 className="settings-subheading">Clean extracted text</h3>
      <SettingToggle
        label="Remove repeated headers and footers"
        detail="Skip repeated lines at the top or bottom of at least three pages."
        checked={pdf.cleanupRepeatedLines}
        onChange={(cleanupRepeatedLines) => save({ cleanupRepeatedLines })}
      />
      <SettingToggle
        label="Remove page numbers"
        detail="Keep standalone page numbers out of narration and reading text."
        checked={pdf.removePageNumbers}
        onChange={(removePageNumbers) => save({ removePageNumbers })}
      />
      <SettingToggle
        label="Repair line-break hyphenation"
        detail="Join words split by a hyphen at the end of a line."
        checked={pdf.repairHyphenation}
        onChange={(repairHyphenation) => save({ repairHyphenation })}
      />
      <p className="muted">
        Scanned-page OCR, manual crop and advanced column extraction are still being built for
        desktop. Image-only PDFs can be opened in Original document view.
      </p>
    </section>
  );
}
export function AiStudyPreferences({
  settings,
  onSave,
  onNavigate,
}: Props & { onNavigate: (page: Page) => void }) {
  return (
    <section className="settings-card">
      <h2>AI & study mode</h2>
      <p>
        Prepare a prompt for an assistant you already use, then paste its reply into Study Hub to
        review flashcards or take a scored quiz.
      </p>
      <label>
        Preferred assistant
        <select
          value={settings.aiAssistant}
          onChange={(e) => void onSave({ aiAssistant: e.target.value })}
        >
          {['ChatGPT', 'Gemini', 'Claude', 'Copilot', 'Perplexity'].map((name) => (
            <option key={name} value={name.toLowerCase()}>
              {name}
            </option>
          ))}
        </select>
      </label>
      <ol className="settings-steps">
        <li>Choose a passage and a task in AI handoff.</li>
        <li>Review the prompt, then copy it or open your assistant.</li>
        <li>Paste the response into Study Hub and review it before adding cards or quizzes.</li>
      </ol>
      <div className="action-row">
        <button className="primary" onClick={() => onNavigate('ai')}>
          Open AI handoff
        </button>
        <button className="secondary" onClick={() => onNavigate('study')}>
          Open Study Hub
        </button>
      </div>
      <p className="muted">
        No API key or local language model is required for handoff. In-app API generation and
        optional local models remain separate desktop parity work.
      </p>
    </section>
  );
}
