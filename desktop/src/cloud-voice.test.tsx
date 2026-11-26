// @vitest-environment jsdom
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CloudVoiceSettings } from './CloudVoiceSettings';

const { mockStatus } = vi.hoisted(() => ({
  mockStatus: {
    config: {
      enabled: false,
      provider: 'ELEVENLABS' as const,
      endpoint: 'https://api.elevenlabs.io/v1/text-to-speech',
      model: 'eleven_v4',
      voice: 'JBFqnCBsd6RMkjVDRZzb',
      language: '',
      wifiOnly: true,
      fallbackToLocal: false,
      cacheAudio: false,
      timeoutSeconds: 30,
      retries: 0,
      pricePerMillion: 0,
      region: '',
    },
    hasKey: false,
    keyMasked: null,
    usage: {
      provider: 'elevenlabs',
      characters: 1500,
      requests: 3,
    },
  },
}));

vi.mock('./api', () => ({
  native: true,
  api: {
    cloudVoiceStatus: vi.fn().mockResolvedValue(mockStatus),
    saveCloudVoiceConfig: vi.fn().mockResolvedValue(mockStatus),
    setCloudVoiceApiKey: vi.fn().mockResolvedValue(mockStatus),
    testCloudVoice: vi.fn().mockResolvedValue({ samples: [], sampleRate: 24000 }),
    fetchCloudVoiceCatalog: vi.fn().mockResolvedValue([]),
    clearCloudVoiceUsage: vi.fn().mockResolvedValue(mockStatus),
  },
  call: vi.fn(),
}));

describe('CloudVoiceSettings', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders provider selection and toggle switch', async () => {
    const handleSelectVoice = vi.fn().mockResolvedValue(undefined);

    render(
      <CloudVoiceSettings
        sampleText="Hello world"
        onSelectVoice={handleSelectVoice}
        selectedVoice=""
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('Personal Cloud Voice Streaming')).toBeInTheDocument();
    });

    expect(screen.getByText('ElevenLabs')).toBeInTheDocument();
    expect(screen.getByText('Cartesia')).toBeInTheDocument();
    expect(screen.getByText('Microsoft Azure Speech')).toBeInTheDocument();
    expect(screen.getByText('Google Cloud TTS')).toBeInTheDocument();
  });

  it('switches active provider when chip is clicked', async () => {
    const handleSelectVoice = vi.fn().mockResolvedValue(undefined);

    render(
      <CloudVoiceSettings
        sampleText="Hello world"
        onSelectVoice={handleSelectVoice}
        selectedVoice=""
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('Personal Cloud Voice Streaming')).toBeInTheDocument();
    });

    const azureBtn = screen.getByRole('button', { name: /microsoft azure speech/i });
    fireEvent.click(azureBtn);

    await waitFor(() => {
      expect(screen.getByText(/400\+ neural voices across 140\+ languages/i)).toBeInTheDocument();
    });
  });
});
