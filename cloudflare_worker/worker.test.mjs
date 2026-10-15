import { readFile } from 'node:fs/promises';
import test from 'node:test';
import assert from 'node:assert/strict';

const source = await readFile(new URL('./worker.js', import.meta.url), 'utf8');
const { default: worker } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const env = { TELEGRAM_BOT_TOKEN: 'synthetic-test-token', TELEGRAM_WEBHOOK_SECRET: 'synthetic-webhook-secret' };
function request(body, secret = env.TELEGRAM_WEBHOOK_SECRET) {
  return new Request('https://example.test/webhook', { method: 'POST',
    headers: { 'X-Telegram-Bot-Api-Secret-Token': secret }, body });
}
test('unconfigured or unauthenticated updates cannot trigger Telegram calls', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = () => { throw new Error('Unexpected outbound request'); };
  try {
    assert.equal((await worker.fetch(request('{}'), {})).status, 503);
    assert.equal((await worker.fetch(request('{}', 'forged'), env)).status, 403);
    assert.equal((await worker.fetch(request('{'), env)).status, 400);
    assert.equal((await worker.fetch(request('x'.repeat(65537)), env)).status, 413);
  } finally { globalThis.fetch = original; }
});
test('authenticated milestone command is marked unreleased and secrets stay out of replies', async () => {
  const original = globalThis.fetch;
  const messages = [];
  globalThis.fetch = async (url, options) => {
    assert.ok(url.startsWith('https://api.telegram.org/'));
    messages.push(JSON.parse(options.body));
    return Response.json({ ok: true });
  };
  try {
    assert.equal((await worker.fetch(request(JSON.stringify({ message: { chat: { id: 12 }, text: '/milestone' } })), env)).status, 200);
    assert.equal(messages.length, 1);
    assert.match(messages[0].text, /in development/);
    assert.ok(!messages[0].text.includes(env.TELEGRAM_BOT_TOKEN));
    assert.equal((await worker.fetch(request(JSON.stringify({ message: { chat: { id: 'forged' }, text: '/download' } })), env)).status, 200);
    assert.equal(messages.length, 1);
  } finally { globalThis.fetch = original; }
});
test('downloads resolve published APK assets rather than invented names', async () => {
  const original = globalThis.fetch;
  const messages = [];
  globalThis.fetch = async (url, options) => {
    if (url.startsWith('https://api.github.com/')) return Response.json({ tag_name: 'v9.0', assets: [
      { name: 'Vern-v9.0-arm64.apk', browser_download_url: 'https://github.com/fhes-tus/Vern-TTS/releases/download/v9.0/Vern-v9.0-arm64.apk' }
    ] });
    messages.push(JSON.parse(options.body));
    return Response.json({ ok: true });
  };
  try {
    assert.equal((await worker.fetch(request(JSON.stringify({ message: { chat: { id: 12 }, text: '/download' } })), env)).status, 200);
    assert.match(JSON.stringify(messages), /Vern-v9.0-arm64.apk/);
    assert.doesNotMatch(JSON.stringify(messages), /Veritas-Reader-v9.0/);
  } finally { globalThis.fetch = original; }
});
