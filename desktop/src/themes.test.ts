import { expect, test } from 'vitest';
import { contrastRatio, paletteData, readableAccent } from './themes';
test('accent labels remain readable across themes and custom light/dark accents', () => {
  for (const palette of Object.values(paletteData))
    for (const background of [palette.background, palette.surface, palette.surfaceVariant]) {
      for (const accent of [palette.primary, '#001166', '#ffffcc', '#777777']) {
        expect(
          contrastRatio(readableAccent(accent, background), background),
        ).toBeGreaterThanOrEqual(4.5);
      }
    }
  expect(readableAccent('#ffffff', '#000000')).toBe('#ffffff');
});
