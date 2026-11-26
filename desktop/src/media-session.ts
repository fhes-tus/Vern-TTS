/**
 * MediaSession and Hardware Media Controls for Vern Desktop.
 * Provides integration with Windows System Media Transport Controls (SMTC)
 * and hardware media keys (Play/Pause, Next Track, Previous Track, Stop)
 * via navigator.mediaSession, plus Audio Output Safety (pausing when headphones/devices disconnect).
 */

export interface MediaMetadataOptions {
  title: string;
  artist?: string;
  album?: string;
  artwork?: Array<{ src: string; sizes?: string; type?: string }>;
}

export function updateMediaMetadata(options: MediaMetadataOptions | null) {
  if (typeof window === 'undefined' || !('mediaSession' in navigator)) return;
  if (!options) {
    navigator.mediaSession.metadata = null;
    return;
  }
  try {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: options.title,
      artist: options.artist || 'Vern Reader',
      album: options.album || 'Vern Reader',
      artwork:
        options.artwork && options.artwork.length > 0
          ? options.artwork
          : [{ src: '/vern-icon.png', sizes: '512x512', type: 'image/png' }],
    });
  } catch {
    // Some platforms/browsers may restrict MediaMetadata constructor
  }
}

export function updatePlaybackState(state: 'none' | 'paused' | 'playing') {
  if (typeof window === 'undefined' || !('mediaSession' in navigator)) return;
  try {
    navigator.mediaSession.playbackState = state;
  } catch {
    // ignore
  }
}

export interface PositionStateOptions {
  duration: number;
  position: number;
  playbackRate?: number;
}

export function updatePositionState(options: PositionStateOptions | null) {
  if (typeof window === 'undefined' || !('mediaSession' in navigator)) return;
  if (typeof navigator.mediaSession.setPositionState !== 'function') return;

  try {
    if (!options || options.duration <= 0) {
      navigator.mediaSession.setPositionState();
      return;
    }
    const safeDuration = Math.max(1, options.duration);
    const safePosition = Math.max(0, Math.min(options.position, safeDuration));
    navigator.mediaSession.setPositionState({
      duration: safeDuration,
      playbackRate: options.playbackRate ?? 1.0,
      position: safePosition,
    });
  } catch {
    // ignore
  }
}

export interface MediaSessionActionCallbacks {
  onPlay?: () => void;
  onPause?: () => void;
  onStop?: () => void;
  onPreviousTrack?: () => void;
  onNextTrack?: () => void;
  onSeekBackward?: () => void;
  onSeekForward?: () => void;
  onSeekTo?: (details: MediaSessionActionDetails) => void;
}

export function registerMediaSessionHandlers(callbacks: MediaSessionActionCallbacks): () => void {
  if (typeof window === 'undefined' || !('mediaSession' in navigator)) return () => {};

  const handlers: Array<
    [MediaSessionAction, ((details: MediaSessionActionDetails) => void) | undefined]
  > = [
    ['play', callbacks.onPlay],
    ['pause', callbacks.onPause],
    ['stop', callbacks.onStop],
    ['previoustrack', callbacks.onPreviousTrack],
    ['nexttrack', callbacks.onNextTrack],
    ['seekbackward', callbacks.onSeekBackward ?? callbacks.onPreviousTrack],
    ['seekforward', callbacks.onSeekForward ?? callbacks.onNextTrack],
    ['seekto', callbacks.onSeekTo],
  ];

  for (const [action, handler] of handlers) {
    try {
      if (handler) {
        navigator.mediaSession.setActionHandler(action, handler);
      } else {
        navigator.mediaSession.setActionHandler(action, null);
      }
    } catch {
      // ignore unsupported actions on certain platforms
    }
  }

  return () => {
    for (const [action] of handlers) {
      try {
        navigator.mediaSession.setActionHandler(action, null);
      } catch {
        // ignore
      }
    }
  };
}

/**
 * Audio Output Safety:
 * Monitors audio device connections and disconnections.
 * If headphones or external audio device are unplugged while playing,
 * automatically pauses narration to prevent sound from suddenly blasting through speakers.
 */
export function setupAudioOutputSafety(
  isPlaying: () => boolean,
  onDisconnectPause: () => void,
): () => void {
  if (typeof window === 'undefined' || !navigator.mediaDevices?.addEventListener) {
    return () => {};
  }

  let lastDeviceCount: number | null = null;

  const checkDevices = async () => {
    try {
      if (!navigator.mediaDevices.enumerateDevices) {
        if (isPlaying()) {
          onDisconnectPause();
        }
        return;
      }
      const devices = await navigator.mediaDevices.enumerateDevices();
      const outputDevices = devices.filter((d) => d.kind === 'audiooutput');
      const count = outputDevices.length;

      if (lastDeviceCount !== null && count < lastDeviceCount) {
        if (isPlaying()) {
          onDisconnectPause();
        }
      }
      lastDeviceCount = count;
    } catch {
      if (isPlaying()) {
        onDisconnectPause();
      }
    }
  };

  if (navigator.mediaDevices.enumerateDevices) {
    navigator.mediaDevices
      .enumerateDevices()
      .then((devices) => {
        const outputs = devices.filter((d) => d.kind === 'audiooutput');
        lastDeviceCount = outputs.length;
      })
      .catch(() => {});
  }

  const handleDeviceChange = () => {
    void checkDevices();
  };

  navigator.mediaDevices.addEventListener('devicechange', handleDeviceChange);
  return () => {
    navigator.mediaDevices.removeEventListener('devicechange', handleDeviceChange);
  };
}
