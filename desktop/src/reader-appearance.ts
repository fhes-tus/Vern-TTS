import type { Settings } from './types';
export const readerFonts = [
  ['System', 'System default'],
  ['Bitter', 'Gazette · Bitter'],
  ['Outfit', 'Crisp · Outfit'],
  ['Times New Roman', 'Typewriter · Times'],
  ['Atkinson', 'Atkinson Hyperlegible'],
  ['Georgia', 'Classic serif'],
] as const;
export function readerFontFamily(font: string) {
  return font === 'System'
    ? 'system-ui'
    : font === 'Times New Roman'
      ? '"Times New Roman", Times, serif'
      : `"${font}"`;
}
export function readerPaperStyle(settings: Settings) {
  const tones = {
    white: ['#fff', '#20201e'],
    sepia: ['#f5ead6', '#352c23'],
    dark: ['#20211f', '#eee9df'],
  };
  const tone = settings.paperTone === 'theme' ? null : tones[settings.paperTone];
  return {
    fontSize: settings.fontSize,
    fontFamily: readerFontFamily(settings.readerFont),
    '--reader-leading': settings.lineSpacing,
    ...(tone
      ? {
          backgroundColor: tone[0],
          color: tone[1],
          '--highlight': settings.paperTone === 'dark' ? '#4b4436' : '#ebdab4',
        }
      : {}),
  };
}
