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

it('reports output only after synthesis and while the audio context is running', async () => {
  let synthesized!: (value: unknown) => void;
  let source!: {
    onended: () => void;
    start: () => void;
    connect: () => void;
    disconnect: () => void;
    stop: () => void;
    buffer?: unknown;
  };
  class Context extends EventTarget {
    state = 'running';
    currentTime = 0;
    destination = {};
    resume = async () => {};
    createBuffer = () => ({ copyToChannel: vi.fn(), duration: 1 });
    createBufferSource = () =>
      (source = {
        onended: () => {},
        start: vi.fn(),
        connect: vi.fn(),
        disconnect: vi.fn(),
        stop: vi.fn(),
      });
  }
  const context = new Context();
  vi.stubGlobal('AudioContext', function TestAudioContext() {
    return context;
  });
  transport.invoke.mockImplementation((command: string) =>
    command === 'synthesize'
      ? new Promise((resolve) => {
          synthesized = resolve;
        })
      : Promise.resolve(),
  );
  const output = vi.fn();
  const playback = new Playback().start(
    'voice',
    ['A reading passage.'],
    0,
    1,
    async () => {},
    vi.fn(),
    output,
  );
  await vi.waitFor(() => expect(synthesized).toBeTypeOf('function'));
  expect(output).not.toHaveBeenCalled();
  synthesized({ samples: [0.1, 0.2], sampleRate: 24000 });
  await vi.waitFor(() => expect(output).toHaveBeenLastCalledWith(0));
  context.state = 'suspended';
  context.dispatchEvent(new Event('statechange'));
  expect(output).toHaveBeenLastCalledWith(null);
  context.state = 'running';
  context.dispatchEvent(new Event('statechange'));
  expect(output).toHaveBeenLastCalledWith(0);
  source.onended();
  await playback;
  expect(output).toHaveBeenLastCalledWith(null);
});
