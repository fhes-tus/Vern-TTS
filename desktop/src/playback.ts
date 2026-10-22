import { call } from './api';

type AudioChunk = { samples: number[]; sampleRate: number };
export class Playback {
  private generation = Date.now() * 1000;
  private context: AudioContext | null = null;
  private source: AudioBufferSourceNode | null = null;
  private sourceEnd = 0;
  private deadline: number | null = null;
  setSleepDeadline(deadline: number | null) {
    this.deadline = deadline;
    if (this.source && this.context)
      this.source.stop(
        Math.min(
          this.sourceEnd,
          deadline
            ? this.context.currentTime + Math.max(0, deadline - Date.now()) / 1000
            : this.sourceEnd,
        ),
      );
  }
  private async expired(onState: (state: string) => void) {
    if (this.deadline === null || this.deadline - Date.now() > 50) return false;
    this.deadline = null;
    await this.stop();
    onState('Sleep timer ended');
    return true;
  }
  async stop() {
    const generation = ++this.generation;
    this.source?.stop();
    this.source = null;
    await call('cancel_speech', { generation });
  }
  async start(
    voiceId: string,
    chunks: string[],
    start: number,
    rate: number,
    onChunk: (index: number) => Promise<void>,
    onState: (state: string) => void,
  ) {
    await this.stop();
    const generation = this.generation;
    this.context ??= new AudioContext();
    await this.context.resume();
    try {
      // Keep at most one future passage in flight. Rejections are handled immediately,
      // even when a seek cancels the loop before it consumes the prefetched result.
      const prepare = (index: number) =>
        call<AudioChunk>('synthesize', {
          voiceId,
          text: chunks[index],
          rate,
          generation,
        }).then(
          (audio) => ({ audio, error: null }),
          (error: unknown) => ({ audio: null, error }),
        );
      let pending = prepare(start);
      for (let index = start; index < chunks.length && generation === this.generation; index++) {
        if (await this.expired(onState)) return;
        onState('Preparing voice…');
        const result = await pending;
        if (generation !== this.generation) return;
        if (await this.expired(onState)) return;
        if (!result.audio) throw result.error;
        const audio = result.audio;
        if (index + 1 < chunks.length) pending = prepare(index + 1);
        const buffer = this.context.createBuffer(1, audio.samples.length, audio.sampleRate);
        buffer.copyToChannel(Float32Array.from(audio.samples), 0);
        await onChunk(index);
        if (generation !== this.generation) return;
        onState('Playing offline');
        await new Promise<void>((resolve) => {
          const source = this.context!.createBufferSource();
          this.source = source;
          source.buffer = buffer;
          source.connect(this.context!.destination);
          source.onended = () => {
            source.disconnect();
            if (this.source === source) this.source = null;
            resolve();
          };
          source.start();
          this.sourceEnd = this.context!.currentTime + buffer.duration;
          this.setSleepDeadline(this.deadline);
        });
        if (generation !== this.generation) return;
        if (await this.expired(onState)) return;
        if (index + 1 < chunks.length) await onChunk(index + 1);
      }
      if (generation === this.generation) onState('Finished');
    } catch (error) {
      if (generation === this.generation) throw error;
    }
  }
}
