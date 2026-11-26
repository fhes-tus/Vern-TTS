// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  registerMediaSessionHandlers,
  setupAudioOutputSafety,
  updateMediaMetadata,
  updatePlaybackState,
  updatePositionState,
} from './media-session';

describe('media-session integration', () => {
  let mediaSessionMock: {
    metadata: unknown;
    playbackState: string;
    setActionHandler: ReturnType<typeof vi.fn>;
    setPositionState: ReturnType<typeof vi.fn>;
  };
  let mediaDevicesMock: {
    addEventListener: ReturnType<typeof vi.fn>;
    removeEventListener: ReturnType<typeof vi.fn>;
    enumerateDevices: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    mediaSessionMock = {
      metadata: null,
      playbackState: 'none',
      setActionHandler: vi.fn(),
      setPositionState: vi.fn(),
    };

    mediaDevicesMock = {
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      enumerateDevices: vi.fn().mockResolvedValue([
        { kind: 'audiooutput', deviceId: 'default' },
        { kind: 'audiooutput', deviceId: 'headphones' },
      ]),
    };

    Object.defineProperty(window.navigator, 'mediaSession', {
      configurable: true,
      value: mediaSessionMock,
    });

    Object.defineProperty(window.navigator, 'mediaDevices', {
      configurable: true,
      value: mediaDevicesMock,
    });

    vi.stubGlobal(
      'MediaMetadata',
      class MockMediaMetadata {
        title: string;
        artist: string;
        album: string;
        artwork: unknown[];
        constructor(init: { title: string; artist: string; album: string; artwork: unknown[] }) {
          this.title = init.title;
          this.artist = init.artist;
          this.album = init.album;
          this.artwork = init.artwork;
        }
      },
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('updates media session metadata correctly', () => {
    updateMediaMetadata({
      title: 'Alice in Wonderland',
      artist: 'Lewis Carroll',
      album: 'Passage 5 of 100',
    });

    expect(mediaSessionMock.metadata).toBeDefined();
    const meta = mediaSessionMock.metadata as { title: string; artist: string; album: string };
    expect(meta.title).toBe('Alice in Wonderland');
    expect(meta.artist).toBe('Lewis Carroll');
    expect(meta.album).toBe('Passage 5 of 100');

    updateMediaMetadata(null);
    expect(mediaSessionMock.metadata).toBeNull();
  });

  it('updates playback state correctly', () => {
    updatePlaybackState('playing');
    expect(mediaSessionMock.playbackState).toBe('playing');

    updatePlaybackState('paused');
    expect(mediaSessionMock.playbackState).toBe('paused');
  });

  it('updates position state correctly with bounds clamping', () => {
    updatePositionState({ duration: 50, position: 20, playbackRate: 1.25 });
    expect(mediaSessionMock.setPositionState).toHaveBeenCalledWith({
      duration: 50,
      position: 20,
      playbackRate: 1.25,
    });

    // Clamps position exceeding duration
    updatePositionState({ duration: 30, position: 50 });
    expect(mediaSessionMock.setPositionState).toHaveBeenCalledWith({
      duration: 30,
      position: 30,
      playbackRate: 1.0,
    });
  });

  it('registers media action handlers and cleans them up', () => {
    const onPlay = vi.fn();
    const onPause = vi.fn();
    const onNextTrack = vi.fn();
    const onPreviousTrack = vi.fn();

    const cleanup = registerMediaSessionHandlers({
      onPlay,
      onPause,
      onNextTrack,
      onPreviousTrack,
    });

    expect(mediaSessionMock.setActionHandler).toHaveBeenCalledWith('play', onPlay);
    expect(mediaSessionMock.setActionHandler).toHaveBeenCalledWith('pause', onPause);
    expect(mediaSessionMock.setActionHandler).toHaveBeenCalledWith('nexttrack', onNextTrack);
    expect(mediaSessionMock.setActionHandler).toHaveBeenCalledWith(
      'previoustrack',
      onPreviousTrack,
    );

    cleanup();
    expect(mediaSessionMock.setActionHandler).toHaveBeenCalledWith('play', null);
    expect(mediaSessionMock.setActionHandler).toHaveBeenCalledWith('pause', null);
    expect(mediaSessionMock.setActionHandler).toHaveBeenCalledWith('nexttrack', null);
    expect(mediaSessionMock.setActionHandler).toHaveBeenCalledWith('previoustrack', null);
  });

  it('sets up audio output safety and detects device disconnect while playing', async () => {
    let deviceChangeHandler!: () => void;
    mediaDevicesMock.addEventListener.mockImplementation((event: string, handler: () => void) => {
      if (event === 'devicechange') deviceChangeHandler = handler;
    });

    let playing = true;
    const onDisconnect = vi.fn();

    const cleanup = setupAudioOutputSafety(() => playing, onDisconnect);

    // Initial enumeration runs
    await vi.waitFor(() => {
      expect(mediaDevicesMock.enumerateDevices).toHaveBeenCalled();
    });

    // Simulate unplugging headphones: output count goes from 2 to 1
    mediaDevicesMock.enumerateDevices.mockResolvedValueOnce([
      { kind: 'audiooutput', deviceId: 'default' },
    ]);

    deviceChangeHandler();

    await vi.waitFor(() => {
      expect(onDisconnect).toHaveBeenCalledTimes(1);
    });

    // If not playing, unplugging does not trigger onDisconnect
    playing = false;
    deviceChangeHandler();
    expect(onDisconnect).toHaveBeenCalledTimes(1);

    cleanup();
    expect(mediaDevicesMock.removeEventListener).toHaveBeenCalledWith(
      'devicechange',
      expect.any(Function),
    );
  });
});
