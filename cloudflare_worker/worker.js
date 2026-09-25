/**
 * Vern TTS Autonomous Telegram Bot Worker (Comprehensive Workstation Edition)
 * Serverless 24/7 Telegram Webhook Handler on Cloudflare Workers
 * Offline-First Android Reading Workstation Knowledge Center & Assistant.
 */

const BOT_TOKEN = "8285832720:AAHM20ABnUrB1VzLM81eUgkO6NdXnb7Je-o";
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
      return new Response("Vern TTS Telegram Bot Webhook Active (Workstation Edition)", { status: 200 });
    }

    try {
      const update = await request.json();
      if (update.message) {
        await handleMessage(update.message, env);
      } else if (update.callback_query) {
        await handleCallbackQuery(update.callback_query, env);
      }
      return new Response("OK", { status: 200 });
    } catch (err) {
      return new Response("Error: " + err.message, { status: 200 });
    }
  }
};

/* --- Telegram API Helpers --- */

async function sendMessage(token, chatId, text, replyMarkup = null) {
  const url = `https://api.telegram.org/bot${token}/sendMessage`;
  const payload = {
    chat_id: chatId,
    text: text,
    parse_mode: "HTML",
    disable_web_page_preview: true
  };
  if (replyMarkup) {
    payload.reply_markup = replyMarkup;
  }
  return fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

async function editMessageText(token, chatId, messageId, text, replyMarkup = null) {
  const url = `https://api.telegram.org/bot${token}/editMessageText`;
  const payload = {
    chat_id: chatId,
    message_id: messageId,
    text: text,
    parse_mode: "HTML",
    disable_web_page_preview: true
  };
  if (replyMarkup) {
    payload.reply_markup = replyMarkup;
  }
  return fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
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
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/* --- Dynamic Release Resolution --- */

async function getLatestRelease() {
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
      headers: { "User-Agent": "Vern-Cloudflare-Worker" }
    });
    if (res.ok) {
      const data = await res.json();
      const tag = data.tag_name || "v2.5.0";
      return {
        tag: tag,
        name: data.name || `Vern TTS ${tag}`,
        arm64_url: `${GITHUB_URL}/releases/download/${tag}/Veritas-Reader-${tag}-arm64-v8a-release.apk`,
        universal_url: `${GITHUB_URL}/releases/download/${tag}/Veritas-Reader-${tag}-universal-release.apk`,
        arm32_url: `${GITHUB_URL}/releases/download/${tag}/Veritas-Reader-${tag}-armeabi-v7a-release.apk`
      };
    }
  } catch (e) {
    console.log("Could not fetch latest release:", e);
  }
  const tag = "v2.5.1";
  return {
    tag: tag,
    name: `Vern TTS ${tag}`,
    arm64_url: `${GITHUB_URL}/releases/download/${tag}/Veritas-Reader-${tag}-arm64-v8a-release.apk`,
    universal_url: `${GITHUB_URL}/releases/download/${tag}/Veritas-Reader-${tag}-universal-release.apk`,
    arm32_url: `${GITHUB_URL}/releases/download/${tag}/Veritas-Reader-${tag}-armeabi-v7a-release.apk`
  };
}

/* --- Navigation & Menu Handlers --- */

async function sendWelcome(token, chatId, messageId = null) {
  const rel = await getLatestRelease();
  const text = 
    `👋 <b>Welcome to the Vern TTS Knowledge Center!</b>\n\n` +
    `<b>Vern TTS is an offline-first Android reading workstation. Built with on-device speech synthesis, a spaced-repetition study hub, embedded voice memos, and zero telemetry tracking.</b>\n\n` +
    `⚡️ <b>Workstation Capabilities:</b>\n` +
    `• 🎙 <b>Embedded Voice Memos</b>: Spoken margin notes & audio reflections attached to any passage.\n` +
    `• 🧠 <b>Spaced-Repetition Study Hub</b>: Active recall flashcards, Leitner review scheduling, & retention scoring.\n` +
    `• 🎧 <b>On-Device Neural Speech</b>: High-fidelity narration with lockscreen media controls & sentence sync.\n` +
    `• 📄 <b>Universal Document Studio</b>: PDF, EPUB, TXT, DOCX, & Markdown with automatic prose synopses.\n` +
    `• 👁 <b>4 Unified Paper Tones</b>: Clean White, Warm Sepia, Soft Dark, & True AMOLED Black.\n` +
    `• 📚 <b>35+ Curated Classics</b>: Built-in library pre-loaded with authentic published first-edition covers.\n` +
    `• 📊 <b>Local Reading Tracker</b>: WPM speed, daily reading minutes, streaks, and local SQLite data.\n` +
    `• 🛡 <b>Zero Surveillance</b>: 0 ads, 0 trackers, 0 telemetry, and no account required.\n\n` +
    `<i>Current Verified Build: <b>${rel.tag}</b> • Tap any section below to explore:</i>`;

  const markup = {
    inline_keyboard: [
      [
        { text: "⬇️ Download APKs", callback_data: "cmd_download" },
        { text: "🌟 Workstation Features", callback_data: "cmd_features" }
      ],
      [
        { text: "🧠 Study Hub & Cards", callback_data: "feat_study" },
        { text: "🎙 Voice Memos", callback_data: "feat_memos" }
      ],
      [
        { text: "🎧 Voice Setup Guide", callback_data: "cmd_guide_voice" },
        { text: "📚 Curated Classics", callback_data: "cmd_classics" }
      ],
      [
        { text: "❓ In-Depth FAQs", callback_data: "cmd_faq" },
        { text: "📬 Official Channels", callback_data: "cmd_contact" }
      ],
      [
        { text: "📢 Telegram Community", url: CHANNEL_URL },
        { text: "🌐 Official Website", url: WEBSITE_URL }
      ]
    ]
  };

  if (messageId) {
    await editMessageText(token, chatId, messageId, text, markup);
  } else {
    await sendMessage(token, chatId, text, markup);
  }
}

async function sendDownloadInfo(token, chatId, messageId = null) {
  const rel = await getLatestRelease();
  const text = 
    `📦 <b>Download Vern TTS (${rel.tag} Latest Stable)</b>\n\n` +
    `Select the optimal build for your device architecture:\n\n` +
    `• <a href="${rel.arm64_url}"><b>64-bit ARM APK (arm64-v8a)</b></a> ⭐️ <i>Recommended</i>\n` +
    `  Tailored for modern Android devices (Android 8.0 through Android 15). Smallest footprint and highest execution performance.\n\n` +
    `• <a href="${rel.universal_url}"><b>Universal Release APK</b></a>\n` +
    `  Contains runtime binaries for all chipsets. Compatible with any supported phone, tablet, or Chromebook.\n\n` +
    `• <a href="${rel.arm32_url}"><b>32-bit Legacy APK (armeabi-v7a)</b></a>\n` +
    `  For older devices and budget hardware architectures running 32-bit chipsets.\n\n` +
    `🛡 <b>Safe Sideloading Note:</b>\n` +
    `If Android prompts <i>\"Install from Unknown Sources\"</i>, tap <b>Allow</b>. This is standard for apps installed outside Google Play. Vern contains zero ads, zero telemetry, and is 100% open source under the Apache 2.0 license.\n\n` +
    `⏳ <b>Google Play Store:</b> Application review is currently in progress. The official Play Store listing will be available shortly!`;

  const markup = {
    inline_keyboard: [
      [
        { text: `⬇️ Download arm64 APK (${rel.tag})`, url: rel.arm64_url }
      ],
      [
        { text: "📦 All Builds on GitHub", url: `${GITHUB_URL}/releases/latest` },
        { text: "🌐 Official Website", url: WEBSITE_URL }
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

async function sendFeaturesMenu(token, chatId, messageId = null) {
  const text = 
    `🌟 <b>Vern TTS Workstation Architecture</b>\n\n` +
    `Vern TTS transforms your Android device into a focused, distraction-free reading workstation.\n\n` +
    `Select an engineering pillar below to inspect how it works:`;

  const markup = {
    inline_keyboard: [
      [
        { text: "🎙 Embedded Voice Memos", callback_data: "feat_memos" },
        { text: "🧠 Spaced-Repetition Study Hub", callback_data: "feat_study" }
      ],
      [
        { text: "🎧 On-Device Speech Synthesis", callback_data: "feat_speech" },
        { text: "📄 Document Studio & Formats", callback_data: "feat_docs" }
      ],
      [
        { text: "👁 4 Unified Paper Tones", callback_data: "feat_tones" },
        { text: "📊 Reading Tracker & Insights", callback_data: "feat_insights" }
      ],
      [
        { text: "📖 Dual Reading Modes", callback_data: "feat_modes" },
        { text: "🛡 100% Offline Privacy", callback_data: "feat_privacy" }
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

async function sendFeatureDetail(token, chatId, messageId, topic) {
  let title = "";
  let body = "";

  if (topic === "memos") {
    title = "🎙 <b>Embedded Voice Memos & Spoken Annotations</b>";
    body = 
      `• <b>Frictionless Spoken Notes</b>: While reading any book or document, tap any paragraph or margin to record audio reflections without breaking your concentration.\n` +
      `• <b>Synchronized Timestamps</b>: Every voice memo is tied directly to the exact sentence and chapter where it was recorded.\n` +
      `• <b>In-Line Playback</b>: Tap the audio memo icon beside any highlighted passage to play back your recorded thoughts directly on the page.\n` +
      `• <b>100% Local Storage</b>: Audio recordings are stored locally inside the application sandbox with zero cloud transmission or AI data scraping.`;
  } else if (topic === "study") {
    title = "🧠 <b>Spaced-Repetition Study Hub & Active Recall</b>";
    body = 
      `• <b>Active Recall Flashcards</b>: Highlight notable quotes, definitions, or complex arguments to instantly generate interactive Question / Answer study cards.\n` +
      `• <b>Spaced-Repetition Scheduling</b>: Review cards using calibrated intervals that reinforce memory retention right before forgetting sets in.\n` +
      `• <b>Study Decks by Book</b>: Filter cards by specific documents, tags, or browse your master library deck.\n` +
      `• <b>Mastery Progress</b>: Track card retention rates, mastery levels, and review intervals locally on your device.`;
  } else if (topic === "speech") {
    title = "🎧 <b>On-Device Neural Speech Synthesis Engine</b>";
    body = 
      `• <b>Zero-Cloud Latency</b>: All speech generation runs locally using on-device engines. Zero audio streaming delays and zero data sent to servers.\n` +
      `• <b>Continuous Background Audio</b>: Keep listening seamlessly while multitasking, using other apps, or with your phone display turned off.\n` +
      `• <b>Lockscreen & Notification Media Controls</b>: Native Android media player controls with Play, Pause, Previous/Next sentence skip, and progress scrub.\n` +
      `• <b>Synchronized Sentence Highlighting</b>: Words and active sentences dynamically illuminate on screen in sync with the spoken voice.\n` +
      `• <b>In-App Engine Customization</b>: Tap the speaker icon while reading to switch between Vern Lite, Vern Studio, Google Speech, or Samsung engines, with real-time rate (0.5x–3.0x) and pitch tuning.`;
  } else if (topic === "insights") {
    title = "📊 <b>Reading Tracker & Local Insights</b>";
    body = 
      `• <b>Reading Speed (WPM)</b>: Accurately computes your personal Words-Per-Minute reading pace across different genres.\n` +
      `• <b>Daily Reading Minutes & Streaks</b>: Visual tracking of reading time and consistency to cultivate steady daily reading habits.\n` +
      `• <b>Book Completion Statistics</b>: Pages turned, chapters read, and milestones achieved across your bookshelf.\n` +
      `• <b>Air-Gapped Analytics</b>: All statistics remain exclusively inside your phone's encrypted SQLite storage. No remote analytics SDKs.`;
  } else if (topic === "docs") {
    title = "📄 <b>Universal Document Studio & Formats</b>";
    body = 
      `• <b>Supported Formats</b>: PDF, EPUB, TXT, DOCX (Word), and Markdown (.md).\n` +
      `• <b>Intelligent Heuristic Synopsis</b>: Automatically bypasses Gutenberg boilerplate headers, legal notices, and licenses to extract the clean opening prose as a synopsis.\n` +
      `• <b>Dedicated PDF Media Bar</b>: Tailored PDF controls featuring 1-tap screen orientation switching (Portrait / Landscape) and Next sentence skip.\n` +
      `• <b>Universal Storage Access</b>: Import documents directly from Internal Storage, SD Cards, Google Drive, or Downloads.`;
  } else if (topic === "tones") {
    title = "👁 <b>4 Unified Paper Tones & Eye Comfort</b>";
    body = 
      `Vern features 4 carefully calibrated color palettes designed for maximum reading endurance:\n\n` +
      `1. <b>Clean White</b> (Daylight): Crisp, high-contrast black typography on neutral white.\n` +
      `2. <b>Warm Sepia</b> (Extended Reading): Warm cream background with softened amber tint that eliminates blue-light eye strain during long sessions.\n` +
      `3. <b>Soft Dark</b> (Evening): Gentle slate-slate contrast that eliminates harsh glare in low-light environments.\n` +
      `4. <b>AMOLED Black</b> (Night / Battery Saver): Pure 0% luminance black (#000000) pixels that completely deactivate OLED subpixels, saving massive battery life.`;
  } else if (topic === "modes") {
    title = "📖 <b>Dual Reading Modes & Navigation</b>";
    body = 
      `• <b>Paged Reading Mode</b>: Authentic physical book simulation with horizontal page flips, subtle page shadows, and haptic feedback.\n` +
      `• <b>Continuous Flow Mode</b>: Smooth, frictionless vertical scrolling with responsive typography that automatically reflows to fit your screen size.\n` +
      `• <b>Sentence Snap</b>: Tap any sentence anywhere on a page to immediately jump audio narration directly to that sentence.\n` +
      `• <b>Fluid Library Gestures</b>: Drag-and-drop to reorder your bookshelf, hold-to-select for batch actions, and swipe to bookmark.`;
  } else {
    title = "🛡 <b>100% Offline Privacy Guarantee</b>";
    body = 
      `• <b>Zero Analytics & Telemetry</b>: No Google Analytics, no Firebase tracking, no Crashlytics data collection.\n` +
      `• <b>Zero Accounts Required</b>: No logins, passwords, or emails. Download and start reading in 2 seconds.\n` +
      `• <b>Zero Ads Forever</b>: Clean, distraction-free reading with zero banners, popups, or subscriptions.\n` +
      `• <b>Local SQLite Storage</b>: All reading history, bookmarks, and notes are stored strictly on your phone's internal sandbox.\n` +
      `• <b>Open Source</b>: Complete source code is auditable on GitHub under the Apache 2.0 license.`;
  }

  const text = `${title}\n\n${body}`;
  const markup = {
    inline_keyboard: [
      [
        { text: "⬅️ Back to Features", callback_data: "cmd_features" },
        { text: "🏠 Main Menu", callback_data: "cmd_menu" }
      ]
    ]
  };

  if (messageId) {
    await editMessageText(token, chatId, messageId, text, markup);
  } else {
    await sendMessage(token, chatId, text, markup);
  }
}

async function sendGuideMasterclass(token, chatId, messageId = null) {
  const text = 
    `🎧 <b>Masterclass: How to Customize & Preview Voices</b>\n\n` +
    `Vern TTS gives you complete control over narration directly <b>inside the app</b>:\n\n` +
    `<b>1. In-App Voice Engine & Picker:</b>\n` +
    `While reading any document, tap the <b>Speaker icon</b> on the bottom player bar. Tap the voice picker to switch between available engines (such as Vern Lite, Vern Studio, Google Speech, or Samsung) and preview different vocal styles.\n\n` +
    `<b>2. Real-Time Pitch & Velocity:</b>\n` +
    `Use the in-app sliders to adjust speech speed from <b>0.5x up to 3.0x</b> and tune vocal pitch to your preference.\n\n` +
    `<b>3. Optional Extra Voice Packs:</b>\n` +
    `If you want additional neural accents or languages, you can also install them in Android Settings > Accessibility > Text-to-Speech output. Vern TTS will automatically detect and list them in your in-app voice menu!`;

  const markup = {
    inline_keyboard: [
      [
        { text: "⬇️ Download Vern TTS", callback_data: "cmd_download" },
        { text: "❓ FAQs", callback_data: "cmd_faq" }
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
  const text = 
    `📬 <b>Vern TTS Official Channels & Access</b>\n\n` +
    `Connect directly with the project and maintain data sovereignty:\n\n` +
    `• 📢 <b>Telegram Community:</b> @myreader_veritas\n` +
    `• 🐙 <b>GitHub Repository:</b> <a href="${GITHUB_URL}">fhes-tus/Vern-TTS</a>\n` +
    `• 𝕏 <b>X (Twitter):</b> <a href="${TWITTER_URL}">@_1st2us</a>\n` +
    `• ✉️ <b>Support & Inquiries:</b> <code>${EMAIL_ADDRESS}</code>\n` +
    `• 🌐 <b>Official Website:</b> <a href="${WEBSITE_URL}">${WEBSITE_URL}</a>\n\n` +
    `<i>Open source under Apache 2.0 license. Zero telemetry, zero corporate surveillance.</i>`;

  const markup = {
    inline_keyboard: [
      [
        { text: "📢 Telegram Community", url: CHANNEL_URL },
        { text: "🐙 GitHub Repository", url: GITHUB_URL }
      ],
      [
        { text: "𝕏 Follow on X", url: TWITTER_URL },
        { text: "🌐 Official Website", url: WEBSITE_URL }
      ],
      [
        { text: "✉️ Email Support", url: `mailto:${EMAIL_ADDRESS}?subject=Vern%20TTS%20Inquiry` }
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

async function sendFaqMenu(token, chatId, messageId = null) {
  const text = 
    `❓ <b>Vern TTS Knowledge Base & FAQs</b>\n\n` +
    `Choose a category to find comprehensive answers:`;

  const markup = {
    inline_keyboard: [
      [
        { text: "🎧 Voice Quality & Audio", callback_data: "faq_audio" },
        { text: "📄 Formats & Scanned PDFs", callback_data: "faq_formats" }
      ],
      [
        { text: "👁 Paper Tones & AMOLED", callback_data: "faq_display" },
        { text: "📝 Study Cards & Voice Memos", callback_data: "faq_study" }
      ],
      [
        { text: "🛡 Privacy & Safe Sideloading", callback_data: "faq_security" },
        { text: "🔄 Updates & Progress", callback_data: "faq_updates" }
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

async function sendFaqDetail(token, chatId, messageId, topic) {
  let title = "";
  let body = "";

  if (topic === "audio") {
    title = "🎧 <b>Audio & Speech Engine FAQs</b>";
    body = 
      `<b>Q: Why does the voice sound robotic, and how do I customize it?</b>\n` +
      `A: You can customize voices directly inside the app! While reading, tap the speaker icon on the bottom player bar to open the Voice Picker. You can switch between engines (Vern Lite, Vern Studio, Google, or Samsung) and select smoother voices. You can also download extra neural voice packs in Android Settings > Text-to-speech, and Vern will automatically list them in the in-app picker.\n\n` +
      `<b>Q: Can I listen with my phone screen turned off?</b>\n` +
      `A: Yes! Vern TTS features a persistent Android Media Notification service. It continues narration in the background while your phone is locked or while using other apps.\n\n` +
      `<b>Q: Can I change playback speed and pitch?</b>\n` +
      `A: Yes. Tap the speaker icon on the bottom reading bar to adjust speech rate from 0.5x up to 3.0x, tune vocal pitch, and select specific voice engines.\n\n` +
      `<b>Q: Does text highlight as the voice speaks?</b>\n` +
      `A: Yes! Active sentences and paragraphs dynamically highlight on screen in sync with the spoken audio.`;
  } else if (topic === "formats") {
    title = "📄 <b>Document Formats & Scanned PDFs</b>";
    body = 
      `<b>Q: What file formats are supported?</b>\n` +
      `A: Vern TTS supports PDF, EPUB, TXT, DOCX (Microsoft Word), and Markdown (.md).\n\n` +
      `<b>Q: Can Vern read scanned image-only PDFs?</b>\n` +
      `A: Standard PDFs with digital text layers extract and read instantly. Image-only scanned PDFs (photocopies without embedded text) require OCR before text can be extracted for speech.\n\n` +
      `<b>Q: Where are the 35+ classic books stored?</b>\n` +
      `A: The classics catalog metadata is pre-indexed inside the app. When you tap a book to read, it downloads the clean public domain text on-demand and saves it locally.`;
  } else if (topic === "display") {
    title = "👁 <b>Paper Tones & Display FAQs</b>";
    body = 
      `<b>Q: How does AMOLED Black save battery?</b>\n` +
      `A: On OLED/AMOLED smartphone screens, black pixels (#000000) are physically turned OFF completely and consume 0% power. Reading in AMOLED Black can extend your battery life by up to 40% during long sessions.\n\n` +
      `<b>Q: Why is Warm Sepia recommended for reading?</b>\n` +
      `A: Blue light from white screens disrupts melatonin production and causes eye fatigue. Warm Sepia filters out harsh high-frequency blue spectrums, creating a comforting experience identical to aged physical book pages.\n\n` +
      `<b>Q: Can I switch between paged flips and vertical scrolling?</b>\n` +
      `A: Yes! You can toggle between physical-style Paged Mode (with page-turn haptics) and continuous vertical Flow Mode at any time.`;
  } else if (topic === "study") {
    title = "📝 <b>Study Hub & Voice Memos FAQs</b>";
    body = 
      `<b>Q: How do Study Cards work?</b>\n` +
      `A: While reading, highlight any passage or sentence and tap <i>Save to Study Hub</i>. You can review them anytime with interactive flip cards (Question / Answer) to memorize key concepts using spaced repetition.\n\n` +
      `<b>Q: How do Embedded Voice Memos work?</b>\n` +
      `A: Tap on any paragraph to record quick spoken thoughts or reflections. They attach directly to that line with a timestamp and play back inline with zero cloud uploads.\n\n` +
      `<b>Q: Does Vern track my reading speed?</b>\n` +
      `A: Yes! Vern calculates your Words Per Minute (WPM), total minutes read, pages completed, and streaks. All stats are calculated 100% locally on your phone.`;
  } else if (topic === "security") {
    title = "🛡 <b>Privacy & Safe Sideloading FAQs</b>";
    body = 
      `<b>Q: Why does Android show \"Unknown App / Source\" when installing?</b>\n` +
      `A: Android displays this warning for *any* application installed from an APK file outside the Google Play Store. It is standard Android behavior. Vern TTS contains zero malware, zero trackers, and is 100% open source on GitHub.\n\n` +
      `<b>Q: Does Vern require any internet permissions?</b>\n` +
      `A: Reading, text extraction, speech generation, voice memos, and study cards all run 100% offline. Internet is only used if you choose to download a new classic from the catalog.\n\n` +
      `<b>Q: Does Vern collect or sell my data?</b>\n` +
      `A: Never. Vern has no telemetry, no tracking IDs, no ads, and no user accounts. Your library and progress remain strictly on your device.`;
  } else if (topic === "updates") {
    title = "🔄 <b>Updating & Reading Progress FAQs</b>";
    body = 
      `<b>Q: Will I lose my books and bookmarks when I update?</b>\n` +
      `A: No! When you install a newer version over your existing Vern TTS app, Android preserves your SQLite database, bookmarks, reading progress, and custom settings automatically.\n\n` +
      `<b>Q: How do I get notified of updates?</b>\n` +
      `A: Join our official Telegram channel <b>@myreader_veritas</b>! New releases and changelogs are published there automatically as soon as they are ready.`;
  }

  const text = `${title}\n\n${body}`;
  const markup = {
    inline_keyboard: [
      [
        { text: "⬅️ Back to FAQs", callback_data: "cmd_faq" },
        { text: "🏠 Main Menu", callback_data: "cmd_menu" }
      ]
    ]
  };

  await editMessageText(token, chatId, messageId, text, markup);
}

/* --- Core Message & Callback Routers --- */

async function handleMessage(message, env) {
  const token = env?.TELEGRAM_BOT_TOKEN || BOT_TOKEN;
  const chatId = message.chat.id;
  const rawText = (message.text || "").trim();
  const text = rawText.toLowerCase();

  if (text.startsWith("/start") || text.startsWith("/help") || text === "menu") {
    await sendWelcome(token, chatId);
  } else if (text.startsWith("/download") || text === "download" || text.includes("apk")) {
    await sendDownloadInfo(token, chatId);
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
    const query = rawText.slice(7).trim();
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
  } else {
    const fallback = 
      `🤖 <b>Vern TTS Assistant</b>\n\n` +
      `I can help you explore offline reading, on-device neural speech, embedded voice memos, the spaced-repetition study hub, and book classics.\n\n` +
      `<i>Select an option below or type <code>/help</code> for available commands:</i>`;
    await sendMessage(token, chatId, fallback, {
      inline_keyboard: [
        [
          { text: "⬇️ Download App", callback_data: "cmd_download" },
          { text: "🌟 Workstation Features", callback_data: "cmd_features" }
        ],
        [
          { text: "🧠 Study Hub", callback_data: "feat_study" },
          { text: "🎙 Voice Memos", callback_data: "feat_memos" }
        ],
        [
          { text: "🎲 Random Classic", callback_data: "cmd_random_book" },
          { text: "❓ Knowledge Base", callback_data: "cmd_faq" }
        ]
      ]
    });
  }
}

async function handleCallbackQuery(cbQuery, env) {
  const token = env?.TELEGRAM_BOT_TOKEN || BOT_TOKEN;
  const chatId = cbQuery.message.chat.id;
  const messageId = cbQuery.message.message_id;
  const data = cbQuery.data;

  await answerCallbackQuery(token, cbQuery.id);

  if (data === "cmd_menu") {
    await sendWelcome(token, chatId, messageId);
  } else if (data === "cmd_download") {
    await sendDownloadInfo(token, chatId, messageId);
  } else if (data === "cmd_features") {
    await sendFeaturesMenu(token, chatId, messageId);
  } else if (data === "cmd_guide" || data === "cmd_guide_voice") {
    await sendGuideMasterclass(token, chatId, messageId);
  } else if (data === "cmd_classics") {
    await sendClassicsMenu(token, chatId, messageId);
  } else if (data === "cmd_random_book") {
    await sendRandomBook(token, chatId, messageId);
  } else if (data.startsWith("cls_cat_")) {
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
