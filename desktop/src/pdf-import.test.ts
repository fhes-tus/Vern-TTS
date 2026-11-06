import { expect, it } from 'vitest';
import { cleanPdfPages } from './pdf-import';
import { defaultSettings } from './types';
it('applies PDF ranges and independent cleanup choices without changing original page indices', () => {
  const pages = [
    'A book\nA hyphen-\nated word.\n1',
    'A book\nAnother passage.\n2',
    'A book\nThe final passage.\n3',
  ];
  expect(cleanPdfPages(pages, defaultSettings.pdfImport)).toEqual([
    'A hyphenated word.',
    'Another passage.',
    'The final passage.',
  ]);
  expect(cleanPdfPages(pages, { ...defaultSettings.pdfImport, startPage: 2, endPage: 2 })).toEqual([
    '',
    'A book\nAnother passage.',
    '',
  ]);
  expect(
    cleanPdfPages(pages, {
      ...defaultSettings.pdfImport,
      cleanupRepeatedLines: false,
      removePageNumbers: false,
      repairHyphenation: false,
    }),
  ).toEqual(pages);
  expect(() => cleanPdfPages(pages, { ...defaultSettings.pdfImport, startPage: 4 })).toThrow(
    'outside this document',
  );
});
