import { beforeEach, expect, it, vi } from 'vitest';
import type { BackupPreview } from './types';

const mocks = vi.hoisted(() => ({
  inspectBackup: vi.fn(),
  androidBackupText: vi.fn(),
  acceptAndroidText: vi.fn(),
  finishAndroidBackup: vi.fn(),
  discardBackup: vi.fn(),
  index: vi.fn(),
  close: vi.fn(),
}));
vi.mock('./api', () => ({ api: mocks }));
vi.mock('./android-index-client', () => ({
  AndroidIndexer: class {
    index = mocks.index;
    close = mocks.close;
  },
}));
import { inspectBackup } from './backup-conversion';
const preview = { token: 'backup-token', books: 2 } as BackupPreview;
beforeEach(() => {
  vi.resetAllMocks();
  mocks.inspectBackup.mockResolvedValue({ kind: 'android', token: 'backup-token', documents: 2 });
  mocks.androidBackupText.mockResolvedValue({ text: 'Reading.' });
  mocks.index.mockResolvedValue({ chunks: ['Reading.'] });
  mocks.finishAndroidBackup.mockResolvedValue(preview);
  mocks.discardBackup.mockResolvedValue(undefined);
});
it('converts sequentially and hands off a single preview after all native acknowledgements', async () => {
  const progress = vi.fn();
  expect(await inspectBackup(new AbortController().signal, progress)).toBe(preview);
  expect(mocks.acceptAndroidText.mock.calls.map((call) => call.slice(0, 2))).toEqual([
    ['backup-token', 0],
    ['backup-token', 1],
  ]);
  expect(progress.mock.calls).toEqual([
    [0, 2],
    [1, 2],
    [2, 2],
  ]);
  expect(mocks.finishAndroidBackup).toHaveBeenCalledOnce();
  expect(mocks.close).toHaveBeenCalledOnce();
  expect(mocks.discardBackup).not.toHaveBeenCalled();
});
it('releases native staging and the worker when indexing fails', async () => {
  mocks.index.mockRejectedValue(new Error('Sentence index mismatch'));
  await expect(inspectBackup(new AbortController().signal, vi.fn())).rejects.toThrow(
    'Sentence index mismatch',
  );
  expect(mocks.acceptAndroidText).not.toHaveBeenCalled();
  expect(mocks.finishAndroidBackup).not.toHaveBeenCalled();
  expect(mocks.discardBackup).toHaveBeenCalledWith('backup-token');
  expect(mocks.close).toHaveBeenCalledOnce();
});
it('cancels after the native read without accepting converted data', async () => {
  const controller = new AbortController();
  mocks.androidBackupText.mockImplementation(async () => {
    controller.abort();
    return { text: 'Reading.' };
  });
  await expect(inspectBackup(controller.signal, vi.fn())).rejects.toThrow('cancelled');
  expect(mocks.index).not.toHaveBeenCalled();
  expect(mocks.acceptAndroidText).not.toHaveBeenCalled();
  expect(mocks.discardBackup).toHaveBeenCalledWith('backup-token');
});
it('discards a preview if navigation cancels while native validation finishes', async () => {
  const controller = new AbortController();
  mocks.finishAndroidBackup.mockImplementation(async () => {
    controller.abort();
    return preview;
  });
  await expect(inspectBackup(controller.signal, vi.fn())).rejects.toThrow('cancelled');
  expect(mocks.discardBackup).toHaveBeenCalledWith('backup-token');
});
it('keeps desktop preview and chooser cancellation independent of the Android worker', async () => {
  mocks.inspectBackup
    .mockResolvedValueOnce({ kind: 'desktop', preview })
    .mockResolvedValueOnce(null);
  expect(await inspectBackup(new AbortController().signal, vi.fn())).toBe(preview);
  expect(await inspectBackup(new AbortController().signal, vi.fn())).toBeNull();
  expect(mocks.index).not.toHaveBeenCalled();
  expect(mocks.discardBackup).not.toHaveBeenCalled();
});
