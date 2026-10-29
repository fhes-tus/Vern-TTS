import CONTENT from "../scripts/telegram_content.json";
/**
 * Vern TTS Autonomous Telegram Bot Worker (Comprehensive Workstation Edition)
 * Serverless 24/7 Telegram Webhook Handler on Cloudflare Workers
 * Offline-First Android Reading Workstation Knowledge Center & Assistant.
 */

let releaseCache;
let releaseCacheAt = 0;
const REPO = "fhes-tus/Vern-TTS";
const WEBSITE_URL = "https://fhes-tus.github.io/Vern-TTS/";
const CHANNEL_URL = "https://t.me/myreader_veritas";
const TWITTER_URL = "https://x.com/_1st2us";
const EMAIL_ADDRESS = "myreader.veritas@gmail.com";
const GITHUB_URL = `https://github.com/${REPO}`;

const CLASSICS = [
  {
    "id": "classic_wizard_of_oz",
    "title": "The Wonderful Wizard of Oz",
    "author": "L. Frank Baum",
    "genre": "Family & Youth",
    "category": "Family & Youth",
    "description": "Dorothy's magical journey through the Land of Oz with the Scarecrow, Tin Woodman, and Cowardly Lion.",
    "quote": "There is no place like home."
  },
  {
    "id": "classic_alice_wonderland",
    "title": "Alice in Wonderland",
    "author": "Lewis Carroll",
    "genre": "Family & Youth",
    "category": "Family & Youth",
    "description": "Alice plunges down a rabbit hole into a whimsical, topsy-turvy subterranean fantasy world.",
    "quote": "Curiouser and curiouser!"
  },
  {
    "id": "classic_peter_pan",
    "title": "Peter Pan",
    "author": "J.M. Barrie",
    "genre": "Family & Youth",
    "category": "Family & Youth",
    "description": "The boy who wouldn't grow up whisks Wendy and her brothers away to the enchanted island of Neverland.",
    "quote": "To live will be an awfully big adventure."
  },
  {
    "id": "classic_anne_green_gables",
    "title": "Anne of Green Gables",
    "author": "L.M. Montgomery",
    "genre": "Family & Youth",
    "category": "Family & Youth",
    "description": "An imaginative, fiercely spirited red-headed orphan brings joy and vitality to Prince Edward Island.",
    "quote": "Dear old world, you are very lovely, and I am glad to be alive in you."
  },
  {
    "id": "classic_secret_garden",
    "title": "The Secret Garden",
    "author": "Frances Hodgson Burnett",
    "genre": "Family & Youth",
    "category": "Family & Youth",
    "description": "Mary Lennox discovers an overgrown, locked garden that heals her spirit and transforms her family.",
    "quote": "If you look the right way, you can see that the whole world is a garden."
  },
  {
    "id": "classic_aesop",
    "title": "Aesop's Fables",
    "author": "Aesop",
    "genre": "Family & Youth",
    "category": "Family & Youth",
    "description": "Timeless moral tales starring clever animals teaching wisdom, honesty, and humility.",
    "quote": "No act of kindness, no matter how small, is ever wasted."
  },
  {
    "id": "classic_sherlock_holmes",
    "title": "The Adventures of Sherlock Holmes",
    "author": "Arthur Conan Doyle",
    "genre": "Mystery",
    "category": "Mystery",
    "description": "Twelve brilliant detective cases solved by Sherlock Holmes and Dr. Watson at 221B Baker Street.",
    "quote": "When you have eliminated the impossible, whatever remains must be the truth."
  },
  {
    "id": "classic_hound_baskervilles",
    "title": "The Hound of the Baskervilles",
    "author": "Arthur Conan Doyle",
    "genre": "Mystery",
    "category": "Mystery",
    "description": "Holmes investigates the curse of a spectral hound haunting the foggy Dartmoor bogs.",
    "quote": "The world is full of obvious things which nobody ever observes."
  },
  {
    "id": "classic_styles",
    "title": "The Mysterious Affair at Styles",
    "author": "Agatha Christie",
    "genre": "Mystery",
    "category": "Mystery",
    "description": "The dazzling mystery debut introducing the eccentric and meticulous Belgian detective Hercule Poirot.",
    "quote": "Instinct is a marvelous thing. It can neither be explained nor ignored."
  },
  {
    "id": "classic_rue_morgue",
    "title": "The Murders in the Rue Morgue",
    "author": "Edgar Allan Poe",
    "genre": "Mystery",
    "category": "Mystery",
    "description": "The pioneering locked-room whodunit where detective C. Auguste Dupin solves an impossible double murder.",
    "quote": "To observe attentively is to remember distinctly."
  },
  {
    "id": "classic_around_world_80_days",
    "title": "Around the World in 80 Days",
    "author": "Jules Verne",
    "genre": "Adventure",
    "category": "Adventure",
    "description": "Phileas Fogg bets his fortune on a breathless, high-stakes global race across steamers, trains, and elephants.",
    "quote": "Anything one man can imagine, other men can make real."
  },
  {
    "id": "classic_treasure_island",
    "title": "Treasure Island",
    "author": "Robert Louis Stevenson",
    "genre": "Adventure",
    "category": "Adventure",
    "description": "Young Jim Hawkins discovers a pirate treasure map, setting sail against the cunning Long John Silver.",
    "quote": "Fifteen men on the dead man's chest\u2014Yo-ho-ho, and a bottle of rum!"
  },
  {
    "id": "classic_call_of_the_wild",
    "title": "The Call of the Wild",
    "author": "Jack London",
    "genre": "Adventure",
    "category": "Adventure",
    "description": "Stolen from California and sold into the Yukon gold rush, Buck must rediscover his primal wolf instincts.",
    "quote": "He was mastered by the sheer surging of life, the tidal wave of being."
  },
  {
    "id": "classic_twenty_thousand_leagues",
    "title": "20,000 Leagues Under the Sea",
    "author": "Jules Verne",
    "genre": "Adventure",
    "category": "Adventure",
    "description": "Professor Aronnax embarks on an awe-inspiring underwater voyage aboard Captain Nemo's submarine, the Nautilus.",
    "quote": "The sea is everything. It covers seven tenths of the terrestrial globe."
  },
  {
    "id": "classic_three_musketeers",
    "title": "The Three Musketeers",
    "author": "Alexandre Dumas",
    "genre": "Adventure",
    "category": "Adventure",
    "description": "D'Artagnan joins Athos, Porthos, and Aramis in swashbuckling swordplay, state intrigue, and brotherhood.",
    "quote": "All for one and one for all, united we stand divided we fall."
  },
  {
    "id": "classic_pride_and_prejudice",
    "title": "Pride and Prejudice",
    "author": "Jane Austen",
    "genre": "Romance & Drama",
    "category": "Romance",
    "description": "The sparkling romantic battle of wits between the witty Elizabeth Bennet and proud Mr. Darcy.",
    "quote": "I declare after all there is no enjoyment like reading!"
  },
  {
    "id": "classic_little_women",
    "title": "Little Women",
    "author": "Louisa May Alcott",
    "genre": "Romance & Drama",
    "category": "Romance",
    "description": "The heartwarming story of the four March sisters navigating love, creativity, ambition, and family.",
    "quote": "I am not afraid of storms, for I am learning how to sail my ship."
  },
  {
    "id": "classic_jane_eyre",
    "title": "Jane Eyre",
    "author": "Charlotte Bront\u00eb",
    "genre": "Romance & Drama",
    "category": "Romance",
    "description": "An independent orphaned governess falls for the brooding Mr. Rochester while uncovering Thornfield's dark secret.",
    "quote": "I am no bird; and no net ensnares me; I am a free human being."
  },
  {
    "id": "classic_sense_and_sensibility",
    "title": "Sense and Sensibility",
    "author": "Jane Austen",
    "genre": "Romance & Drama",
    "category": "Romance",
    "description": "The Dashwood sisters find love and learn to balance reason and intense emotion in Victorian society.",
    "quote": "It isn't what we say or think that defines us, but what we do."
  },
  {
    "id": "classic_ben_franklin",
    "title": "The Autobiography of Benjamin Franklin",
    "author": "Benjamin Franklin",
    "genre": "Life & Habits",
    "category": "Life & Habits",
    "description": "The founding father's charming handbook on daily routines, practical virtues, and personal self-discipline.",
    "quote": "Energy and persistence conquer all things."
  },
  {
    "id": "classic_as_a_man_thinketh",
    "title": "As a Man Thinketh",
    "author": "James Allen",
    "genre": "Life & Habits",
    "category": "Life & Habits",
    "description": "A short, profound handbook explaining how the mind shapes personal destiny, joy, and success.",
    "quote": "A man is literally what he thinks, his character being the sum of his thoughts."
  },
  {
    "id": "classic_the_prophet",
    "title": "The Prophet",
    "author": "Kahlil Gibran",
    "genre": "Life & Habits",
    "category": "Life & Habits",
    "description": "Twenty-six poetic essays offering gentle reflections on love, marriage, work, friendship, and peace.",
    "quote": "Work is love made visible."
  },
  {
    "id": "classic_self_reliance",
    "title": "Self-Reliance",
    "author": "Ralph Waldo Emerson",
    "genre": "Life & Habits",
    "category": "Life & Habits",
    "description": "An inspiring manifesto urging every person to trust their inner intuition rather than societal conformity.",
    "quote": "To be yourself in a world trying to make you something else is great accomplishment."
  },
  {
    "id": "classic_meditations",
    "title": "Meditations",
    "author": "Marcus Aurelius",
    "genre": "Life & Habits",
    "category": "Life & Habits",
    "description": "Private journals of the Roman Emperor on stoic duty, resilience, emotional mastery, and inner peace.",
    "quote": "You have power over your mind - not outside events. Realize this, and find strength."
  },
  {
    "id": "classic_art_of_war",
    "title": "The Art of War",
    "author": "Sun Tzu",
    "genre": "Life & Habits",
    "category": "Life & Habits",
    "description": "Ancient Chinese strategic wisdom on leadership, deception, conflict resolution, and self-possession.",
    "quote": "In the midst of chaos, there is also opportunity."
  },
  {
    "id": "classic_gift_of_magi",
    "title": "The Gift of the Magi",
    "author": "O. Henry",
    "genre": "Quick Reads",
    "category": "Quick Reads",
    "description": "A heartwarming Christmas masterpiece about love, selfless sacrifice, and the true meaning of giving.",
    "quote": "Of all who give and receive gifts, such as they are wisest."
  },
  {
    "id": "classic_christmas_carol",
    "title": "A Christmas Carol",
    "author": "Charles Dickens",
    "genre": "Quick Reads",
    "category": "Quick Reads",
    "description": "The miserly Ebenezer Scrooge is visited by three spirits who transform his cold heart into joyful generosity.",
    "quote": "I will honour Christmas in my heart, and try to keep it all the year."
  },
  {
    "id": "classic_yellow_wallpaper",
    "title": "The Yellow Wallpaper",
    "author": "Charlotte Perkins Gilman",
    "genre": "Quick Reads",
    "category": "Quick Reads",
    "description": "A chilling psychological tale documenting a woman's battle for autonomy and creativity in a confined room.",
    "quote": "I got out at last, in spite of you and Jane!"
  },
  {
    "id": "classic_metamorphosis",
    "title": "The Metamorphosis",
    "author": "Franz Kafka",
    "genre": "Quick Reads",
    "category": "Quick Reads",
    "description": "Traveling salesman Gregor Samsa awakens one morning to find himself transformed into a giant insect.",
    "quote": "One morning Gregor Samsa awoke transformed in his bed into a monstrous vermin."
  },
  {
    "id": "classic_monte_cristo",
    "title": "The Count of Monte Cristo",
    "author": "Alexandre Dumas",
    "genre": "Epic Classics",
    "category": "Epic Classics",
    "description": "The ultimate saga of betrayal, hidden treasure, enduring patience, retribution, and redemption.",
    "quote": "All human wisdom is contained in these two words: Wait and Hope."
  },
  {
    "id": "classic_frankenstein",
    "title": "Frankenstein",
    "author": "Mary Shelley",
    "genre": "Epic Classics",
    "category": "Epic Classics",
    "description": "The seminal sci-fi gothic tragedy of Victor Frankenstein and the sentient creature he brought to life.",
    "quote": "Beware; for I am fearless, and therefore powerful."
  },
  {
    "id": "classic_dracula",
    "title": "Dracula",
    "author": "Bram Stoker",
    "genre": "Epic Classics",
    "category": "Epic Classics",
    "description": "The definitive vampire classic detailing Count Dracula's attempt to move from Transylvania to England.",
    "quote": "We learn from failure, not from success!"
  },
  {
    "id": "classic_dorian_gray",
    "title": "The Picture of Dorian Gray",
    "author": "Oscar Wilde",
    "genre": "Epic Classics",
    "category": "Epic Classics",
    "description": "A hedonistic young man sells his soul for eternal youth while his portrait absorbs the sins of his life.",
    "quote": "The books that the world calls immoral are books that show the world its own shame."
  },
  {
    "id": "classic_tale_two_cities",
    "title": "A Tale of Two Cities",
    "author": "Charles Dickens",
    "genre": "Epic Classics",
    "category": "Epic Classics",
    "description": "Set in London and Paris during the French Revolution, depicting sacrifice, love, and redemption.",
    "quote": "It was the best of times, it was the worst of times."
  },
  {
    "id": "classic_walden",
    "title": "Walden",
    "author": "Henry David Thoreau",
    "genre": "Epic Classics",
    "category": "Epic Classics",
    "description": "A reflection upon simple living in natural surroundings and personal declaration of independence.",
    "quote": "I went to the woods because I wished to live deliberately."
  }
];

export default {
  async fetch(request, env, ctx) {
    if (request.method !== "POST") {
      if (!["GET", "HEAD"].includes(request.method)) return new Response("Method not allowed", { status: 405 });
      if (new URL(request.url).pathname !== "/") return new Response("Not found", { status: 404 });
      return new Response("Vern Telegram bot active", { status: 200 });
    }

    if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_WEBHOOK_SECRET) {
      return new Response("Webhook is not configured", { status: 503 });
    }
    if (request.headers.get("X-Telegram-Bot-Api-Secret-Token") !== env.TELEGRAM_WEBHOOK_SECRET) {
      return new Response("Forbidden", { status: 403 });
    }
    let update;
    try {
      const reader = request.body?.getReader();
      if (!reader) return new Response("Missing body", { status: 400 });
      const chunks = [];
      let size = 0;
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > 65536) {
          await reader.cancel();
          return new Response("Body too large", { status: 413 });
        }
        chunks.push(value);
      }
      const bytes = new Uint8Array(size);
      let offset = 0;
      for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
      update = JSON.parse(new TextDecoder().decode(bytes));
      if (!update || typeof update !== "object") throw new Error("Invalid update");
    } catch {
      return new Response("Invalid JSON", { status: 400 });
    }
    try {
      if (update.message) {
        await handleMessage(update.message, env);
      } else if (update.callback_query) {
        await handleCallbackQuery(update.callback_query, env);
      }
      return new Response("OK", { status: 200 });
    } catch {
      return new Response("Temporary upstream failure", { status: 503 });
    }
  }
};

/* --- Telegram API Helpers --- */

function stripHtmlTags(text) {
  return text ? text.replace(/<[^>]+>/g, '') : "";
}

function safeTruncateHtml(text, limit = 3900) {
  if (!text || text.length <= limit) return text;
  let cutPos = text.lastIndexOf("\n\n", limit - 100);
  if (cutPos === -1 || cutPos < 1000) cutPos = text.lastIndexOf("\n", limit - 100);
  if (cutPos === -1 || cutPos < 500) cutPos = limit - 100;
  let truncated = text.slice(0, cutPos) + "\n\n<i>...[truncated]</i>";

  for (const tag of ['b', 'i', 'code', 'a']) {
    const openMatches = truncated.match(new RegExp(`<${tag}(\\s+[^>]*)?>`, 'gi')) || [];
    const closeMatches = truncated.match(new RegExp(`</${tag}>`, 'gi')) || [];
    if (openMatches.length > closeMatches.length) {
      truncated += `</${tag}>`.repeat(openMatches.length - closeMatches.length);
    }
  }
  return truncated;
}

async function sendMessage(token, chatId, text, replyMarkup = null) {
  const url = `https://api.telegram.org/bot${token}/sendMessage`;
  const safeText = safeTruncateHtml(text, 3900);
  const payload = {
    chat_id: chatId,
    text: safeText,
    parse_mode: "HTML",
    disable_web_page_preview: true
  };
  if (replyMarkup) {
    payload.reply_markup = replyMarkup;
  }
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    if (data && data.ok) return data;
    if (data?.error_code !== 400 || !/parse|entities/i.test(data.description || "")) throw new Error("Telegram request failed");
    // Retry an explicitly rejected HTML parse only.
    payload.parse_mode = null;
    payload.text = stripHtmlTags(text).slice(0, 3900);
    return fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  } catch (err) {
    throw new Error("Telegram request failed");
  }
}

async function editMessageText(token, chatId, messageId, text, replyMarkup = null) {
  const url = `https://api.telegram.org/bot${token}/editMessageText`;
  const safeText = safeTruncateHtml(text, 3900);
  const payload = {
    chat_id: chatId,
    message_id: messageId,
    text: safeText,
    parse_mode: "HTML",
    disable_web_page_preview: true
  };
  if (replyMarkup) {
    payload.reply_markup = replyMarkup;
  }
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    if (data && data.ok) return data;
    if (data && data.description && data.description.includes("message is not modified")) {
      return data;
    }
    if (data?.error_code !== 400 || !/parse|entities/i.test(data.description || "")) throw new Error("Telegram request failed");
    // Retry an explicitly rejected HTML parse only.
    payload.parse_mode = null;
    payload.text = stripHtmlTags(text).slice(0, 3900);
    return fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  } catch (err) {
    throw new Error("Telegram request failed");
  }
}

async function answerCallbackQuery(token, callbackQueryId, text = "") {
  const url = `https://api.telegram.org/bot${token}/answerCallbackQuery`;
  return fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ callback_query_id: callbackQueryId, text: text })
  });
}

function escapeHtml(str) {
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

async function setupBotCommands(token) {
  const commands = CONTENT.commands;
  const url = `https://api.telegram.org/bot${token}/setMyCommands`;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ commands })
  });
  return res.json();
}

/* --- Dynamic Release Resolution & Changelog --- */

async function getLatestRelease() {
  if (releaseCache && Date.now() - releaseCacheAt < 900000) return releaseCache;
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
      headers: { "User-Agent": "Vern-Cloudflare-Worker" }
    });
    if (!res.ok) throw new Error("Release unavailable");
    const data = await res.json();
    const assets = (data.assets || []).filter(a => a.name?.endsWith(".apk") &&
      a.browser_download_url?.startsWith(`${GITHUB_URL}/releases/download/`));
    const asset = marker => assets.find(a => a.name.includes(marker))?.browser_download_url || `${GITHUB_URL}/releases/latest`;
    releaseCache = {
      tag: escapeHtml(data.tag_name || "Latest stable"),
      name: escapeHtml(data.name || "Vern"),
      body: String(data.body || "").slice(0, 2800),
      arm64_url: asset("arm64"), universal_url: asset("universal"), arm32_url: asset("armeabi")
    };
    releaseCacheAt = Date.now();
    return releaseCache;
  } catch {
    return releaseCache || { tag: "Latest stable", name: "Vern",
      arm64_url: `${GITHUB_URL}/releases/latest`, universal_url: `${GITHUB_URL}/releases/latest`,
      arm32_url: `${GITHUB_URL}/releases/latest` };
  }
}

async function sendMilestone(token, chatId) {
  await sendChangelogContent(token, chatId);
}

function markdownToTelegramHtml(mdText, maxBullets = 8) {
  if (!mdText) return "";
  const lines = mdText.split(/\r?\n/);
  const cleanLines = [];
  let bulletCount = 0;
  let inDownloads = false;

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) {
      if (cleanLines.length && cleanLines[cleanLines.length - 1] !== "") {
        cleanLines.push("");
      }
      continue;
    }

    if (/^(-{3,}|_{3,}|\*{3,})$/.test(line)) continue;

    const headerMatch = line.match(/^#{1,6}\s*(.*)/);
    if (headerMatch) {
      const title = headerMatch[1].trim();
      const lowerTitle = title.toLowerCase();
      if (['download', 'artifact', 'verification'].some(w => lowerTitle.includes(w))) {
        inDownloads = true;
        continue;
      }
      if (inDownloads) continue;
      if (lowerTitle.includes("build") || lowerTitle.startsWith("vern")) continue;

      let titleEscaped = escapeHtml(title);
      titleEscaped = titleEscaped.replace(/\*\*(.*?)\*\*/g, '<b>$1</b>');
      cleanLines.push(`\n<b>${titleEscaped}</b>`);
      continue;
    }

    if (inDownloads) continue;

    const lower = line.toLowerCase();
    if (['sha-256', 'sha256', 'checksum', 'verification summary', 'compiledebug', 'compilerelease'].some(w => lower.includes(w))) continue;
    if (/[a-f0-9]{64}/i.test(line)) continue;

    const bulletMatch = line.match(/^[\*\-]\s+(.*)/);
    if (bulletMatch) {
      bulletCount++;
      if (maxBullets && bulletCount > maxBullets) continue;
      let content = bulletMatch[1].trim();
      content = escapeHtml(content);
      content = content.replace(/\*\*(.*?)\*\*/g, '<b>$1</b>');
      content = content.replace(/(?<!\w)\*([^\*]+)\*(?!\w)/g, '<i>$1</i>');
      content = content.replace(/`([^`]+)`/g, '<code>$1</code>');
      content = content.replace(/\[([^\]]+)\]\((https?:\/\/[^\s\)]+)\)/g, '<a href="$2">$1</a>');
      cleanLines.push(`• ${content}`);
      continue;
    }

    let escaped = escapeHtml(line);
    escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<b>$1</b>');
    escaped = escaped.replace(/(?<!\w)\*([^\*]+)\*(?!\w)/g, '<i>$1</i>');
    escaped = escaped.replace(/`([^`]+)`/g, '<code>$1</code>');
    escaped = escaped.replace(/\[([^\]]+)\]\((https?:\/\/[^\s\)]+)\)/g, '<a href="$2">$1</a>');
    cleanLines.push(escaped);
  }

  if (maxBullets && bulletCount > maxBullets) {
    cleanLines.push(`\n• <i>...and ${bulletCount - maxBullets} more enhancements!</i>`);
  }

  let result = cleanLines.join("\n").trim();
  result = result.replace(/\n{3,}/g, '\n\n');
  return result;
}

async function sendChangelogContent(token, chatId, messageId = null) {
  const text = CONTENT.release;
  const markup = {"inline_keyboard":[[{"text":"Get it on Google Play","url":"https://play.google.com/store/apps/details?id=com.veritas.reader"}],[{"text":"Full release notes","url":"https://github.com/fhes-tus/Vern-TTS/blob/main/RELEASE_NOTES_v2.7.0.md"}],[{"text":"Main menu","callback_data":"cmd_menu"}]]};
  if (messageId) await editMessageText(token, chatId, messageId, text, markup);
  else await sendMessage(token, chatId, text, markup);
}

/* --- Navigation & Menu Handlers --- */

async function sendWelcome(token, chatId, messageId = null) {
  const text = CONTENT.welcome;
  const markup = {"inline_keyboard":[[{"text":"Get it on Google Play","url":"https://play.google.com/store/apps/details?id=com.veritas.reader"}],[{"text":"What's new in 2.7.0","callback_data":"cmd_changelog"},{"text":"Explore classics","callback_data":"cmd_classics"}],[{"text":"Features","callback_data":"cmd_features"},{"text":"Voices and listening","callback_data":"cmd_guide_voice"}],[{"text":"Help / FAQs","callback_data":"cmd_faq"},{"text":"Telegram channel","url":"https://t.me/myreader_veritas"}],[{"text":"Website","url":"https://fhes-tus.github.io/Vern-TTS/"}]]};
  if (messageId) await editMessageText(token, chatId, messageId, text, markup);
  else await sendMessage(token, chatId, text, markup);
}

async function sendDownloadInfo(token, chatId, messageId = null) {
  const rel = await getLatestRelease();
  const text = CONTENT.download + '\n\nLatest GitHub APK release: <b>' + escapeHtml(stripHtmlTags(rel.tag)) + '</b>';
  const markup = {"inline_keyboard":[[{"text":"Get it on Google Play","url":"https://play.google.com/store/apps/details?id=com.veritas.reader"}],[{"text":"Published GitHub APKs","url":"https://github.com/fhes-tus/Vern-TTS/releases/latest"}],[{"text":"What's new","callback_data":"cmd_changelog"},{"text":"Support","callback_data":"cmd_contact"}],[{"text":"Main menu","callback_data":"cmd_menu"}]]};
  // Use only an actual published asset, never an invented download filename.
  if (rel.universal_url !== GITHUB_URL + '/releases/latest') markup.inline_keyboard.splice(2, 0, [{text: 'Universal APK (' + stripHtmlTags(rel.tag) + ')', url: rel.universal_url}]);
  else if (rel.arm64_url !== GITHUB_URL + '/releases/latest') markup.inline_keyboard.splice(2, 0, [{text: 'ARM64 APK (' + stripHtmlTags(rel.tag) + ')', url: rel.arm64_url}]);
  if (messageId) await editMessageText(token, chatId, messageId, text, markup);
  else await sendMessage(token, chatId, text, markup);
}

async function sendFeaturesMenu(token, chatId, messageId = null) {
  const text = CONTENT.features_intro;
  const markup = {"inline_keyboard":[[{"text":"Reading and files","callback_data":"feat_docs"},{"text":"Voices and listening","callback_data":"feat_speech"}],[{"text":"Notes and voice memos","callback_data":"feat_memos"},{"text":"Flashcards and quizzes","callback_data":"feat_study"}],[{"text":"Page colours and text","callback_data":"feat_tones"},{"text":"Reading habits","callback_data":"feat_insights"}],[{"text":"Page turns and scrolling","callback_data":"feat_modes"},{"text":"Privacy","callback_data":"feat_privacy"}],[{"text":"Main menu","callback_data":"cmd_menu"}]]};
  if (messageId) await editMessageText(token, chatId, messageId, text, markup);
  else await sendMessage(token, chatId, text, markup);
}

async function sendFeatureDetail(token, chatId, messageId, topic) {
  const detail = CONTENT.features[topic] || CONTENT.features.privacy;
  const text = '<b>' + escapeHtml(detail.title) + '</b>\n\n' + detail.body;
  const markup = {inline_keyboard: [[{text: 'Back to features', callback_data:'cmd_features'}, {"text":"Main menu","callback_data":"cmd_menu"}]]};
  if (messageId) await editMessageText(token, chatId, messageId, text, markup);
  else await sendMessage(token, chatId, text, markup);
}

async function sendGuideMasterclass(token, chatId, messageId = null) {
  const text = CONTENT.voice;
  const markup = {"inline_keyboard":[[{"text":"Main menu","callback_data":"cmd_menu"}]]};
  if (messageId) await editMessageText(token, chatId, messageId, text, markup);
  else await sendMessage(token, chatId, text, markup);
}

async function sendRandomBook(token, chatId, messageId = null) {
  const book = CLASSICS[Math.floor(Math.random() * CLASSICS.length)];
  const title = escapeHtml(book.title);
  const author = escapeHtml(book.author);
  const genre = escapeHtml(book.genre);
  const quote = escapeHtml(book.quote || "");
  const desc = escapeHtml(book.description || "");

  let text =
    `🎲 <b>Classic Spotlight</b>\n\n` +
    `📖 <b>${title}</b>\n` +
    `<i>by ${author}</i>\n\n` +
    `🏷 <b>Category:</b> ${genre}\n`;
  if (quote) {
    text += `💬 <b>Notable Quote:</b>\n<i>\"${quote}\"</i>\n\n`;
  }
  text +=
    `📝 <b>Synopsis:</b>\n${desc}\n\n` +
    `✨ <i>Pre-loaded inside Vern TTS with authentic first-edition cover art!</i>`;

  const markup = {
    inline_keyboard: [
      [
        { text: "🎲 Another Random Classic", callback_data: "cmd_random_book" }
      ],
      [
        { text: "📚 Browse Categories", callback_data: "cmd_classics" },
        { text: "⬇️ Download App", callback_data: "cmd_download" }
      ],
      [
        { text: "⬅️ Back to Main Menu", callback_data: "cmd_menu" }
      ]
    ]
  };

  if (messageId) {
    await editMessageText(token, chatId, messageId, text, markup);
  } else {
    await sendMessage(token, chatId, text, markup);
  }
}

async function sendClassicsMenu(token, chatId, messageId = null) {
  const text =
    `📚 <b>35+ Curated Public Domain Classics</b>\n\n` +
    `Vern TTS comes with a built-in library of timeless literary works, each pre-loaded with ` +
    `<b>authentic, published first-edition cover art</b>, memorable quotes, and clean typography.\n\n` +
    `<i>Choose a genre below to explore titles or roll for a random book:</i>`;

  const markup = {
    inline_keyboard: [
      [
        { text: "🎲 Random Book Spotlight", callback_data: "cmd_random_book" }
      ],
      [
        { text: "👨‍👩‍👧 Family & Youth (6)", callback_data: "cls_cat_Family & Youth" },
        { text: "🔍 Mystery (4)", callback_data: "cls_cat_Mystery" }
      ],
      [
        { text: "🗺 Adventure (5)", callback_data: "cls_cat_Adventure" },
        { text: "📜 Epic Classics (6)", callback_data: "cls_cat_Epic Classics" }
      ],
      [
        { text: "💡 Life & Habits (6)", callback_data: "cls_cat_Life & Habits" },
        { text: "⚡️ Quick Reads (4)", callback_data: "cls_cat_Quick Reads" }
      ],
      [
        { text: "❤️ Romance & Drama (4)", callback_data: "cls_cat_Romance & Drama" }
      ],
      [
        { text: "⬇️ Download App to Read", callback_data: "cmd_download" }
      ],
      [
        { text: "⬅️ Back to Main Menu", callback_data: "cmd_menu" }
      ]
    ]
  };

  if (messageId) {
    await editMessageText(token, chatId, messageId, text, markup);
  } else {
    await sendMessage(token, chatId, text, markup);
  }
}

async function sendClassicsCategory(token, chatId, messageId, category) {
  const catBooks = CLASSICS.filter(b => b.genre === category || b.category === category);
  let text = `🏷 <b>Category: ${escapeHtml(category)}</b> (${catBooks.length} titles)\n\n`;

  catBooks.forEach((b, i) => {
    text += `<b>${i + 1}. ${escapeHtml(b.title)}</b>\n<i>by ${escapeHtml(b.author)}</i>\n${escapeHtml(b.description)}\n\n`;
  });

  text += `✨ <i>All 35+ titles are available inside Vern TTS with 1-tap download and zero cloud accounts.</i>`;

  const markup = {
    inline_keyboard: [
      [
        { text: "🎲 Random Classic", callback_data: "cmd_random_book" },
        { text: "📚 All Categories", callback_data: "cmd_classics" }
      ],
      [
        { text: "⬇️ Download App to Read", callback_data: "cmd_download" }
      ],
      [
        { text: "⬅️ Back to Main Menu", callback_data: "cmd_menu" }
      ]
    ]
  };

  await editMessageText(token, chatId, messageId, text, markup);
}

async function sendClassicsSearch(token, chatId, query) {
  const q = query.toLowerCase().trim();
  const matches = CLASSICS.filter(b =>
    (b.title && b.title.toLowerCase().includes(q)) ||
    (b.author && b.author.toLowerCase().includes(q)) ||
    (b.description && b.description.toLowerCase().includes(q)) ||
    (b.genre && b.genre.toLowerCase().includes(q))
  );

  if (matches.length === 0) {
    const text =
      `🔍 <b>No matching classics found for \"${escapeHtml(query)}\"</b>\n\n` +
      `Try searching by author (e.g. <code>Austen</code>, <code>Doyle</code>, <code>Shelley</code>, <code>Marcus</code>) ` +
      `or explore curated categories:`;
    const markup = {
      inline_keyboard: [
        [{ text: "📚 Browse All Categories", callback_data: "cmd_classics" }],
        [{ text: "🎲 Random Book", callback_data: "cmd_random_book" }]
      ]
    };
    await sendMessage(token, chatId, text, markup);
    return;
  }

  let text = `🔍 <b>Found ${matches.length} Classic(s) for \"${escapeHtml(query)}\":</b>\n\n`;
  matches.slice(0, 6).forEach((b, i) => {
    text += `<b>${i + 1}. ${escapeHtml(b.title)}</b> — <i>${escapeHtml(b.author)}</i> (${escapeHtml(b.genre)})\n`;
    if (b.quote) {
      text += `💬 <i>\"${escapeHtml(b.quote)}\"</i>\n`;
    }
    text += `\n`;
  });

  if (matches.length > 6) {
    text += `<i>...and ${matches.length - 6} more titles.</i>\n\n`;
  }
  text += `📥 <i>Read these titles inside Vern TTS!</i>`;

  const markup = {
    inline_keyboard: [
      [
        { text: "📚 Browse Categories", callback_data: "cmd_classics" },
        { text: "⬇️ Download App", callback_data: "cmd_download" }
      ],
      [
        { text: "⬅️ Back to Main Menu", callback_data: "cmd_menu" }
      ]
    ]
  };

  await sendMessage(token, chatId, text, markup);
}

async function sendContactChannels(token, chatId, messageId = null) {
  const text = "<b>Stay in touch.</b>\n\nFollow @myreader_veritas for updates and reading ideas.\n\nNeed help? Email myreader.veritas@gmail.com. For a bug report, include your Android version, document format, and what happened.";
  const markup = {"inline_keyboard":[[{"text":"Telegram channel","url":"https://t.me/myreader_veritas"},{"text":"Website","url":"https://fhes-tus.github.io/Vern-TTS/"}],[{"text":"Source code","url":"https://github.com/fhes-tus/Vern-TTS"}],[{"text":"Main menu","callback_data":"cmd_menu"}]]};
  if (messageId) await editMessageText(token, chatId, messageId, text, markup);
  else await sendMessage(token, chatId, text, markup);
}

async function sendFaqMenu(token, chatId, messageId = null) {
  const text = '<b>A few useful answers.</b>\n\nChoose a topic below.';
  const markup = {"inline_keyboard":[[{"text":"Voices and offline listening","callback_data":"faq_voice"}],[{"text":"Reading and files","callback_data":"faq_reading"},{"text":"Notes and studying","callback_data":"faq_study"}],[{"text":"Privacy","callback_data":"faq_security"},{"text":"Updates and backups","callback_data":"faq_updates"}],[{"text":"Main menu","callback_data":"cmd_menu"}]]};
  if (messageId) await editMessageText(token, chatId, messageId, text, markup);
  else await sendMessage(token, chatId, text, markup);
}

async function sendFaqDetail(token, chatId, messageId, topic) {
  const legacy = {audio:'voice',formats:'reading',display:'reading'};
  const detail = CONTENT.faqs[legacy[topic] || topic] || CONTENT.faqs.reading;
  const text = '<b>' + escapeHtml(detail.title) + '</b>\n\n' + detail.body;
  const markup = {inline_keyboard: [[{text:'Back to FAQs', callback_data:'cmd_faq'},{"text":"Main menu","callback_data":"cmd_menu"}]]};
  if (messageId) await editMessageText(token, chatId, messageId, text, markup);
  else await sendMessage(token, chatId, text, markup);
}

async function handleMessage(message, env) {
  const token = env.TELEGRAM_BOT_TOKEN;
  if (!Number.isSafeInteger(message.chat?.id)) return;
  const chatId = message.chat.id;
  const rawText = String(message.text || message.caption || "").slice(0, 4096).trim();
  const text = rawText.toLowerCase();

  // If message contains media without text
  if (!rawText && (message.photo || message.video || message.document || message.audio || message.voice)) {
    const mediaReply =
      "📸 <b>Media Received!</b>\n\n" +
      "Vern TTS is an offline Android reading workstation supporting <b>PDF, EPUB, TXT, DOCX, and Markdown (.md)</b> documents.\n\n" +
      "• <b>To read books:</b> Transfer files to your Android device storage and open them in Vern TTS.\n" +
      "• <b>To explore classics:</b> Tap <b>Classics</b> to browse 35+ curated public domain books pre-loaded with authentic covers.\n\n" +
      "<i>Select an option below to get started:</i>";
    const markup = {
      inline_keyboard: [
        [{ text: "⬇️ Download App", callback_data: "cmd_download" }, { text: "📋 What's New", callback_data: "cmd_changelog" }],
        [{ text: "📚 Curated Classics", callback_data: "cmd_classics" }, { text: "🏠 Main Menu", callback_data: "cmd_menu" }]
      ]
    };
    await sendMessage(token, chatId, mediaReply, markup);
    return;
  }

  if (/^\/milestone(@\w+)?(?:\s|$)/.test(text)) {
    await sendMilestone(token, chatId);
  } else if (/^\/customize(@\w+)?(?:\s|$)/.test(text)) {
    await sendFeatureDetail(token, chatId, null, 'tones');
  } else if (/^\/security(@\w+)?(?:\s|$)/.test(text)) {
    await sendFaqDetail(token, chatId, null, 'security');
  } else if (text.startsWith("/start") || text.startsWith("/help") || text === "menu") {
    await sendWelcome(token, chatId);
  } else if (text.startsWith("/download") || text === "download" || text.includes("apk")) {
    await sendDownloadInfo(token, chatId);
  } else if (
    text.startsWith("/changelog") || text.startsWith("/release") || text.startsWith("/whatsnew") ||
    text.startsWith("/update") || text.startsWith("/notes") || text.includes("what's new") ||
    text.includes("release note") || text.includes("changelog")
  ) {
    await sendChangelogContent(token, chatId);
  } else if (text.startsWith("/features") || text === "features" || text === "feature") {
    await sendFeaturesMenu(token, chatId);
  } else if (text.startsWith("/study") || text.includes("flashcard") || text.includes("study hub") || text.includes("recall")) {
    await sendFeatureDetail(token, chatId, null, "study");
  } else if (text.startsWith("/memos") || text.startsWith("/memo") || text.includes("voice memo") || text.includes("voice note")) {
    await sendFeatureDetail(token, chatId, null, "memos");
  } else if (text.startsWith("/insights") || text.includes("wpm") || text.includes("reading speed") || text.includes("streak")) {
    await sendFeatureDetail(token, chatId, null, "insights");
  } else if (text.startsWith("/voice") || text.startsWith("/guide") || text.startsWith("/tips") || text.includes("voice") || text.includes("robotic")) {
    await sendGuideMasterclass(token, chatId);
  } else if (text.startsWith("/random") || text.includes("random book") || text.includes("quote")) {
    await sendRandomBook(token, chatId);
  } else if (text.startsWith("/search")) {
    const query = rawText.slice(7).trim().slice(0, 160);
    if (!query) {
      await sendMessage(token, chatId, "🔍 <i>Please specify a search term. Example:</i> <code>/search austen</code>");
    } else {
      await sendClassicsSearch(token, chatId, query);
    }
  } else if (text.startsWith("/classics") || text === "classics" || text === "books" || text === "catalog") {
    await sendClassicsMenu(token, chatId);
  } else if (text.startsWith("/faq") || text === "faq" || text === "faqs" || text.includes("question")) {
    await sendFaqMenu(token, chatId);
  } else if (text.startsWith("/contact") || text.startsWith("/community") || text.includes("channel") || text.includes("developer") || text.includes("support")) {
    await sendContactChannels(token, chatId);
  } else if (["library", "import", "add book", "add to library", "open file"].some(w => text.includes(w))) {
    const t =
      "📚 <b>How to Add Books & Documents to Vern TTS</b>\n\n" +
      "1. <b>Storage Files:</b> Place any PDF, EPUB, TXT, DOCX, or MD file on your device (Internal Storage or Downloads).\n" +
      "2. <b>Instant Import:</b> In Vern TTS, tap <b>+ Add Document</b> or use the batch file browser to import files directly into your personal library.\n" +
      "3. <b>Classics Shelf:</b> Tap the <b>Classics</b> tab inside the app to download any of the 35+ curated public domain books with authentic covers.\n\n" +
      "<i>All imported documents remain 100% private and offline on your device sandbox.</i>";
    const m = {
      inline_keyboard: [
        [{ text: "⬇️ Download App", callback_data: "cmd_download" }, { text: "📚 Browse Classics", callback_data: "cmd_classics" }],
        [{ text: "🏠 Main Menu", callback_data: "cmd_menu" }]
      ]
    };
    await sendMessage(token, chatId, t, m);
  } else {
    const fallback =
      `🤖 <b>Vern TTS Assistant</b>\n\n` +
      `I can help you explore offline reading, on-device neural speech, embedded voice memos, the spaced-repetition study hub, and book classics.\n\n` +
      `<i>Select an option below or type <code>/help</code> for available commands:</i>`;
    await sendMessage(token, chatId, fallback, {
      inline_keyboard: [
        [
          { text: "⬇️ Download App", callback_data: "cmd_download" },
          { text: "📋 What's New", callback_data: "cmd_changelog" }
        ],
        [
          { text: "🌟 Features", callback_data: "cmd_features" },
          { text: "📚 Curated Classics", callback_data: "cmd_classics" }
        ],
        [
          { text: "🧠 Study Hub", callback_data: "feat_study" },
          { text: "🎙 Voice Memos", callback_data: "feat_memos" }
        ],
        [
          { text: "🎲 Random Classic", callback_data: "cmd_random_book" },
          { text: "❓ Help and FAQs", callback_data: "cmd_faq" }
        ]
      ]
    });
  }
}

async function handleCallbackQuery(cbQuery, env) {
  const token = env.TELEGRAM_BOT_TOKEN;
  if (!Number.isSafeInteger(cbQuery.message?.chat?.id) || typeof cbQuery.data !== "string") return;
  const chatId = cbQuery.message.chat.id;
  const messageId = cbQuery.message.message_id;
  const data = cbQuery.data;

  await answerCallbackQuery(token, cbQuery.id);

  if (data === "cmd_milestone") {
    await sendMilestone(token, chatId);
  } else if (data === "cmd_menu") {
    await sendWelcome(token, chatId, messageId);
  } else if (data === "cmd_download") {
    await sendDownloadInfo(token, chatId, messageId);
  } else if (data === "cmd_changelog" || data === "cmd_release") {
    await sendChangelogContent(token, chatId, messageId);
  } else if (data === "cmd_features") {
    await sendFeaturesMenu(token, chatId, messageId);
  } else if (data === "cmd_guide" || data === "cmd_guide_voice") {
    await sendGuideMasterclass(token, chatId, messageId);
  } else if (data === "cmd_classics") {
    await sendClassicsMenu(token, chatId, messageId);
  } else if (data === "cmd_random_book") {
    await sendRandomBook(token, chatId, messageId);
  } else if (data.startswith ? data.startswith("cls_cat_") : data.startsWith("cls_cat_")) {
    const category = data.replace("cls_cat_", "");
    await sendClassicsCategory(token, chatId, messageId, category);
  } else if (data === "cmd_contact") {
    await sendContactChannels(token, chatId, messageId);
  } else if (data === "cmd_faq") {
    await sendFaqMenu(token, chatId, messageId);
  } else if (data.startsWith("feat_")) {
    const topic = data.replace("feat_", "");
    await sendFeatureDetail(token, chatId, messageId, topic);
  } else if (data.startsWith("faq_")) {
    const topic = data.replace("faq_", "");
    await sendFaqDetail(token, chatId, messageId, topic);
  }
}
