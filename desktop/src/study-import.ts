import type { QuizQuestion } from './types';
type Pair = { front: string; back: string };
type Row = Record<string, unknown>;
const clean = (s: string) => s.replace(/\*\*|__|`/g, '').trim();
function bounded(text: string) {
  if (text.length > 500_000) throw new Error('Paste up to 500,000 characters at a time.');
}
function jsonRows(text: string, keys: string[]): Row[] | null {
  const start = text.search(/[[{]/);
  if (start < 0) return null;
  const end = Math.max(text.lastIndexOf(']'), text.lastIndexOf('}'));
  try {
    const parsed = JSON.parse(text.slice(start, end + 1));
    const rows = Array.isArray(parsed) ? parsed : keys.map((k) => parsed[k]).find(Array.isArray);
    return Array.isArray(rows) ? rows.filter((r): r is Row => !!r && typeof r === 'object') : null;
  } catch {
    return null;
  }
}
function value(row: Row, ...keys: string[]) {
  return (
    keys
      .map((k) => row[k])
      .find((s): s is string => typeof s === 'string' && !!s.trim())
      ?.trim() ?? ''
  );
}
function limit<T>(items: T[]): T[] {
  if (items.length > 200) throw new Error('Import up to 200 items at a time.');
  if (!items.length) throw new Error('No complete items found. Check the format below.');
  return items;
}
function answer(raw: string, options: Map<string, string>): string {
  const whole = [...options.values()].find((v) => v.toLowerCase() === raw.toLowerCase());
  if (whole) return whole;
  const label = raw.match(/^([a-z0-9])(?:[).:-]\s*(.*))?$/i);
  const resolved = label ? options.get(label[1].toUpperCase()) : '';
  return resolved && (!label?.[2] || label[2].toLowerCase() === resolved.toLowerCase())
    ? resolved
    : '';
}
export function parseCards(text: string): Pair[] {
  bounded(text);
  const rows = jsonRows(text, ['flashcards', 'cards', 'deck']);
  if (rows)
    return limit(
      rows
        .filter((r) => !('options' in r))
        .map((r) => ({
          front: value(r, 'front', 'question', 'term'),
          back: value(r, 'back', 'answer', 'definition'),
        }))
        .filter((c) => c.front && c.back),
    );
  if (/^\s*[B-D][).:-]\s*\S/m.test(text))
    throw new Error('This looks like a quiz. Choose Quiz instead.');
  const table = text
    .split('\n')
    .filter((l) => l.trim().startsWith('|') && !l.includes('---'))
    .map((l) => l.split('|').slice(1, -1).map(clean))
    .filter((c) => c.length >= 2 && c[0] && c[1] && !/^(front|term|question|concept)$/i.test(c[0]))
    .map((c) => ({ front: c[0], back: c[1] }));
  if (table.length) return limit(table);
  const definitions = [
    ...text.matchAll(/^\s*(?:\d+[.)]|[-*•])\s*\*\*([^*]+)\*\*[:\s—–-]+(.+)$/gm),
  ].map((m) => ({ front: clean(m[1]), back: clean(m[2]) }));
  if (definitions.length) return limit(definitions);
  const cards: Pair[] = [];
  let front = '';
  let back = '';
  let inBack = false;
  const flush = () => {
    if (front && back) cards.push({ front: front.trim(), back: back.trim() });
    front = '';
    back = '';
    inBack = false;
  };
  for (const raw of text.split('\n')) {
    const line = clean(raw).replace(/^\s*(?:[-*•]|\d+[.)])\s*/, '');
    const q = line.match(/^(?:q|front|question|term|concept|prompt):\s*(.*)$/i);
    const a = line.match(/^(?:a|back|answer|definition|meaning|explanation):\s*(.*)$/i);
    if (q) {
      flush();
      front = q[1];
    } else if (a && front) {
      back += `${back ? ' ' : ''}${a[1]}`;
      inBack = true;
    } else if (!line && inBack) flush();
    else if (inBack) back += ` ${line}`;
    else if (front) front += ` ${line}`;
  }
  flush();
  return limit(cards);
}
export function parseQuiz(text: string): QuizQuestion[] {
  bounded(text);
  const rows = jsonRows(text, ['quiz', 'questions', 'items']);
  if (rows)
    return limit(
      rows
        .map((r) => {
          const options = new Map<string, string>();
          if (Array.isArray(r.options))
            r.options.forEach((o, i) => {
              if (typeof o === 'string') options.set(String.fromCharCode(65 + i), o.trim());
            });
          else if (r.options && typeof r.options === 'object')
            for (const [k, v] of Object.entries(r.options))
              if (typeof v === 'string') options.set(k.toUpperCase(), v.trim());
          const values = [...options.values()];
          const resolved =
            Number.isInteger(r.answerIndex) && Number(r.answerIndex) >= 0
              ? values[Number(r.answerIndex)]
              : answer(value(r, 'answer', 'correct'), options);
          return {
            question: value(r, 'question', 'q'),
            options: values,
            answer: resolved ?? '',
            explanation: value(r, 'explanation', 'reason'),
          };
        })
        .filter(valid),
    );
  const questions: QuizQuestion[] = [];
  let question = '';
  let options = new Map<string, string>();
  let key = '';
  let explanation = '';
  const flush = () => {
    const q = {
      question,
      options: [...options.values()],
      answer: answer(key, options),
      explanation,
    };
    if (valid(q)) questions.push(q);
    question = '';
    options = new Map();
    key = '';
    explanation = '';
  };
  for (const raw of text.split('\n')) {
    const line = clean(raw).replace(/^[-*•]\s*/, '');
    if (!line) continue;
    const a = line.match(/^(?:correct\s*)?answer(?:\s*key)?\s*[:-]\s*(.+)$/i);
    const why = line.match(/^(?:explanation|reason|why)\s*[:-]\s*(.+)$/i);
    const choice = line.match(/^([a-d0-9])[).:-]\s*(.+)$/i);
    if (a) key = a[1];
    else if (why) explanation = why[1];
    else if (choice && question && (!key || options.size === 0))
      options.set(choice[1].toUpperCase(), choice[2]);
    else {
      if (options.size) flush();
      question +=
        (question ? ' ' : '') + line.replace(/^(?:Q(?:uestion)?\s*\d*[:.)-]?|\d+[.)])\s*/i, '');
    }
  }
  flush();
  return limit(questions);
}
function valid(q: QuizQuestion) {
  return (
    !!q.question &&
    q.options.length >= 2 &&
    q.options.length <= 8 &&
    q.options.every(Boolean) &&
    new Set(q.options.map((o) => o.toLowerCase())).size === q.options.length &&
    q.options.includes(q.answer)
  );
}
