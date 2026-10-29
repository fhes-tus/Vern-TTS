import { readFile } from 'node:fs/promises';
import test from 'node:test';
import assert from 'node:assert/strict';

const content = JSON.parse(await readFile(new URL('../scripts/telegram_content.json', import.meta.url), 'utf8'));
const source = (await readFile(new URL('./worker.js', import.meta.url), 'utf8'))
  .replace('import CONTENT from "../scripts/telegram_content.json";', `const CONTENT = ${JSON.stringify(content)};`);
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
    assert.equal((await worker.fetch(new Request('https://example.test/setup-commands'), env)).status, 404);
    assert.equal((await worker.fetch(new Request('https://example.test/', { method: 'PUT' }), env)).status, 405);
  } finally { globalThis.fetch = original; }
});
test('legacy milestone command gives the current release and keeps secrets out of replies', async () => {
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
    assert.match(messages[0].text, /2\.7\.0.*Build 45/);
    assert.doesNotMatch(messages[0].text, /in development|preview|review is currently/i);
    assert.ok(!messages[0].text.includes(env.TELEGRAM_BOT_TOKEN));
    assert.equal((await worker.fetch(request(JSON.stringify({ message: { chat: { id: 'forged' }, text: '/download' } })), env)).status, 200);
    assert.equal(messages.length, 1);
  } finally { globalThis.fetch = original; }
});

test('welcome and current release do not depend on GitHub availability', async () => {
  const original = globalThis.fetch;
  const messages = [];
  globalThis.fetch = async (url, options) => {
    assert.ok(url.startsWith('https://api.telegram.org/'));
    messages.push(JSON.parse(options.body));
    return Response.json({ ok: true });
  };
  try {
    for (const text of ['/start', '/changelog', '/customize', '/security']) {
      assert.equal((await worker.fetch(request(JSON.stringify({ message: { chat: { id: 12 }, text } })), env)).status, 200);
    }
    assert.equal(messages.length, 4);
    assert.match(messages[0].text, /Version 2\.7\.0.*Build 45/);
    assert.equal(messages[0].reply_markup.inline_keyboard[0][0].url, content.play_url);
    assert.match(messages[2].text, /Make yourself comfortable/);
    assert.match(messages[3].text, /Privacy and offline use/);
    for (const message of messages) assert.ok(message.text.length < 1200);
  } finally { globalThis.fetch = original; }
});

test('every welcome and feature callback can be opened without dropping the reply', async () => {
  const original = globalThis.fetch;
  const messages = [];
  globalThis.fetch = async (url, options) => {
    if (url.startsWith('https://api.github.com/')) return Response.json({tag_name:'v2.6.2', assets:[]});
    const payload = JSON.parse(options.body);
    if (payload.text) messages.push(payload);
    return Response.json({ok:true});
  };
  try {
    for (const data of ['cmd_menu','cmd_download','cmd_changelog','cmd_features','cmd_classics','cmd_guide_voice','cmd_faq',
      'cmd_contact','feat_docs','feat_speech','feat_memos','feat_study','feat_tones','feat_insights','feat_modes','feat_privacy',
      'faq_voice','faq_reading','faq_study','faq_security','faq_updates']) {
      const previous=messages.length;
      const update={callback_query:{id:'callback',data,message:{chat:{id:12},message_id:34}}};
      assert.equal((await worker.fetch(request(JSON.stringify(update)),env)).status,200,data);
      assert.equal(messages.length,previous+1,data);
      assert.doesNotMatch(messages.at(-1).text,/review is currently in progress|in development/);
    }
  } finally { globalThis.fetch=original; }
});
test('downloads resolve published APK assets rather than invented names', async () => {
  const {default: assetWorker} = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}#asset-test`);
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
    assert.equal((await assetWorker.fetch(request(JSON.stringify({ message: { chat: { id: 12 }, text: '/download' } })), env)).status, 200);
    assert.match(JSON.stringify(messages), /Vern-v9.0-arm64.apk/);
    assert.doesNotMatch(JSON.stringify(messages), /Veritas-Reader-v9.0/);
  } finally { globalThis.fetch = original; }
});
