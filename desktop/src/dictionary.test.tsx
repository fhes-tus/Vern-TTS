// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { DictionaryBrowser } from './DictionaryBrowser';
import { api } from './api';

describe('DictionaryBrowser', () => {
  it('renders download banner when dictionary pack is not installed', async () => {
    vi.spyOn(api, 'dictionaryStatus').mockResolvedValue([
      null,
      { isDownloading: false, progress: 0, error: null },
    ]);

    render(<DictionaryBrowser />);

    await waitFor(() => {
      expect(screen.getByText(/Open English WordNet 2025 Pack/i)).toBeInTheDocument();
      expect(screen.getByText(/Download offline pack/i)).toBeInTheDocument();
    });
  });

  it('renders search and alphabet browsing when pack is installed', async () => {
    vi.spyOn(api, 'dictionaryStatus').mockResolvedValue([
      {
        id: 'oewn-2025',
        title: 'English · Open English WordNet 2025',
        words: 120000,
        bytes: 32000000,
        source: 'https://en-word.net/',
        license: 'CC BY 4.0',
        credit: 'Open English WordNet Community',
      },
      { isDownloading: false, progress: 0, error: null },
    ]);
    vi.spyOn(api, 'browseWords').mockResolvedValue({
      words: ['apple', 'application', 'apply'],
      hasMore: true,
    });

    render(<DictionaryBrowser />);

    await waitFor(() => {
      expect(screen.getByText(/Installed/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/Look up a word or lemma/i)).toBeInTheDocument();
      expect(screen.getByText('apple')).toBeInTheDocument();
      expect(screen.getByText('application')).toBeInTheDocument();
      expect(screen.getByText('apply')).toBeInTheDocument();
    });
  });

  it('displays word definition when looked up', async () => {
    vi.spyOn(api, 'dictionaryStatus').mockResolvedValue([
      {
        id: 'oewn-2025',
        title: 'English · Open English WordNet 2025',
        words: 120000,
        bytes: 32000000,
        source: 'https://en-word.net/',
        license: 'CC BY 4.0',
        credit: 'Open English WordNet Community',
      },
      { isDownloading: false, progress: 0, error: null },
    ]);
    vi.spyOn(api, 'browseWords').mockResolvedValue({ words: [], hasMore: false });
    vi.spyOn(api, 'lookupWord').mockResolvedValue({
      query: 'serendipity',
      headword: 'serendipity',
      senses: [
        {
          partOfSpeech: 'noun',
          definition: 'good luck in making unexpected and fortunate discoveries',
          examples: ['a fortunate stroke of serendipity'],
          synonyms: ['good fortune'],
        },
      ],
    });

    render(<DictionaryBrowser initialWord="serendipity" />);

    await waitFor(() => {
      expect(screen.getByText('serendipity')).toBeInTheDocument();
      expect(screen.getByText(/good luck in making unexpected/i)).toBeInTheDocument();
      expect(screen.getByText(/"a fortunate stroke of serendipity"/i)).toBeInTheDocument();
      expect(screen.getByText('noun')).toBeInTheDocument();
      expect(screen.getByText('Save to vocabulary')).toBeInTheDocument();
    });
  });
});
