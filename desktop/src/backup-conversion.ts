import { api } from './api';
import { AndroidIndexer } from './android-index-client';
import type { BackupPreview } from './types';

export async function inspectBackup(
  signal: AbortSignal,
  onProgress: (completed: number, total: number) => void,
): Promise<BackupPreview | null> {
  const inspection = await api.inspectBackup();
  if (!inspection) return null;
  const token = inspection.kind === 'desktop' ? inspection.preview.token : inspection.token;
  let indexer: AndroidIndexer | undefined;
  let handedOff = false;
  try {
    const check = () => {
      if (signal.aborted) throw new Error('Backup conversion cancelled.');
    };
    check();
    if (inspection.kind === 'desktop') {
      handedOff = true;
      return inspection.preview;
    }
    indexer = new AndroidIndexer();
    onProgress(0, inspection.documents);
    for (let index = 0; index < inspection.documents; index++) {
      check();
      const input = await api.androidBackupText(token);
      check();
      if (!input) throw new Error('The Android backup conversion ended early. Choose it again.');
      const converted = await indexer.index(input, signal);
      check();
      await api.acceptAndroidText(token, index, converted);
      onProgress(index + 1, inspection.documents);
    }
    check();
    const preview = await api.finishAndroidBackup(token);
    check();
    handedOff = true;
    return preview;
  } finally {
    indexer?.close();
    if (!handedOff) await api.discardBackup(token).catch(() => {});
  }
}
