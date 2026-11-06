import { useEffect } from 'react';
import palettes from './data/palettes.json';
import type { Settings } from './types';

export const themeFamilies = [
  { id: 'sage', name: 'Sage', light: 'light', dark: 'dark' },
  { id: 'midnight', name: 'Midnight', light: 'midnight_light', dark: 'midnight_dark' },
  { id: 'dracula', name: 'Dracula', light: 'dracula_light', dark: 'dracula' },
  { id: 'github', name: 'GitHub', light: 'github_light', dark: 'github_dark' },
  { id: 'bw_gradient', name: 'B/W Gradient', light: 'bw_gradient_light', dark: 'bw_gradient_dark' },
  { id: 'neon', name: 'Neon', light: 'neon', dark: 'neon' },
];
export const extraPalettes = [
  ['amoled', 'AMOLED black'],
  ['dark_high_contrast', 'Dark high contrast'],
  ['white_high_contrast', 'White high contrast'],
  ['solarized_dark', 'Solarized dark'],
  ['tomorrow_night_blue', 'Tomorrow Night Blue'],
];
type Palette = {
  dark: boolean;
  primary: string;
  background: string;
  surface: string;
  onSurface: string;
  onSurfaceVariant: string;
  surfaceVariant: string;
  primaryContainer: string;
  tertiary: string;
  surfaceContainerLow?: string;
  outlineVariant?: string;
  outline?: string;
};
export const paletteData = palettes as Record<string, Palette>;
export function paletteFor(settings: Pick<Settings, 'theme' | 'colorTheme'>, systemDark: boolean) {
  const family = themeFamilies.find((f) => f.id === settings.colorTheme);
  const dark = settings.theme === 'dark' || (settings.theme === 'system' && systemDark);
  return (
    paletteData[family ? (dark ? family.dark : family.light) : settings.colorTheme] ??
    paletteData[dark ? 'dark' : 'light']
  );
}
export function contrastInk(hex: string) {
  const rgb = hex
    .slice(1)
    .match(/../g)!
    .map((value) => {
      const c = parseInt(value, 16) / 255;
      return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
  const luminance = rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722;
  return (luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05) ? '#000000' : '#ffffff';
}
function rgb(hex: string) {
  return [1, 3, 5].map((index) => parseInt(hex.slice(index, index + 2), 16));
}
export function contrastRatio(a: string, b: string) {
  const luminance = (hex: string) =>
    rgb(hex)
      .map((value) => {
        const c = value / 255;
        return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
      })
      .reduce((sum, c, index) => sum + c * [0.2126, 0.7152, 0.0722][index], 0);
  const x = luminance(a),
    y = luminance(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}
// Preserve the chosen button colour while making small accent text readable on each surface.
export function readableAccent(accent: string, background: string) {
  if (contrastRatio(accent, background) >= 4.5) return accent;
  const source = rgb(accent),
    target = rgb(contrastInk(background));
  for (let step = 1; step <= 100; step++) {
    const mixed =
      '#' +
      source
        .map((value, index) =>
          Math.round(value + ((target[index] - value) * step) / 100)
            .toString(16)
            .padStart(2, '0'),
        )
        .join('');
    if (contrastRatio(mixed, background) >= 4.5) return mixed;
  }
  return contrastInk(background);
}
export function useTheme(settings: Settings) {
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    function apply() {
      const root = document.documentElement;
      const p = paletteFor(settings, media.matches);
      const accent = settings.accentColor || p.primary;
      root.dataset.theme = p.dark ? 'dark' : 'light';
      root.dataset.palette = settings.colorTheme;
      root.dataset.reduceMotion = String(settings.reduceMotion);
      root.dataset.reduceTransparency = String(settings.reduceTransparency);
      root.style.colorScheme = p.dark ? 'dark' : 'light';
      const tokens = {
        page: p.background,
        'page-background':
          settings.colorTheme === 'bw_gradient'
            ? `linear-gradient(135deg, ${p.background}, ${p.surfaceVariant})`
            : p.background,
        surface: p.surface,
        low: p.surfaceContainerLow ?? p.background,
        container: p.surfaceVariant,
        line: p.outlineVariant ?? p.outline ?? p.surfaceVariant,
        ink: p.onSurface,
        muted: p.onSurfaceVariant,
        accent,
        'accent-strong': accent,
        'accent-text': readableAccent(accent, p.background),
        'accent-on-surface': readableAccent(accent, p.surface),
        'accent-on-container': readableAccent(accent, p.surfaceVariant),
        'on-accent': contrastInk(accent),
        sage: p.tertiary,
        'accent-container': settings.accentColor
          ? `color-mix(in srgb, ${accent} 17%, ${p.surface})`
          : p.primaryContainer,
        highlight: settings.accentColor
          ? `color-mix(in srgb, ${accent} 17%, ${p.surface})`
          : p.primaryContainer,
      };
      for (const [key, value] of Object.entries(tokens)) root.style.setProperty(`--${key}`, value);
    }
    apply();
    media.addEventListener?.('change', apply);
    return () => media.removeEventListener?.('change', apply);
  }, [
    settings.theme,
    settings.colorTheme,
    settings.accentColor,
    settings.reduceMotion,
    settings.reduceTransparency,
  ]);
}
