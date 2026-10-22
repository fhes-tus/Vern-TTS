import { unzipSync } from 'fflate';
import { DOMParser } from '@xmldom/xmldom';
import type { ParsedBook } from './types';

const LIMIT = 32 * 1024 * 1024;
const decoder = new TextDecoder('utf-8', { fatal: true });
export function chunksFromText(text: string): string[] {
  const normalized = text.replace(/\r\n?/g, '\n').replace(/\0/g, '').normalize('NFC');
  const segmenter = new Intl.Segmenter('en', { granularity: 'sentence' });
  const chunks: string[] = [];
  for (const paragraph of normalized.split(/\n\s*\n/)) {
    for (const sentence of segmenter.segment(paragraph.replace(/\s+/g, ' ').trim())) {
      // Bound native inference latency even when punctuation is absent.
      let rest = sentence.segment.trim();
      while (rest.length > 600) {
        const split = Math.max(rest.lastIndexOf(' ', 600), 1);
        const at = split < 100 ? 600 : split;
        chunks.push(rest.slice(0, at).trim());
        rest = rest.slice(at).trim();
      }
      if (rest) chunks.push(rest);
    }
  }
  if (!chunks.length)
    throw new Error('No readable text found. Scanned PDFs need OCR, which is not available yet.');
  if (chunks.length > 100_000) throw new Error('This book exceeds the 100,000-chunk limit.');
  return chunks;
}

export function xml(source: string) {
  if (/<!DOCTYPE|<!ENTITY/i.test(source)) {
    // EPUB commonly declares a standard XHTML doctype; strip declarations, never resolve entities.
    source = source.replace(/<!DOCTYPE[^>]*(\[[\s\S]*?\]\s*)?>/gi, '');
    if (/<!ENTITY/i.test(source)) throw new Error('EPUB entity declarations are unsupported.');
  }
  return new DOMParser({
    onError: (level, message) => {
      if (level === 'fatalError') throw new Error(message);
    },
  }).parseFromString(source, 'application/xml');
}

export function resolve(base: string, href: string) {
  const parts: string[] = [];
  for (const part of (base + decodeURIComponent(href.split('#')[0])).split('/')) {
    if (part === '..') {
      if (!parts.length) throw new Error('Unsafe EPUB path.');
      parts.pop();
    } else if (part && part !== '.') parts.push(part);
  }
  return parts.join('/');
}

export function archiveXml(bytes: Uint8Array, images = false) {
  let total = 0;
  let count = 0;
  return unzipSync(bytes, {
    filter: (entry) => {
      if (
        ++count > 10_000 ||
        entry.originalSize > LIMIT ||
        (total += entry.originalSize) > 64 * 1024 * 1024
      )
        throw new Error('Document archive expands beyond the safe import limit.');
      if (
        entry.name.startsWith('/') ||
        entry.name.includes('\\') ||
        entry.name.includes(':') ||
        entry.name.includes('\0') ||
        entry.name.split('/').includes('..')
      )
        throw new Error('Unsafe document archive path.');
      return (
        /\.(xml|rels|opf|xhtml|html|htm)$/i.test(entry.name) ||
        (images && /\.(png|jpe?g|gif|webp)$/i.test(entry.name))
      );
    },
  });
}

export function epubText(bytes: Uint8Array) {
  const files = archiveXml(bytes);
  const read = (name: string) => {
    if (!files[name]) throw new Error(`EPUB is missing ${name}.`);
    return decoder.decode(files[name]);
  };
  const container = xml(read('META-INF/container.xml'));
  const opfPath = container.getElementsByTagName('rootfile')[0]?.getAttribute('full-path');
  if (!opfPath) throw new Error('EPUB has no package document.');
  const opf = xml(read(opfPath));
  const base = opfPath.slice(0, opfPath.lastIndexOf('/') + 1);
  const manifest = new Map(
    Array.from(opf.getElementsByTagName('item')).map((item) => [
      item.getAttribute('id'),
      item.getAttribute('href'),
    ]),
  );
  const texts: string[] = [];
  for (const item of Array.from(opf.getElementsByTagName('itemref'))) {
    if (item.getAttribute('linear') === 'no') continue;
    const href = manifest.get(item.getAttribute('idref'));
    if (!href) throw new Error('EPUB spine references a missing chapter.');
    const chapter = xml(read(resolve(base, href)));
    for (const tag of ['script', 'style', 'head']) {
      for (const node of Array.from(chapter.getElementsByTagName(tag)))
        node.parentNode?.removeChild(node);
    }
    // Preserve block boundaries before taking textContent. No publisher HTML is rendered.
    for (const node of Array.from(chapter.getElementsByTagName('*'))) {
      if (/^(p|h[1-6]|div|br|li|blockquote)$/i.test(node.localName ?? ''))
        node.appendChild(chapter.createTextNode('\n\n'));
    }
    texts.push(chapter.documentElement?.textContent ?? '');
  }
  return {
    title: opf.getElementsByTagNameNS('*', 'title')[0]?.textContent?.trim() ?? '',
    author: opf.getElementsByTagNameNS('*', 'creator')[0]?.textContent?.trim() ?? '',
    text: texts.join('\n\n'),
    pages: texts,
  };
}

export function officeText(bytes: Uint8Array, format: 'docx' | 'pptx') {
  const files = archiveXml(bytes);
  const read = (path: string) => {
    if (!files[path]) throw new Error(`Document is missing ${path}.`);
    return xml(decoder.decode(files[path]));
  };
  const paragraphs = (path: string, namespace: string) => {
    const doc = read(path);
    // Preserve run adjacency, explicit tabs/breaks and paragraph boundaries.
    return Array.from(doc.getElementsByTagNameNS(namespace, 'p'))
      .map((p) => {
        return Array.from(p.getElementsByTagName('*'))
          .map((node) => {
            if (node.namespaceURI !== namespace) return '';
            if (node.localName === 't') return node.textContent ?? '';
            if (node.localName === 'tab') return '\t';
            if (node.localName === 'br' || node.localName === 'cr') return '\n';
            return '';
          })
          .join('');
      })
      .filter((p) => p.trim())
      .join('\n\n');
  };
  const properties = files['docProps/core.xml'] ? read('docProps/core.xml') : null;
  const metadata = {
    title: properties?.getElementsByTagNameNS('*', 'title')[0]?.textContent?.trim() ?? '',
    author: properties?.getElementsByTagNameNS('*', 'creator')[0]?.textContent?.trim() ?? '',
  };
  if (format === 'docx')
    return {
      ...metadata,
      text: paragraphs(
        'word/document.xml',
        'http://schemas.openxmlformats.org/wordprocessingml/2006/main',
      ),
      pages: [
        paragraphs(
          'word/document.xml',
          'http://schemas.openxmlformats.org/wordprocessingml/2006/main',
        ),
      ],
    };
  const presentation = read('ppt/presentation.xml');
  const relationships = read('ppt/_rels/presentation.xml.rels');
  const targets = new Map(
    Array.from(relationships.getElementsByTagNameNS('*', 'Relationship'))
      .filter((r) => r.getAttribute('TargetMode') !== 'External')
      .map((r) => [r.getAttribute('Id'), r.getAttribute('Target')]),
  );
  const slideIds = Array.from(presentation.getElementsByTagNameNS('*', 'sldId'));
  if (slideIds.length > 3000) throw new Error('Presentation exceeds the 3,000-slide import limit.');
  const slides = slideIds.map((slide) => {
    const id = slide.getAttributeNS(
      'http://schemas.openxmlformats.org/officeDocument/2006/relationships',
      'id',
    );
    const target = targets.get(id);
    if (!target) throw new Error('Presentation references a missing or external slide.');
    const path = target.startsWith('/') ? resolve('', target.slice(1)) : resolve('ppt/', target);
    return paragraphs(path, 'http://schemas.openxmlformats.org/drawingml/2006/main');
  });
  return { ...metadata, text: slides.join('\n\n'), pages: slides };
}

export async function parseBook(name: string, bytes: Uint8Array): Promise<ParsedBook> {
  if (bytes.length > LIMIT) throw new Error('Please choose a book smaller than 32 MB.');
  const format = name.split('.').pop()?.toLowerCase() ?? '';
  let title = name.replace(/\.[^.]+$/, '').replace(/_/g, ' ');
  let author = '';
  let text: string;
  let sourcePages: string[] = [];
  if (format === 'epub') {
    const epub = epubText(bytes);
    text = epub.text;
    title = epub.title || title;
    author = epub.author;
    sourcePages = epub.pages;
  } else if (format === 'docx' || format === 'pptx') {
    const document = officeText(bytes, format);
    text = document.text;
    title = document.title || title;
    author = document.author;
    sourcePages = document.pages;
  } else if (format === 'pdf') {
    const pdfjs = await import('pdfjs-dist');
    pdfjs.GlobalWorkerOptions.workerSrc = new URL(
      'pdfjs-dist/build/pdf.worker.min.mjs',
      import.meta.url,
    ).href;
    const task = pdfjs.getDocument({ data: bytes, useSystemFonts: true });
    const pdf = await task.promise;
    try {
      if (pdf.numPages > 3000) throw new Error('PDF exceeds the 3,000-page import limit.');
      const pages: string[] = [];
      let chars = 0;
      for (let n = 1; n <= pdf.numPages; n++) {
        const content = await (await pdf.getPage(n)).getTextContent();
        const page = content.items
          .map((item) => ('str' in item ? item.str + (item.hasEOL ? '\n' : ' ') : ''))
          .join('');
        chars += page.length;
        if (chars > LIMIT) throw new Error('Extracted PDF text exceeds 32 MB.');
        pages.push(page);
      }
      text = pages.join('\n\n');
      sourcePages = pages;
    } finally {
      await task.destroy();
    }
  } else if (['txt', 'md', 'log'].includes(format)) {
    const utf16 =
      bytes[0] === 0xff && bytes[1] === 0xfe
        ? 'utf-16le'
        : bytes[0] === 0xfe && bytes[1] === 0xff
          ? 'utf-16be'
          : 'utf-8';
    text = new TextDecoder(utf16, { fatal: true }).decode(bytes);
  } else
    throw new Error(
      'Choose TXT, Markdown, EPUB, PDF, DOCX or PPTX. Other formats are not supported yet.',
    );
  if (new TextEncoder().encode(text).length > LIMIT)
    throw new Error('Extracted document text exceeds 32 MB.');
  const textAvailable = !!text.trim();
  const chunks =
    !textAvailable && format === 'pdf'
      ? sourcePages.map((_, n) => `Page ${n + 1} — no extracted text`)
      : chunksFromText(text);
  let offset = 0;
  const sourcePageStarts = sourcePages.map((page, n) => {
    const start = textAvailable ? offset : n;
    if (page.trim()) offset += chunksFromText(page).length;
    return Math.min(chunks.length - 1, start);
  });
  return {
    title,
    author,
    format: format.toUpperCase(),
    chunks,
    textAvailable,
    sourcePageStarts,
    position: 0,
    favorite: false,
  };
}
