import { useEffect, useRef } from 'react';
import { api, native } from './api';
import type { ActivityEvent, ActivityTarget } from './types';

// Transient bridge only: the native monotonic clock computes and saves elapsed time.
export function useReadingActivity(
  reader: ActivityTarget | null,
  audio: ActivityTarget | null,
  playbackRequested: boolean,
  onError: (error: unknown) => void,
) {
  const latest = useRef({ reader, audio, playbackRequested });
  const queue = useRef(Promise.resolve());
  const send = useRef<(interacting?: boolean) => void>(() => {});
  useEffect(() => {
    if (!native) return;
    function publish(interacting = false) {
      const state = latest.current;
      const event: ActivityEvent = {
        ...state,
        reader: document.visibilityState === 'visible' && document.hasFocus() ? state.reader : null,
        interacting,
      };
      queue.current = queue.current.then(() => api.recordActivity(event)).catch(onError);
    }
    send.current = publish;
    let lastInteraction = -Infinity;
    const interaction = () => {
      // Pointer movement is deliberately excluded; reading gestures/keys renew activity.
      if (performance.now() - lastInteraction < 1000) return;
      lastInteraction = performance.now();
      if (document.visibilityState === 'visible' && document.hasFocus()) publish(true);
    };
    const visibility = () => publish();
    const timer = setInterval(publish, 5000);
    window.addEventListener('focus', visibility);
    window.addEventListener('blur', visibility);
    document.addEventListener('visibilitychange', visibility);
    document.addEventListener('pointerdown', interaction, { passive: true });
    document.addEventListener('keydown', interaction);
    document.addEventListener('wheel', interaction, { passive: true });
    return () => {
      clearInterval(timer);
      window.removeEventListener('focus', visibility);
      window.removeEventListener('blur', visibility);
      document.removeEventListener('visibilitychange', visibility);
      document.removeEventListener('pointerdown', interaction);
      document.removeEventListener('keydown', interaction);
      document.removeEventListener('wheel', interaction);
      send.current = () => {};
      queue.current = queue.current
        .then(() =>
          api.recordActivity({
            reader: null,
            audio: null,
            interacting: false,
            playbackRequested: false,
          }),
        )
        .catch(onError);
    };
  }, [onError]);
  useEffect(() => {
    latest.current = { reader, audio, playbackRequested };
    send.current();
  }, [reader, audio, playbackRequested]);
}
