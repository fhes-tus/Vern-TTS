import { expect, test, type Page } from '@playwright/test';
import { defaultSettings, type Voice, type Settings } from '../../src/types';

async function voiceShell(page: Page) {
  await page.addInitScript(
    ({ prefs }) => {
      const base: Voice = {
        id: '',
        name: '',
        family: '',
        engine: '',
        size: '',
        language: 'en-US',
        gender: 'Female',
        state: 'installed',
        downloaded: 0,
        total: 0,
        error: null,
      };
      const state = {
        prefs,
        voices: [
          {
            ...base,
            id: `windows_${'a'.repeat(64)}`,
            name: 'Microsoft David',
            family: 'Windows',
            engine: 'Microsoft',
            gender: 'Male',
          },
          {
            ...base,
            id: `windows_${'b'.repeat(64)}`,
            name: 'Microsoft Hazel',
            family: 'Windows',
            engine: 'Microsoft',
            language: 'en-GB',
          },
          {
            ...base,
            id: 'kokoro_af_heart',
            name: 'Heart',
            family: 'Studio',
            engine: 'Kokoro',
            size: '~126 MB',
          },
          {
            ...base,
            id: 'kokoro_bf_emma',
            name: 'Emma',
            family: 'Studio',
            engine: 'Kokoro',
            language: 'en-GB',
            size: '~126 MB',
          },
          {
            ...base,
            id: 'kokoro_af_bella',
            name: 'Bella',
            family: 'Studio',
            engine: 'Kokoro',
            size: '~126 MB',
            state: 'idle',
          },
          {
            ...base,
            id: 'piper_en_us_lessac',
            name: 'Lessac',
            family: 'Lite',
            engine: 'Piper',
            size: '~20 MB',
            gender: 'Male',
            state: 'idle',
          },
        ],
        speech: [] as { voiceId: string; text: string; rate: number }[],
        downloads: [] as string[],
        cancels: 0,
        manage: 0,
        fail: false,
      };
      Reflect.set(window, '__voiceState', state);
      Reflect.set(window, 'isTauri', true);
      Reflect.set(window, '__TAURI_INTERNALS__', {
        metadata: { currentWindow: { label: 'main' }, currentWebview: { label: 'main' } },
        invoke: async (command: string, args: Record<string, unknown> = {}) => {
          if (command === 'get_settings') return structuredClone(state.prefs);
          if (command === 'save_settings') {
            state.prefs = structuredClone(args.settings as Settings);
            return;
          }
          if (command === 'list_voices') return structuredClone(state.voices);
          if (command.startsWith('list_')) return [];
          if (command === 'record_reading_activity') return;
          if (command === 'record_reading_open') return;
          if (command === 'get_source_info') return null;
          if (command === 'cancel_speech') {
            state.cancels++;
            return;
          }
          if (command === 'synthesize') {
            state.speech.push(args as (typeof state.speech)[number]);
            if (state.fail)
              throw new Error(
                'This Windows voice is no longer installed. Refresh Voice Studio and choose another voice.',
              );
            await new Promise((resolve) => setTimeout(resolve, 150));
            return {
              samples: Array.from({ length: 24000 }, (_, n) => Math.sin(n * 0.03) * 0.03),
              sampleRate: 24000,
            };
          }
          if (command === 'download_voice') {
            state.downloads.push(String(args.voiceId));
            state.voices = state.voices.map((voice) =>
              voice.family === 'Studio' ? { ...voice, state: 'installed' } : voice,
            );
            return;
          }
          if (command === 'open_voice_settings') {
            state.manage++;
            return;
          }
          throw new Error(`Unexpected voice command: ${command}`);
        },
      });
    },
    { prefs: { ...defaultSettings, voice: 'kokoro_af_heart' } },
  );
  await page.goto('/#voices');
  await expect(page.getByRole('heading', { name: 'Voice Studio.' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Microsoft David', exact: true })).toBeVisible();
}

test('installed Windows voices can be selected and filtered by source, accent and name', async ({
  page,
}) => {
  await voiceShell(page);
  const windows = page.getByRole('region', { name: 'Windows voices', exact: true });
  await expect(windows.getByText('Installed on this PC', { exact: false })).toHaveCount(2);
  await expect(windows.getByRole('button', { name: /Download/ })).toHaveCount(0);
  await page.getByRole('button', { name: 'Use Microsoft David', exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__voiceState').prefs.voice))
    .toBe(`windows_${'a'.repeat(64)}`);
  await expect(
    page.getByRole('button', { name: 'Use Microsoft David', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: 'Windows voices', exact: true }).click();
  await expect(page.locator('.voice-choice')).toHaveCount(2);
  await page.getByLabel('Search voices', { exact: true }).fill('en-GB');
  await expect(page.locator('.voice-choice h3')).toHaveText(['Microsoft Hazel']);
  await page.screenshot({ path: '.artifacts/voices-windows-desktop.png' });
  await page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('button', { name: 'Library', exact: true })
    .click();
  await page.getByRole('button', { name: 'Go back', exact: true }).click();
  await expect(page.getByLabel('Search voices', { exact: true })).toHaveValue('en-GB');
  await expect(page.getByRole('button', { name: 'Windows voices', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  await page.getByLabel('Search voices', { exact: true }).fill('');
  await page.getByRole('button', { name: 'Manage Windows voices', exact: false }).click();
  expect(await page.evaluate(() => Reflect.get(window, '__voiceState').manage)).toBe(1);
  await page.getByRole('button', { name: 'Refresh voices', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Use Microsoft David', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true');
});

test('voice previews use custom text, stop cleanly and report a missing installed voice', async ({
  page,
}) => {
  await voiceShell(page);
  await page
    .getByLabel('Try your own words', { exact: true })
    .fill('Read this short sample in my chosen voice.');
  await page.getByRole('button', { name: 'Preview Microsoft David', exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__voiceState').speech.length))
    .toBe(1);
  expect(await page.evaluate(() => Reflect.get(window, '__voiceState').speech[0])).toMatchObject({
    voiceId: `windows_${'a'.repeat(64)}`,
    text: 'Read this short sample in my chosen voice.',
    rate: 1,
  });
  await page.getByRole('button', { name: 'Stop preview of Microsoft David', exact: true }).click();
  await expect(
    page.getByRole('button', { name: 'Preview Microsoft David', exact: true }),
  ).toBeVisible();
  await page.evaluate(() => {
    Reflect.get(window, '__voiceState').fail = true;
  });
  await page.getByRole('button', { name: 'Preview Microsoft Hazel', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('no longer installed');
  await page.getByRole('button', { name: 'Dismiss voice error', exact: true }).click();
  await expect(page.getByRole('alert')).not.toBeVisible();
});

test('Studio speakers share a download and the voice browser fits desktop and narrow screens', async ({
  page,
}) => {
  await voiceShell(page);
  await page.getByRole('button', { name: 'Studio voices', exact: true }).click();
  const bella = page
    .locator('.voice-choice')
    .filter({ has: page.getByRole('heading', { name: 'Bella', exact: true }) });
  await bella.getByRole('button', { name: 'Download Studio', exact: true }).click();
  await expect(bella.getByRole('button', { name: 'Use Bella', exact: true })).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, '__voiceState').downloads)).toEqual([
    'kokoro_af_bella',
  ]);
  await page.getByRole('button', { name: 'Use Emma', exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => Reflect.get(window, '__voiceState').prefs.voice))
    .toBe('kokoro_bf_emma');
  for (const width of [1365, 900, 390]) {
    await page.setViewportSize({ width, height: 767 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: `.artifacts/voices-studio-${width}.png` });
  }
});
