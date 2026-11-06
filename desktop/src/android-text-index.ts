// Android ReaderTextModel.kt / LiteraryDialogue.kt boundary rules. UTF-16 indices
// match Kotlin; native code still validates every converted reading and anchor.
export type AndroidIndexInput = {
  text: string;
  pageCount: number;
  sentenceIndexVersion: number;
  expectedChunks: number;
};
export type AndroidIndex = {
  chunks: string[];
  sourceToChunk: number[];
  sourcePages: number[];
  pageStarts: number[];
  sourceCount: number;
  warnings: string[];
};
const isSpace = (c: string | undefined) => {
  if (!c) return false;
  const n = c.charCodeAt(0);
  return (
    (n >= 9 && n <= 13) ||
    (n >= 28 && n <= 32) ||
    n === 0xa0 ||
    n === 0x1680 ||
    (n >= 0x2000 && n <= 0x200a) ||
    n === 0x2028 ||
    n === 0x2029 ||
    n === 0x202f ||
    n === 0x205f ||
    n === 0x3000
  );
};
const isLetter = (c: string | undefined) => !!c && /\p{L}/u.test(c);
const isDigit = (c: string | undefined) => !!c && /\p{Nd}/u.test(c);
const isUpper = (c: string | undefined) => !!c && /\p{Uppercase}/u.test(c);
const isLower = (c: string | undefined) => !!c && /\p{Lowercase}/u.test(c);
const trim = (s: string) => {
  let a = 0,
    b = s.length;
  while (a < b && isSpace(s[a])) a++;
  while (b > a && isSpace(s[b - 1])) b--;
  return s.slice(a, b);
};
const trimEnd = (s: string) => {
  let n = s.length;
  while (n > 0 && isSpace(s[n - 1])) n--;
  return s.slice(0, n);
};
const ABBREVIATIONS = new Set(
  'mr mrs ms dr prof sr jr st v vs etc e.g i.e fig figs no nos vol pp p ch dept univ inc ltd et al ed eds ref refs sec secs para paras eq eqs app approx c ca cf ff op cit ibid'.split(
    ' ',
  ),
);
const ALIASES: Record<string, string> = {
  chris: 'Christian',
  chr: 'Christian',
  evan: 'Evangelist',
  ev: 'Evangelist',
  hope: 'Hopeful',
  faith: 'Faithful',
  ignor: 'Ignorance',
  inter: 'Interpreter',
  talk: 'Talkative',
  pli: 'Pliable',
  obst: 'Obstinate',
  byends: 'By-ends',
  'by-ends': 'By-ends',
};
const PROSE_LABELS = new Set(
  'chapter part section note notes figure table warning summary answer question'.split(' '),
);
const CHAPTERS = new Set(
  'chapter prologue epilogue introduction preface part book section act scene'.split(' '),
);
const RUNNING_VERBS = new Set(
  'updates explores describes examines focuses covers discusses presents analyzes reviews addresses investigates illustrates shows demonstrates argues explains details is was are were will has have had contains provides'.split(
    ' ',
  ),
);
const BULLET = /^(\d{1,4}[.)]|\([a-zA-Z0-9]+\)|[a-zA-Z][.)]|[IVXLCDM]{1,6}[.)])[ \t\n\r\f\v]+/i;
const NUMBERED_PREFIX = /^(?:#+[ \t\n\r\f\v]*)?(?:(?:[0-9]+\.)*[0-9]+|[IVXLCDM]+)$/i;
const INITIALS = /^([a-z]\.){2,}[a-z]?$/;
const CLOSERS = '!?.”’"\')]}»';
const MARKS = '.!?';
const PAGE =
  /^[ \t\n\r\f\v]*#?[ \t\n\r\f\v]*\[\[?\{?VERITAS[_ \t\n\r\f\v]PAGE:?[ \t\n\r\f\v]*(\d+)\}?\]\]?[ \t\n\r\f\v]*$/i;
const INLINE_PAGE =
  /#?[ \t\n\r\f\v]*\[\[?\{?VERITAS[_ \t\n\r\f\v]PAGE:?[ \t\n\r\f\v]*\d+\}?\]\]?/gi;
const NAME = "[\\p{Lu}][\\p{L}’'-]*(?:\\s+[\\p{Lu}][\\p{L}’'-]*){0,2}";
const VERBS =
  'said|asked|replied|answered|whispered|shouted|cried|murmured|continued|responded|exclaimed|yelled|muttered|insisted|warned|demanded|explained|added|remarked|grunted|sighed|urged|begged|groaned|screamed|barked|snapped|called|cries|says|asks|replies|answers';
const ATTRIBUTION = new RegExp(
  '^(?:' + NAME + ')\\s+(?:' + VERBS + ')\\b|^(?:' + VERBS + ')\\s+(?:' + NAME + ')\\b',
  'iu',
);
function speakerLabel(s: string): boolean {
  if (/^\s*(Chris|Chr|Evan|Ev|Hope|Faith|Ignor|Inter|Talk|Pli|Obst|Byends|By-ends)\.\s+/i.test(s))
    return true;
  const found = /^\s*([\p{Lu}][\p{L}’'-]*(?:[ -][\p{Lu}][\p{L}’'-]*){0,2}):\s*/u.exec(s);
  if (!found || PROSE_LABELS.has(found[1].toLowerCase())) return false;
  const name = found[1].replace(/^(?:Then|And|But|So|Now)\s+/i, '');
  const canonical = Object.hasOwn(ALIASES, name.toLowerCase()) ? ALIASES[name.toLowerCase()] : name;
  return (
    '“"‘\''.includes(s.slice(found[0].length).trimStart()[0] || '\0') ||
    Object.values(ALIASES).includes(canonical)
  );
}
function toc(line: string): boolean {
  const s = trim(line);
  if (s.length < 3 || !isDigit(s.at(-1))) return false;
  let i = s.length - 1,
    digits = 0,
    spaces = 0,
    leaders = 0;
  while (i >= 0 && isDigit(s[i])) {
    digits++;
    i--;
  }
  if (digits > 5 || i < 0) return false;
  while (i >= 0 && (s[i] === ' ' || s[i] === '\t')) {
    spaces += s[i] === '\t' ? 4 : 1;
    i--;
  }
  if (i < 0) return false;
  while (i >= 0 && ('.·•…-_'.includes(s[i]) || s[i] === ' ')) {
    if (s[i] !== ' ') leaders++;
    i--;
  }
  if (leaders >= 2 || spaces >= 2) return i >= 0 && /\p{L}/u.test(s.slice(0, i + 1));
  const space = s.indexOf(' ');
  return (
    isDigit(s[0]) &&
    space >= 2 &&
    space <= 15 &&
    s.slice(0, space).includes('.') &&
    /^[\p{Nd}.]+$/u.test(s.slice(0, space))
  );
}
function chapter(line: string): boolean {
  const s = trim(line),
    words = s.split(/[ \t\n\r\f\v]+/).filter(Boolean);
  if (s.length < 3 || s.length > 70 || !CHAPTERS.has((words[0] || '').toLowerCase())) return false;
  if (words.length <= 2) return true;
  if (RUNNING_VERBS.has(words[2].toLowerCase()) || isLower(words[2][0])) return false;
  return !/[,;?]$/.test(s);
}
class Boundaries {
  private remaining = 80_000_000;
  private lines: number[] = [-1];
  constructor(readonly text: string) {
    for (let i = 0; i < text.length; i++) if (text[i] === '\n') this.lines.push(i);
    this.lines.push(text.length);
  }
  step(n = 1) {
    this.remaining -= n;
    if (this.remaining < 0) throw new Error('The backup text is too complex to index safely.');
  }
  lineAt(i: number): [number, number] {
    let a = 0,
      b = this.lines.length - 1;
    while (a + 1 < b) {
      const m = (a + b) >>> 1;
      if (this.lines[m] < i) a = m;
      else b = m;
    }
    return [this.lines[a] + 1, this.lines[b]];
  }
  next(i: number): number | undefined {
    for (let n = i + 1; n < this.text.length; n++) {
      this.step();
      if (!isSpace(this.text[n])) return n;
    }
  }
  previous(i: number): string | undefined {
    for (let n = Math.max(i - 1, 0); n >= 0; n--) {
      this.step();
      if (!isSpace(this.text[n])) return this.text[n];
    }
  }
  sentence(i: number, legacy: boolean): boolean {
    const s = this.text,
      mark = s[i];
    this.step();
    if (legacy) {
      if (mark === '.') {
        if (s[i - 1] === '.' || s[i + 1] === '.') return false;
        if ('.…•·'.includes(this.previous(i) || '\0')) return false;
        const remaining = trim(s.slice(i + 1, this.lineAt(i)[1]));
        if (remaining.length >= 1 && remaining.length <= 5 && /^\p{Nd}+$/u.test(remaining))
          return false;
      }
      const next = this.next(i);
      if (next === undefined) return true;
      if (!isUpper(s[next]) && !isDigit(s[next]) && !'"\'\u0060('.includes(s[next])) return false;
      if (isDigit(s[i - 1]) && isDigit(s[i + 1])) return false;
      const token = this.token(i);
      if (
        ABBREVIATIONS.has(token) ||
        (token.length === 1 && isLetter(token[0])) ||
        INITIALS.test(token)
      )
        return false;
      if (token && (/^[\p{Nd}.]+$/u.test(token) || /^[IVXLCDM]+$/i.test(token))) {
        const before = s
          .slice(0, i - token.length)
          .replace(/[ \t]+$/, '')
          .replace(/^[ \t#]+|[ \t#]+$/g, '');
        if (!before || /[\n•*-]$/.test(before) || /(chapter|part|section|book)$/i.test(before))
          return false;
      }
      return true;
    }
    if (mark === '.' && isDigit(s[i - 1]) && isDigit(s[i + 1])) return false;
    let end = i + 1;
    while (end < s.length && CLOSERS.includes(s[end])) {
      this.step();
      end++;
    }
    if (end < s.length && !isSpace(s[end])) return false;
    const next = this.next(end - 1);
    if (next === undefined) return true;
    if (mark === '.') {
      const token = this.token(i);
      if (
        ABBREVIATIONS.has(token) ||
        Object.hasOwn(ALIASES, token) ||
        (token.length === 1 && isLetter(token[0])) ||
        INITIALS.test(token)
      )
        return false;
      const [start, lineEnd] = this.lineAt(i);
      if (NUMBERED_PREFIX.test(trim(s.slice(start, i))) || toc(s.slice(start, lineEnd)))
        return false;
      if (end - i > 1 && (s.slice(i, end).match(/\./g)?.length || 0) >= 2 && isLower(s[next]))
        return false;
    }
    if (/[”’"']/.test(s.slice(i + 1, end)) && ATTRIBUTION.test(s.slice(next, next + 100)))
      return false;
    return true;
  }
  token(i: number): string {
    let start = i;
    while (
      start > 0 &&
      (isLetter(this.text[start - 1]) ||
        isDigit(this.text[start - 1]) ||
        this.text[start - 1] === '.')
    ) {
      this.step();
      start--;
    }
    return this.text
      .slice(start, i)
      .replace(/^\.+|\.+$/g, '')
      .toLowerCase();
  }
  line(i: number): boolean {
    const s = this.text,
      previous = this.previous(i),
      n = this.next(i),
      next = n === undefined ? undefined : s[n];
    if (!previous || !next) return false;
    if (s[i + 1] === '\n' || s[i - 1] === '\n') return true;
    if (isLower(next) && previous !== '|') return false;
    if (
      MARKS.includes(previous) &&
      (isUpper(next) || isDigit(next) || '"\'\u0060(['.includes(next))
    )
      return true;
    if (previous === ':' && (isUpper(next) || '"\'\u0060(['.includes(next))) return true;
    if (previous === '-') return false;
    if (previous !== '|' && !'#|-*•'.includes(next) && !isDigit(next) && !isUpper(next))
      return false;
    const prev = trim(s.slice(this.lineAt(i)[0], i));
    const nextEnd = s.indexOf('\n', i + 1);
    const nextLine = trim(s.slice(i + 1, nextEnd < 0 ? s.length : nextEnd));
    return (
      prev.startsWith('#') ||
      nextLine.startsWith('#') ||
      prev.startsWith('|') ||
      nextLine.startsWith('|') ||
      /^[-*•] /.test(nextLine) ||
      BULLET.test(nextLine) ||
      toc(prev) ||
      toc(nextLine) ||
      chapter(prev) ||
      chapter(nextLine)
    );
  }
}
function fragments(source: string, legacy: boolean): string[] {
  const text = trim(source.replace(/\r/g, '\n')).replace(/\n{3,}/g, '\n\n');
  if (!text) return [];
  const out: string[] = [],
    boundaries = new Boundaries(text);
  let start = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    const boundary = MARKS.includes(c)
      ? boundaries.sentence(i, legacy)
      : c === '\n' &&
        (boundaries.line(i) ||
          (!legacy && speakerLabel(text.slice(i + 1, i + 121).split('\n')[0])));
    if (!boundary) continue;
    let end = i + 1;
    if (!legacy && MARKS.includes(c))
      while (end < text.length && CLOSERS.includes(text[end])) end++;
    const part = trim(text.slice(start, end));
    if (part) out.push(part);
    start = end;
    i = end - 1;
  }
  const rest = trim(text.slice(start));
  if (rest) out.push(rest);
  return out;
}
function softEnd(boundary: Boundaries, desired: number, min: number): number {
  const s = boundary.text;
  const end = Math.max(min + 1, Math.min(s.length, desired)),
    first = Math.max(min + 1, end - 600);
  for (let i = end; i >= first; i--) if (s[i] === '\n' && s[i - 1] === '\n') return i;
  for (let i = end; i >= first; i--)
    if (MARKS.includes(s[i] || '\0') && boundary.sentence(i, false))
      return Math.min(i + 1, s.length);
  for (let i = end; i >= first; i--) if (isSpace(s[i])) return i;
  return end < s.length && /[\ud800-\udbff]/.test(s[end - 1]) && /[\udc00-\udfff]/.test(s[end])
    ? end + 1
    : end;
}
function pages(raw: string, stored: number): { page: number; text: string }[] {
  const normalized = raw.replace(/\r/g, '\n'),
    result: { page: number; text: string }[] = [];
  let current = 1,
    saw = false,
    buffer: string[] = [];
  const flush = () => {
    const text = trim(buffer.join('\n'));
    if (text) result.push({ page: current, text });
    buffer = [];
  };
  for (const line of normalized.split('\n')) {
    const match = PAGE.exec(trim(line));
    if (match) {
      flush();
      saw = true;
      const parsed = Number(match[1]);
      if (Number.isSafeInteger(parsed) && parsed <= 2147483647) current = Math.max(1, parsed);
      if (current > 10000) throw new Error('Backup pages exceed the supported 10,000-page limit.');
    } else if (/veritas[_ ]page/i.test(line)) {
      const cleaned = trim(trim(line).replace(INLINE_PAGE, ''));
      if (cleaned) buffer.push(cleaned);
    } else buffer.push(line);
  }
  flush();
  if (saw && result.length) return result;
  const clean = trim(
    normalized
      .split('\n')
      .map((line) => trimEnd(line.replace(INLINE_PAGE, '')))
      .join('\n')
      .replace(/\n{3,}/g, '\n\n'),
  );
  if (!clean) return [];
  const count = stored > 0 ? stored : Math.max(1, Math.ceil(clean.length / 2200));
  if (count > 10000) throw new Error('Backup pages exceed the supported 10,000-page limit.');
  if (count === 1) return [{ page: 1, text: clean }];
  const target = Math.max(1, Math.floor(clean.length / count)),
    out: { page: number; text: string }[] = [],
    boundary = new Boundaries(clean);
  let cursor = 0;
  for (let p = 1; p <= count && cursor < clean.length; p++) {
    const end = softEnd(
      boundary,
      p === count ? clean.length : cursor + Math.min(target, clean.length - cursor),
      cursor,
    );
    const text = trim(clean.slice(cursor, end));
    if (text) out.push({ page: p, text });
    cursor = end;
  }
  return out;
}
function splitBytes(s: string): string[] {
  const result: string[] = [];
  let part = '',
    bytes = 0;
  for (const c of s) {
    const point = c.codePointAt(0)!,
      size = point <= 0x7f ? 1 : point <= 0x7ff ? 2 : point <= 0xffff ? 3 : 4;
    if (bytes + size > 4096) {
      const text = trim(part);
      if (text) result.push(text);
      part = '';
      bytes = 0;
    }
    part += c;
    bytes += size;
  }
  if (trim(part)) result.push(trim(part));
  return result;
}
export function indexAndroidText(input: AndroidIndexInput): AndroidIndex {
  if (
    input.text.length > 32 * 1024 * 1024 ||
    new TextEncoder().encode(input.text).length > 32 * 1024 * 1024
  )
    throw new Error('A restored reading exceeds 32 MB.');
  if (
    !Number.isInteger(input.pageCount) ||
    input.pageCount < 0 ||
    input.pageCount > 10000 ||
    ![1, 2].includes(input.sentenceIndexVersion) ||
    !Number.isInteger(input.expectedChunks) ||
    input.expectedChunks < 0 ||
    input.expectedChunks > 100000
  )
    throw new Error('The Android reading has unsupported page or sentence metadata.');
  if (/([.!?])\1{4096,}/.test(input.text))
    throw new Error('The backup contains an excessive punctuation run.');
  const candidates = [
    { legacy: input.sentenceIndexVersion === 1, pages: input.pageCount },
    { legacy: input.sentenceIndexVersion !== 1, pages: input.pageCount },
    { legacy: input.sentenceIndexVersion === 1, pages: 0 },
    { legacy: input.sentenceIndexVersion !== 1, pages: 0 },
  ];
  const pageCache = new Map<number, ReturnType<typeof pages>>();
  for (const candidate of candidates) {
    if (!pageCache.has(candidate.pages))
      pageCache.set(candidate.pages, pages(input.text, candidate.pages));
    const raw = pageCache
      .get(candidate.pages)!
      .flatMap((page) =>
        fragments(page.text, candidate.legacy).map((text) => ({ page: page.page, text })),
      );
    if (input.expectedChunks > 0 && raw.length !== input.expectedChunks) continue;
    const chunks: string[] = [],
      sourceToChunk: number[] = [],
      sourcePages: number[] = [];
    for (const row of raw) {
      sourceToChunk.push(chunks.length);
      sourcePages.push(row.page);
      chunks.push(...splitBytes(row.text));
    }
    if (chunks.length > 100000) throw new Error('The restored reading has too many passages.');
    const pageCount = sourcePages.reduce((m, n) => Math.max(m, n), Math.max(input.pageCount, 1)),
      pageStarts: number[] = [];
    let cursor = 0;
    for (let page = 1; page <= pageCount; page++) {
      while (cursor < sourcePages.length && sourcePages[cursor] < page) cursor++;
      pageStarts.push(
        cursor < sourceToChunk.length ? sourceToChunk[cursor] : Math.max(0, chunks.length - 1),
      );
    }
    return {
      chunks,
      sourceToChunk,
      sourcePages,
      pageStarts,
      sourceCount: raw.length,
      warnings:
        candidate === candidates[0]
          ? []
          : ['Reconstructed a legacy Android sentence index using its stored passage count.'],
    };
  }
  throw new Error(
    'The Android passage count does not match its text. Restore with the Android version that created this backup and save a fresh backup; positions were not guessed.',
  );
}
