import { afterEach, expect, it, vi } from 'vitest';
const transport = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock('./api', () => ({ call: transport.invoke }));
import { Playback } from './playback';
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});
it('discards obsolete synthesis after seeking before it reaches the audio device', async () => {
  let finish!: (value: unknown) => void;
  const createBuffer = vi.fn();
  class Context {
    resume = async () => {};
    createBuffer = createBuffer;
  }
  vi.stubGlobal('AudioContext', Context);
  transport.invoke.mockImplementation((command: string) =>
    command === 'synthesize'
      ? new Promise((resolve) => {
          finish = resolve;
        })
      : Promise.resolve(),
  );
  const player = new Playback();
  const onChunk = vi.fn();
  const playback = player.start('voice', ['An old passage.'], 0, 1, onChunk, vi.fn());
  await vi.waitFor(() => expect(finish).toBeTypeOf('function'));
  await player.stop();
  finish({ samples: [0.1, 0.2], sampleRate: 24000 });
  await playback;
  expect(createBuffer).not.toHaveBeenCalled();
  expect(onChunk).not.toHaveBeenCalled();
});
