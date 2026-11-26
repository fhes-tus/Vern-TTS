import { useEffect, useState } from 'react';
import { api, native } from './api';
import { Glyph } from './Glyph';
import type {
  DictionaryDownloadState,
  DictionaryEntry,
  DictionaryPackInfo,
  DictionaryWordPage,
} from './types';
import './dictionary.css';

export function DictionaryBrowser(props: {
  initialWord?: string;
  onClose?: () => void;
  onSaveVocabulary?: (word: string, definition: string) => void;
}) {
  const [pack, setPack] = useState<DictionaryPackInfo | null>(null);
  const [downloadState, setDownloadState] = useState<DictionaryDownloadState>({
    isDownloading: false,
    progress: 0,
    error: null,
  });
  const [query, setQuery] = useState(props.initialWord || '');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [activeEntry, setActiveEntry] = useState<DictionaryEntry | null>(null);
  const [entryLoading, setEntryLoading] = useState(false);
  const [entryError, setEntryError] = useState<string | null>(null);

  // Browse state
  const [prefix, setPrefix] = useState('');
  const [historyCursors, setHistoryCursors] = useState<string[]>([]);
  const [currentCursor, setCurrentCursor] = useState<string | null>(null);
  const [wordPage, setWordPage] = useState<DictionaryWordPage>({ words: [], hasMore: false });
  const [pageLoading, setPageLoading] = useState(false);
  const [savedWords, setSavedWords] = useState<Set<string>>(new Set());

  // Load status
  async function refreshStatus() {
    try {
      const [info, dl] = await api.dictionaryStatus();
      setPack(info);
      setDownloadState(dl);
    } catch {
      // Ignored in non-native or test environments
    }
  }

  useEffect(() => {
    refreshStatus();
    const timer = setInterval(() => {
      if (downloadState.isDownloading) {
        refreshStatus();
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [downloadState.isDownloading]);

  // Load initial word if given
  useEffect(() => {
    if (props.initialWord) {
      lookupWord(props.initialWord);
    }
  }, [props.initialWord]);

  // Load page when prefix or cursor changes
  useEffect(() => {
    if (!pack) return;
    let cancelled = false;
    setPageLoading(true);
    api
      .browseWords(prefix, currentCursor, 40)
      .then((res) => {
        if (!cancelled) {
          setWordPage(res);
          setPageLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) setPageLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [prefix, currentCursor, pack]);

  // Suggestions for autocomplete
  useEffect(() => {
    if (!query.trim() || !pack) {
      setSuggestions([]);
      return;
    }
    let cancelled = false;
    api.suggestWords(query).then((items) => {
      if (!cancelled) setSuggestions(items.filter((w) => w.toLowerCase() !== query.toLowerCase()));
    });
    return () => {
      cancelled = true;
    };
  }, [query, pack]);

  async function lookupWord(word: string) {
    if (!word.trim()) return;
    setEntryLoading(true);
    setEntryError(null);
    setSuggestions([]);
    try {
      const res = await api.lookupWord(word);
      if (res) {
        setActiveEntry(res);
        setQuery(res.headword);
      } else {
        setEntryError(`No definition found for "${word}".`);
      }
    } catch (e) {
      setEntryError(String(e));
    } finally {
      setEntryLoading(false);
    }
  }

  async function handleDownload() {
    try {
      await api.downloadDictionary();
      setDownloadState({ isDownloading: true, progress: 0, error: null });
    } catch (e) {
      setDownloadState((prev) => ({ ...prev, error: String(e) }));
    }
  }

  async function handleCancelDownload() {
    try {
      await api.cancelDictionaryDownload();
      setDownloadState({ isDownloading: false, progress: 0, error: 'Cancelled' });
    } catch (e) {
      console.error(e);
    }
  }

  async function handleDelete() {
    if (!window.confirm('Remove the offline English dictionary pack from this PC?')) return;
    try {
      await api.deleteDictionary();
      setPack(null);
      setActiveEntry(null);
      setWordPage({ words: [], hasMore: false });
    } catch (e) {
      console.error(e);
    }
  }

  async function handleSaveVocabulary(entry: DictionaryEntry) {
    const firstDef = entry.senses[0]?.definition || '';
    if (props.onSaveVocabulary) {
      props.onSaveVocabulary(entry.headword, firstDef);
    } else {
      try {
        const settings = await api.settings();
        const existing = settings.vocabulary || [];
        if (!existing.some((v) => v.word.toLowerCase() === entry.headword.toLowerCase())) {
          await api.saveSettings({
            ...settings,
            vocabulary: [
              {
                id: crypto.randomUUID(),
                word: entry.headword,
                definition: firstDef,
                bookId: null,
                position: null,
              },
              ...existing,
            ],
          });
        }
      } catch (e) {
        console.error(e);
      }
    }
    setSavedWords((prev) => new Set([...prev, entry.headword]));
  }

  function handlePrefixSelect(letter: string) {
    setPrefix(letter);
    setCurrentCursor(null);
    setHistoryCursors([]);
    setActiveEntry(null);
  }

  function handleNextPage() {
    if (wordPage.words.length === 0) return;
    const lastWord = wordPage.words[wordPage.words.length - 1];
    setHistoryCursors((prev) => [...prev, currentCursor || '']);
    setCurrentCursor(lastWord);
  }

  function handlePrevPage() {
    if (historyCursors.length === 0) return;
    const prevCursors = [...historyCursors];
    const prevCursor = prevCursors.pop()!;
    setHistoryCursors(prevCursors);
    setCurrentCursor(prevCursor === '' ? null : prevCursor);
  }

  return (
    <div className="dictionary-container" data-testid="dictionary-browser">
      <div className="dictionary-header">
        <div className="dictionary-title-row">
          <div className="dictionary-title">
            <Glyph name="book" />
            <h2>Offline English Dictionary</h2>
          </div>
          {props.onClose && (
            <button className="icon-button" onClick={props.onClose} title="Close dictionary">
              <Glyph name="x" />
            </button>
          )}
        </div>

        {/* Pack Info / Download Banner */}
        {!pack ? (
          <div className="dictionary-banner download-needed">
            <div className="banner-text">
              <strong>Open English WordNet 2025 Pack</strong>
              <p>
                Offline definitions, parts of speech, and examples for ~120,000 words. Download once
                (~9.6 MB), use completely offline without an internet connection.
              </p>
            </div>
            {downloadState.isDownloading ? (
              <div className="dictionary-progress-bar">
                <div
                  className="progress-fill"
                  style={{
                    transform: `scaleX(${Math.min(1, Math.max(0, downloadState.progress / 100))})`,
                  }}
                />
                <span>Downloading & indexing... {downloadState.progress}%</span>
                <button className="secondary small" onClick={handleCancelDownload}>
                  Cancel
                </button>
              </div>
            ) : (
              <div className="banner-actions">
                <button
                  className="primary"
                  onClick={handleDownload}
                  disabled={!native}
                  title={!native ? 'Only available in native desktop app' : undefined}
                >
                  <Glyph name="download" />
                  Download offline pack (~9.6 MB)
                </button>
                {downloadState.error && <p className="banner-error">{downloadState.error}</p>}
              </div>
            )}
          </div>
        ) : (
          <div className="dictionary-banner installed">
            <div className="banner-text">
              <span className="installed-badge">Installed</span>
              <strong>{pack.title}</strong>
              <span className="pack-details">
                {pack.words.toLocaleString()} words · {(pack.bytes / (1024 * 1024)).toFixed(1)} MB ·{' '}
                {pack.credit}
              </span>
            </div>
            <button className="danger small" onClick={handleDelete} title="Remove dictionary">
              <Glyph name="trash" />
              Remove pack
            </button>
          </div>
        )}

        {/* Search bar */}
        {pack && (
          <div className="dictionary-search-box">
            <div className="search-input-wrapper">
              <Glyph name="search" />
              <input
                type="text"
                placeholder="Look up a word or lemma..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') lookupWord(query);
                }}
              />
              {query && (
                <button
                  className="clear-search"
                  onClick={() => {
                    setQuery('');
                    setActiveEntry(null);
                  }}
                >
                  <Glyph name="x" />
                </button>
              )}
              <button
                className="primary search-btn"
                onClick={() => lookupWord(query)}
                disabled={!query.trim() || entryLoading}
              >
                Lookup
              </button>
            </div>

            {/* Suggestions dropdown */}
            {suggestions.length > 0 && (
              <div className="dictionary-suggestions">
                {suggestions.map((s) => (
                  <button
                    key={s}
                    className="suggestion-item"
                    onClick={() => {
                      setQuery(s);
                      lookupWord(s);
                    }}
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Main Body */}
      {pack && (
        <div className="dictionary-body">
          {/* Active Entry Definition Card */}
          {activeEntry ? (
            <div className="entry-card">
              <div className="entry-header">
                <div>
                  <h3 className="entry-headword">{activeEntry.headword}</h3>
                  {activeEntry.query.toLowerCase() !== activeEntry.headword.toLowerCase() && (
                    <span className="entry-query-alias">Matched from: {activeEntry.query}</span>
                  )}
                </div>
                <div className="entry-actions">
                  <button
                    className="secondary small"
                    onClick={() => handleSaveVocabulary(activeEntry)}
                    disabled={savedWords.has(activeEntry.headword)}
                  >
                    <Glyph name="bookmark" />
                    {savedWords.has(activeEntry.headword) ? 'Saved' : 'Save to vocabulary'}
                  </button>
                  <button
                    className="secondary small"
                    onClick={() => setActiveEntry(null)}
                    title="Back to browsing"
                  >
                    Browse list
                  </button>
                </div>
              </div>

              <div className="senses-list">
                {activeEntry.senses.map((sense, idx) => (
                  <div key={idx} className="sense-item">
                    <div className="sense-pos-row">
                      <span className={`pos-tag pos-${sense.partOfSpeech}`}>
                        {sense.partOfSpeech}
                      </span>
                      <span className="sense-num">#{idx + 1}</span>
                    </div>
                    <p className="sense-def">{sense.definition}</p>
                    {sense.examples.length > 0 && (
                      <ul className="sense-examples">
                        {sense.examples.map((ex, i) => (
                          <li key={i}>"{ex}"</li>
                        ))}
                      </ul>
                    )}
                    {sense.synonyms.length > 0 && (
                      <div className="sense-synonyms">
                        <span className="synonyms-label">Synonyms:</span>
                        {sense.synonyms.map((syn) => (
                          <button
                            key={syn}
                            className="synonym-pill"
                            onClick={() => {
                              setQuery(syn);
                              lookupWord(syn);
                            }}
                          >
                            {syn}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ) : entryLoading ? (
            <div className="dictionary-loading">
              <p>Looking up definition...</p>
            </div>
          ) : entryError ? (
            <div className="dictionary-error">
              <p>{entryError}</p>
              <button
                className="secondary small"
                onClick={() => {
                  setEntryError(null);
                  setQuery('');
                }}
              >
                Clear
              </button>
            </div>
          ) : (
            /* Browse Alphabet & Wordlist */
            <div className="dictionary-browse-section">
              <div className="alphabet-chips">
                <button
                  className={`chip ${prefix === '' ? 'active' : ''}`}
                  onClick={() => handlePrefixSelect('')}
                >
                  All
                </button>
                {'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map((letter) => (
                  <button
                    key={letter}
                    className={`chip ${prefix.toUpperCase() === letter ? 'active' : ''}`}
                    onClick={() => handlePrefixSelect(letter.toLowerCase())}
                  >
                    {letter}
                  </button>
                ))}
              </div>

              <div className="words-grid-wrapper">
                {pageLoading ? (
                  <div className="dictionary-loading">Loading words...</div>
                ) : wordPage.words.length === 0 ? (
                  <div className="dictionary-empty">No words found starting with "{prefix}".</div>
                ) : (
                  <div className="words-grid">
                    {wordPage.words.map((w) => (
                      <button
                        key={w}
                        className="word-item-btn"
                        onClick={() => {
                          setQuery(w);
                          lookupWord(w);
                        }}
                      >
                        {w}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* Keyset pagination footer */}
              <div className="browse-pagination">
                <button
                  className="secondary small"
                  onClick={handlePrevPage}
                  disabled={historyCursors.length === 0 || pageLoading}
                >
                  <Glyph name="chevron-left" />
                  Previous
                </button>
                <span className="page-indicator">
                  {prefix ? `Prefix: "${prefix.toUpperCase()}"` : 'All words'}
                </span>
                <button
                  className="secondary small"
                  onClick={handleNextPage}
                  disabled={!wordPage.hasMore || pageLoading}
                >
                  Next
                  <Glyph name="chevron-right" />
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
