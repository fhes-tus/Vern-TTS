import { archiveXml, epubText, resolve, xml } from './import';
import type { DocumentBlock, DocumentPage, DocumentRun, SourceDocument } from './document-model';
import type { Element as XmlElement } from '@xmldom/xmldom';

const decode = new TextDecoder('utf-8', { fatal: true });
const elements = (node: XmlElement, name: string) =>
  Array.from(node.getElementsByTagName('*')).filter((e) => e.localName === name);
const value = (element: XmlElement | undefined, name: string) =>
  element?.getAttribute(name) ?? element?.getAttribute(`w:${name}`) ?? '';
const paragraphs = (node: XmlElement) =>
  elements(node, 'p')
    .map((p) =>
      elements(p, 't')
        .map((t) => t.textContent ?? '')
        .join(''),
    )
    .filter(Boolean);
const color = (v: string) => (/^[a-f\d]{6}$/i.test(v) ? `#${v}` : undefined);

export function parseSourceDocument(name: string, bytes: Uint8Array): SourceDocument {
  if (bytes.length > 32 * 1024 * 1024) throw new Error('Original document exceeds 32 MB.');
  const format = name.split('.').pop()?.toLowerCase();
  if (['txt', 'md', 'log'].includes(format ?? '')) {
    const encoding =
      bytes[0] === 255 && bytes[1] === 254
        ? 'utf-16le'
        : bytes[0] === 254 && bytes[1] === 255
          ? 'utf-16be'
          : 'utf-8';
    const text = new TextDecoder(encoding, { fatal: true }).decode(bytes);
    const paragraphs = text.split(/\n\s*\n/).filter((p) => p.trim());
    const pages: DocumentPage[] = [];
    let blocks: DocumentBlock[] = [];
    let length = 0;
    for (const paragraph of paragraphs) {
      if (length > 1800) {
        pages.push({
          width: 816,
          height: 1056,
          fixed: false,
          blocks,
          text: blocks.map((b) => b.text).join('\n\n'),
        });
        blocks = [];
        length = 0;
      }
      blocks.push({ kind: 'paragraph', text: paragraph });
      length += paragraph.length;
    }
    if (blocks.length)
      pages.push({
        width: 816,
        height: 1056,
        fixed: false,
        blocks,
        text: blocks.map((b) => b.text).join('\n\n'),
      });
    return {
      pages,
      notice: 'Original plain text. Use Reading view for responsive text pagination.',
    };
  }
  const files = archiveXml(bytes, true);
  const read = (path: string) => {
    if (!files[path]) throw new Error(`Original document is missing ${path}.`);
    return xml(decode.decode(files[path]));
  };
  let imageBytes = 0;
  const imageCache = new Map<string, string>();
  const image = (path: string) => {
    if (imageCache.has(path)) return imageCache.get(path);
    const data = files[path];
    const ext = path.split('.').pop()?.toLowerCase();
    if (
      !data ||
      !['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext ?? '') ||
      data.length > 8 * 1024 * 1024
    )
      return undefined;
    imageBytes += data.length;
    if (imageBytes > 24 * 1024 * 1024 || imageCache.size >= 300)
      throw new Error('Embedded images exceed the document view limit.');
    let binary = '';
    for (let i = 0; i < data.length; i += 16384)
      binary += String.fromCharCode(...data.subarray(i, i + 16384));
    const url = `data:image/${ext === 'jpg' ? 'jpeg' : ext};base64,${btoa(binary)}`;
    imageCache.set(path, url);
    return url;
  };
  const relationships = (path: string, base: string) => {
    const map = new Map<string, string>();
    if (!files[path]) return map;
    for (const relation of Array.from(read(path).getElementsByTagNameNS('*', 'Relationship'))) {
      if (relation.getAttribute('TargetMode') === 'External') continue;
      const target = relation.getAttribute('Target');
      const id = relation.getAttribute('Id');
      if (target && id)
        map.set(id, target.startsWith('/') ? resolve('', target.slice(1)) : resolve(base, target));
    }
    return map;
  };
  const plainPage = (
    blocks: DocumentBlock[],
    width = 816,
    height = 1056,
    fixed = false,
  ): DocumentPage => ({
    width,
    height,
    blocks,
    fixed,
    text: blocks.map((b) => b.text).join('\n\n'),
  });
  if (format === 'docx') {
    const doc = read('word/document.xml');
    const body = doc.getElementsByTagNameNS('*', 'body')[0];
    if (!body) throw new Error('Word document has no body.');
    const rels = relationships('word/_rels/document.xml.rels', 'word/');
    const size = doc.getElementsByTagNameNS('*', 'pgSz')[0];
    const width = Math.min(2000, Math.max(400, Number(value(size, 'w')) / 15 || 816));
    const height = Math.min(3000, Math.max(400, Number(value(size, 'h')) / 15 || 1056));
    const pages: DocumentPage[] = [];
    let blocks: DocumentBlock[] = [];
    let used = 80;
    const push = () => {
      if (blocks.length) {
        pages.push(plainPage(blocks, width, height));
        blocks = [];
        used = 80;
      }
    };
    for (const node of Array.from(body.childNodes).filter(
      (n): n is XmlElement => n.nodeType === 1,
    )) {
      if (node.localName === 'p') {
        if (elements(node, 'pageBreakBefore').length) push();
        const runs: DocumentRun[] = [];
        for (const run of elements(node, 'r')) {
          let text = '';
          for (const part of Array.from(run.childNodes).filter(
            (n): n is XmlElement => n.nodeType === 1,
          )) {
            if (part.localName === 't') text += part.textContent ?? '';
            if (part.localName === 'tab') text += '\t';
            if (part.localName === 'br' && value(part, 'type') !== 'page') text += '\n';
          }
          if (text)
            runs.push({
              text,
              bold: elements(run, 'b').some((e) => value(e, 'val') !== '0'),
              italic: elements(run, 'i').some((e) => value(e, 'val') !== '0'),
              size: Math.max(
                8,
                Math.min(64, Number(value(elements(run, 'sz')[0], 'val')) / 1.5 || 16),
              ),
              color: color(value(elements(run, 'color')[0], 'val')),
            });
        }
        const text = runs.map((r) => r.text).join('');
        const level =
          Number(value(elements(node, 'pStyle')[0], 'val').match(/heading(\d)/i)?.[1]) || 0;
        const alignment = value(elements(node, 'jc')[0], 'val');
        const align =
          alignment === 'both'
            ? 'justify'
            : ['left', 'center', 'right', 'justify'].includes(alignment)
              ? (alignment as DocumentBlock['align'])
              : 'left';
        const estimated =
          Math.ceil(text.length / Math.max(20, (width - 110) / 8)) * (level ? 34 : 25) + 16;
        if (used + estimated > height - 65) push();
        if (text) {
          blocks.push({ kind: 'paragraph', text, runs, level, align });
          used += estimated;
        }
        for (const blip of elements(node, 'blip')) {
          const path = rels.get(blip.getAttribute('r:embed') ?? '');
          const src = path ? image(path) : undefined;
          if (src) {
            if (used + 280 > height - 65) push();
            blocks.push({ kind: 'image', text: '', src });
            used += 280;
          }
        }
        if (
          elements(node, 'br').some((e) => value(e, 'type') === 'page') ||
          elements(node, 'lastRenderedPageBreak').length
        )
          push();
      } else if (node.localName === 'tbl') {
        const cells = elements(node, 'tr').map((row) =>
          Array.from(row.childNodes)
            .filter(
              (n): n is XmlElement => n.nodeType === 1 && (n as XmlElement).localName === 'tc',
            )
            .map((cell) => paragraphs(cell).join('\n')),
        );
        if (used + cells.length * 45 > height - 65) push();
        blocks.push({ kind: 'table', text: cells.flat().join('\n\n'), cells });
        used += cells.length * 45;
      }
    }
    push();
    return {
      pages,
      notice:
        'Word content, tables and images. Page breaks and fonts may differ from Microsoft Word.',
    };
  }
  if (format === 'pptx') {
    const presentation = read('ppt/presentation.xml');
    const size = presentation.getElementsByTagNameNS('*', 'sldSz')[0];
    const widthEmu = Number(size?.getAttribute('cx')) || 9144000;
    const heightEmu = Number(size?.getAttribute('cy')) || 5143500;
    const width = 960;
    const height = (width * heightEmu) / widthEmu;
    const rels = relationships('ppt/_rels/presentation.xml.rels', 'ppt/');
    const pages = Array.from(presentation.getElementsByTagNameNS('*', 'sldId')).map((entry) => {
      const path = rels.get(entry.getAttribute('r:id') ?? '');
      if (!path) throw new Error('Missing slide relationship.');
      const doc = read(path);
      const root = doc.documentElement!;
      const base = path.slice(0, path.lastIndexOf('/') + 1);
      const filename = path.slice(path.lastIndexOf('/') + 1);
      const media = relationships(`${base}_rels/${filename}.rels`, base);
      const blocks: DocumentBlock[] = [];
      const shapes = elements(root, 'spTree')[0];
      for (const shape of shapes
        ? Array.from(shapes.childNodes).filter((n): n is XmlElement => n.nodeType === 1)
        : []) {
        const transform = elements(shape, 'xfrm')[0];
        const off = transform ? elements(transform, 'off')[0] : undefined;
        const ext = transform ? elements(transform, 'ext')[0] : undefined;
        const box = {
          x: (Number(off?.getAttribute('x') ?? 0) / widthEmu) * width,
          y: (Number(off?.getAttribute('y') ?? 0) / heightEmu) * height,
          width: Math.max(20, (Number(ext?.getAttribute('cx') ?? widthEmu) / widthEmu) * width),
          height: Math.max(
            20,
            (Number(ext?.getAttribute('cy') ?? heightEmu / 5) / heightEmu) * height,
          ),
        };
        const text = paragraphs(shape).join('\n');
        const font = elements(shape, 'rPr')[0];
        if (text)
          blocks.push({
            kind: 'paragraph',
            text,
            box,
            runs: [
              {
                text,
                size: Math.min(96, Math.max(10, Number(font?.getAttribute('sz')) / 75 || 24)),
                bold: font?.getAttribute('b') === '1',
                italic: font?.getAttribute('i') === '1',
              },
            ],
          });
        for (const blip of elements(shape, 'blip')) {
          const file = media.get(blip.getAttribute('r:embed') ?? '');
          const src = file ? image(file) : undefined;
          if (src) blocks.push({ kind: 'image', text: '', src, box });
        }
      }
      return plainPage(blocks, width, height, true);
    });
    if (pages.length > 3000) throw new Error('Presentation exceeds 3,000 slides.');
    return {
      pages,
      notice:
        'Slide text and embedded images in source positions. Themes, grouped shapes and transitions may differ from PowerPoint.',
    };
  }
  if (format === 'epub') {
    const container = read('META-INF/container.xml');
    const opfPath = container.getElementsByTagNameNS('*', 'rootfile')[0]?.getAttribute('full-path');
    if (!opfPath) throw new Error('EPUB has no package.');
    const opf = read(opfPath);
    const base = opfPath.slice(0, opfPath.lastIndexOf('/') + 1);
    const manifest = new Map(
      Array.from(opf.getElementsByTagNameNS('*', 'item')).map((e) => [
        e.getAttribute('id'),
        e.getAttribute('href'),
      ]),
    );
    const pages = Array.from(opf.getElementsByTagNameNS('*', 'itemref'))
      .filter((e) => e.getAttribute('linear') !== 'no')
      .map((entry) => {
        const href = manifest.get(entry.getAttribute('idref'));
        if (!href) throw new Error('Missing EPUB section.');
        const path = resolve(base, href);
        const chapter = read(path);
        const blocks: DocumentBlock[] = [];
        const chapterBase = path.slice(0, path.lastIndexOf('/') + 1);
        for (const node of Array.from(chapter.getElementsByTagName('*'))) {
          if (
            ['script', 'style', 'head', 'iframe', 'object'].some((tag) => {
              let p = node.parentNode;
              while (p) {
                if ('localName' in p && p.localName === tag) return true;
                p = p.parentNode;
              }
              return false;
            })
          )
            continue;
          if (/^(p|h[1-6]|li|blockquote)$/i.test(node.localName ?? ''))
            blocks.push({
              kind: 'paragraph',
              text: node.textContent ?? '',
              level: /^h[1-6]$/.test(node.localName ?? '') ? Number(node.localName?.slice(1)) : 0,
            });
          if (node.localName === 'img') {
            const srcPath = node.getAttribute('src');
            if (srcPath && !/^(?:[a-z]+:|\/\/)/i.test(srcPath)) {
              const src = image(resolve(chapterBase, srcPath));
              if (src) blocks.push({ kind: 'image', text: node.getAttribute('alt') ?? '', src });
            }
          }
        }
        return plainPage(blocks);
      });
    if (!pages.length) {
      const text = epubText(bytes).text;
      pages.push(plainPage([{ kind: 'paragraph', text }]));
    }
    return {
      pages,
      notice:
        'EPUB sections and embedded images. Publisher scripts and external resources are disabled; text reflows to the page.',
    };
  }
  throw new Error(
    'Original view supports PDF, EPUB, DOCX and PPTX. Use Reading view for plain text.',
  );
}
