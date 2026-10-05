package com.veritas.reader.ui.screens

import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.SavedDocument
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.veritas.reader.classicEdition

data class BookEditionOption(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val tag: String = ""
)

data class ClassicBookEntry(
    val id: String,
    val title: String,
    val author: String,
    val genre: String,
    val description: String,
    val downloadUrl: String,
    val estimatedMinutes: Int,
    val coverGradient: List<Color>,
    val accentColor: Color,
    val quote: String = "",
    val category: String = "Popular",
    val editions: List<BookEditionOption> = emptyList(),
    val legacyCatalogId: String = ""
) {
    val hasDirectTextDownload: Boolean
        get() = downloadUrl.substringBefore('?').endsWith(".txt", ignoreCase = true)
}

data class FreeBookSite(
    val name: String,
    val description: String,
    val icon: String,
    val url: String
)

val FREE_BOOK_SITES = listOf(
    FreeBookSite("Project Gutenberg", "70,000+ public domain classics & epubs", "🏛️", "https://www.gutenberg.org"),
    FreeBookSite("Standard Ebooks", "Beautifully typeset, modern public domain releases", "✨", "https://standardebooks.org"),
    FreeBookSite("Open Library", "Over 3 million books to borrow & read online", "📖", "https://openlibrary.org"),
    FreeBookSite("ManyBooks", "50,000+ free digital titles across all genres", "📚", "https://manybooks.net"),
    FreeBookSite("Ocean of PDF", "Comprehensive search for books & manuscripts", "🌊", "https://oceanofpdf.com")
)

val CURATED_CLASSICS = listOf(
    // --- 1. Family & Youth (Wholesome, Inspiring & All Ages) ---
    ClassicBookEntry(
        id = "classic_wizard_of_oz",
        title = "The Wonderful Wizard of Oz",
        author = "L. Frank Baum",
        genre = "Family & Youth",
        category = "Family & Youth",
        description = "Dorothy's magical journey through the Land of Oz with the Scarecrow, Tin Woodman, and Cowardly Lion.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/55/pg55.txt",
        estimatedMinutes = 180,
        coverGradient = listOf(Color(0xFF1E3A2F), Color(0xFF2E5D4B)),
        accentColor = Color(0xFFF6E05E),
        quote = "There is no place like home."
    ),
    ClassicBookEntry(
        id = "classic_alice_wonderland",
        title = "Alice in Wonderland",
        author = "Lewis Carroll",
        genre = "Family & Youth",
        category = "Family & Youth",
        description = "Alice plunges down a rabbit hole into a whimsical, topsy-turvy subterranean fantasy world.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/11/pg11.txt",
        estimatedMinutes = 120,
        coverGradient = listOf(Color(0xFF2B3A67), Color(0xFF496A81)),
        accentColor = Color(0xFFBEE3F8),
        quote = "Curiouser and curiouser!"
    ),
    ClassicBookEntry(
        id = "classic_peter_pan",
        title = "Peter Pan",
        author = "J.M. Barrie",
        genre = "Family & Youth",
        category = "Family & Youth",
        description = "The boy who wouldn't grow up whisks Wendy and her brothers away to the enchanted island of Neverland.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/16/pg16.txt",
        estimatedMinutes = 200,
        coverGradient = listOf(Color(0xFF22543D), Color(0xFF2F855A)),
        accentColor = Color(0xFF9AE6B4),
        quote = "To live will be an awfully big adventure."
    ),
    ClassicBookEntry(
        id = "classic_anne_green_gables",
        title = "Anne of Green Gables",
        author = "L.M. Montgomery",
        genre = "Family & Youth",
        category = "Family & Youth",
        description = "An imaginative, fiercely spirited red-headed orphan brings joy and vitality to Prince Edward Island.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/45/pg45.txt",
        estimatedMinutes = 360,
        coverGradient = listOf(Color(0xFF744210), Color(0xFF975A16)),
        accentColor = Color(0xFFFBD38D),
        quote = "Dear old world, you are very lovely, and I am glad to be alive in you."
    ),
    ClassicBookEntry(
        id = "classic_secret_garden",
        title = "The Secret Garden",
        author = "Frances Hodgson Burnett",
        genre = "Family & Youth",
        category = "Family & Youth",
        description = "Mary Lennox discovers an overgrown, locked garden that heals her spirit and transforms her family.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/113/pg113.txt",
        estimatedMinutes = 320,
        coverGradient = listOf(Color(0xFF234E52), Color(0xFF285E61)),
        accentColor = Color(0xFF81E6D9),
        quote = "If you look the right way, you can see that the whole world is a garden."
    ),
    ClassicBookEntry(
        id = "classic_aesop",
        title = "Aesop's Fables",
        author = "Aesop",
        genre = "Family & Youth",
        category = "Family & Youth",
        description = "Timeless moral tales starring clever animals teaching wisdom, honesty, and humility.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/21/pg21.txt",
        estimatedMinutes = 45,
        coverGradient = listOf(Color(0xFF4A5568), Color(0xFF718096)),
        accentColor = Color(0xFFE2E8F0),
        quote = "No act of kindness, no matter how small, is ever wasted."
    ),

    // --- 2. Mystery & Detective ---
    ClassicBookEntry(
        id = "classic_sherlock_holmes",
        title = "The Adventures of Sherlock Holmes",
        author = "Arthur Conan Doyle",
        genre = "Mystery",
        category = "Mystery",
        description = "Twelve brilliant detective cases solved by Sherlock Holmes and Dr. Watson at 221B Baker Street.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/1661/pg1661.txt",
        estimatedMinutes = 320,
        coverGradient = listOf(Color(0xFF1A202C), Color(0xFF2D3748)),
        accentColor = Color(0xFF63B3ED),
        quote = "When you have eliminated the impossible, whatever remains must be the truth."
    ),
    ClassicBookEntry(
        id = "classic_hound_baskervilles",
        title = "The Hound of the Baskervilles",
        author = "Arthur Conan Doyle",
        genre = "Mystery",
        category = "Mystery",
        description = "Holmes investigates the curse of a spectral hound haunting the foggy Dartmoor bogs.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/2852/pg2852.txt",
        estimatedMinutes = 240,
        coverGradient = listOf(Color(0xFF171923), Color(0xFF234E52)),
        accentColor = Color(0xFF4FD1C5),
        quote = "The world is full of obvious things which nobody ever observes."
    ),
    ClassicBookEntry(
        id = "classic_styles",
        title = "The Mysterious Affair at Styles",
        author = "Agatha Christie",
        genre = "Mystery",
        category = "Mystery",
        description = "The dazzling mystery debut introducing the eccentric and meticulous Belgian detective Hercule Poirot.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/863/pg863.txt",
        estimatedMinutes = 260,
        coverGradient = listOf(Color(0xFF4A154B), Color(0xFF611F69)),
        accentColor = Color(0xFFE9D8FD),
        quote = "Instinct is a marvelous thing. It can neither be explained nor ignored."
    ),
    ClassicBookEntry(
        id = "classic_rue_morgue",
        title = "The Murders in the Rue Morgue",
        author = "Edgar Allan Poe",
        genre = "Mystery",
        category = "Mystery",
        description = "The pioneering locked-room whodunit where detective C. Auguste Dupin solves an impossible double murder.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/2147/pg2147.txt",
        estimatedMinutes = 55,
        coverGradient = listOf(Color(0xFF321E1E), Color(0xFF4C1D1D)),
        accentColor = Color(0xFFFEB2B2),
        quote = "To observe attentively is to remember distinctly."
    ),

    // --- 3. Adventure & Exploration ---
    ClassicBookEntry(
        id = "classic_around_world_80_days",
        title = "Around the World in 80 Days",
        author = "Jules Verne",
        genre = "Adventure",
        category = "Adventure",
        description = "Phileas Fogg bets his fortune on a breathless, high-stakes global race across steamers, trains, and elephants.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/103/pg103.txt",
        estimatedMinutes = 280,
        coverGradient = listOf(Color(0xFF1C4532), Color(0xFF276749)),
        accentColor = Color(0xFF9AE6B4),
        quote = "Anything one man can imagine, other men can make real."
    ),
    ClassicBookEntry(
        id = "classic_treasure_island",
        title = "Treasure Island",
        author = "Robert Louis Stevenson",
        genre = "Adventure",
        category = "Adventure",
        description = "Young Jim Hawkins discovers a pirate treasure map, setting sail against the cunning Long John Silver.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/120/pg120.txt",
        estimatedMinutes = 260,
        coverGradient = listOf(Color(0xFF7B341E), Color(0xFF9C4221)),
        accentColor = Color(0xFFFBD38D),
        quote = "Fifteen men on the dead man's chest—Yo-ho-ho, and a bottle of rum!"
    ),
    ClassicBookEntry(
        id = "classic_call_of_the_wild",
        title = "The Call of the Wild",
        author = "Jack London",
        genre = "Adventure",
        category = "Adventure",
        description = "Stolen from California and sold into the Yukon gold rush, Buck must rediscover his primal wolf instincts.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/215/pg215.txt",
        estimatedMinutes = 110,
        coverGradient = listOf(Color(0xFF285E61), Color(0xFF319795)),
        accentColor = Color(0xFFE6FFFA),
        quote = "He was mastered by the sheer surging of life, the tidal wave of being."
    ),
    ClassicBookEntry(
        id = "classic_twenty_thousand_leagues",
        title = "20,000 Leagues Under the Sea",
        author = "Jules Verne",
        genre = "Adventure",
        category = "Adventure",
        description = "Professor Aronnax embarks on an awe-inspiring underwater voyage aboard Captain Nemo's submarine, the Nautilus.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/164/pg164.txt",
        estimatedMinutes = 420,
        coverGradient = listOf(Color(0xFF0F3460), Color(0xFF1A1A40)),
        accentColor = Color(0xFF48CAE4),
        quote = "The sea is everything. It covers seven tenths of the terrestrial globe."
    ),
    ClassicBookEntry(
        id = "classic_three_musketeers",
        title = "The Three Musketeers",
        author = "Alexandre Dumas",
        genre = "Adventure",
        category = "Adventure",
        description = "D'Artagnan joins Athos, Porthos, and Aramis in swashbuckling swordplay, state intrigue, and brotherhood.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/1257/pg1257.txt",
        estimatedMinutes = 620,
        coverGradient = listOf(Color(0xFF5C1D24), Color(0xFF782833)),
        accentColor = Color(0xFFFED7E2),
        quote = "All for one and one for all, united we stand divided we fall."
    ),

    // --- 4. Romance & Heartfelt Drama ---
    ClassicBookEntry(
        id = "classic_pride_and_prejudice",
        title = "Pride and Prejudice",
        author = "Jane Austen",
        genre = "Romance & Drama",
        category = "Romance",
        description = "The sparkling romantic battle of wits between the witty Elizabeth Bennet and proud Mr. Darcy.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/1342/pg1342.txt",
        estimatedMinutes = 420,
        coverGradient = listOf(Color(0xFF3D2338), Color(0xFF5B3254)),
        accentColor = Color(0xFFFED7E2),
        quote = "I declare after all there is no enjoyment like reading!"
    ),
    ClassicBookEntry(
        id = "classic_little_women",
        title = "Little Women",
        author = "Louisa May Alcott",
        genre = "Romance & Drama",
        category = "Romance",
        description = "The heartwarming story of the four March sisters navigating love, creativity, ambition, and family.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/514/pg514.txt",
        estimatedMinutes = 520,
        coverGradient = listOf(Color(0xFF4A3410), Color(0xFF6B4B18)),
        accentColor = Color(0xFFFEEBC8),
        quote = "I am not afraid of storms, for I am learning how to sail my ship."
    ),
    ClassicBookEntry(
        id = "classic_jane_eyre",
        title = "Jane Eyre",
        author = "Charlotte Brontë",
        genre = "Romance & Drama",
        category = "Romance",
        description = "An independent orphaned governess falls for the brooding Mr. Rochester while uncovering Thornfield's dark secret.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/1260/pg1260.txt",
        estimatedMinutes = 540,
        coverGradient = listOf(Color(0xFF2D3748), Color(0xFF4A5568)),
        accentColor = Color(0xFFE2E8F0),
        quote = "I am no bird; and no net ensnares me; I am a free human being."
    ),
    ClassicBookEntry(
        id = "classic_sense_and_sensibility",
        title = "Sense and Sensibility",
        author = "Jane Austen",
        genre = "Romance & Drama",
        category = "Romance",
        description = "The Dashwood sisters find love and learn to balance reason and intense emotion in Victorian society.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/161/pg161.txt",
        estimatedMinutes = 410,
        coverGradient = listOf(Color(0xFF44337A), Color(0xFF553C9A)),
        accentColor = Color(0xFFD6BCFA),
        quote = "It isn't what we say or think that defines us, but what we do."
    ),

    // --- 5. Life, Habits & Mindset ---
    ClassicBookEntry(
        id = "classic_ben_franklin",
        title = "The Autobiography of Benjamin Franklin",
        author = "Benjamin Franklin",
        genre = "Life & Habits",
        category = "Life & Habits",
        description = "The founding father's charming handbook on daily routines, practical virtues, and personal self-discipline.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/20203/pg20203.txt",
        estimatedMinutes = 210,
        coverGradient = listOf(Color(0xFF2C3E50), Color(0xFF34495E)),
        accentColor = Color(0xFFFAD02C),
        quote = "Energy and persistence conquer all things."
    ),
    ClassicBookEntry(
        id = "classic_as_a_man_thinketh",
        title = "As a Man Thinketh",
        author = "James Allen",
        genre = "Life & Habits",
        category = "Life & Habits",
        description = "A short, profound handbook explaining how the mind shapes personal destiny, joy, and success.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/4507/pg4507.txt",
        estimatedMinutes = 45,
        coverGradient = listOf(Color(0xFF1C3144), Color(0xFF2A475E)),
        accentColor = Color(0xFF81E6D9),
        quote = "A man is literally what he thinks, his character being the sum of his thoughts."
    ),
    ClassicBookEntry(
        id = "classic_the_prophet",
        title = "The Prophet",
        author = "Kahlil Gibran",
        genre = "Life & Habits",
        category = "Life & Habits",
        description = "Twenty-six poetic essays offering gentle reflections on love, marriage, work, friendship, and peace.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/58585/pg58585.txt",
        estimatedMinutes = 65,
        coverGradient = listOf(Color(0xFF4A3410), Color(0xFF745219)),
        accentColor = Color(0xFFFEEBC8),
        quote = "Work is love made visible."
    ),
    ClassicBookEntry(
        id = "classic_self_reliance",
        title = "Self-Reliance",
        author = "Ralph Waldo Emerson",
        genre = "Life & Habits",
        category = "Life & Habits",
        description = "An inspiring manifesto urging every person to trust their inner intuition rather than societal conformity.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/16643/pg16643.txt",
        estimatedMinutes = 50,
        coverGradient = listOf(Color(0xFF20382B), Color(0xFF335C45)),
        accentColor = Color(0xFFC6F6D5),
        quote = "To be yourself in a world trying to make you something else is great accomplishment."
    ),
    ClassicBookEntry(
        id = "classic_meditations",
        title = "Meditations",
        author = "Marcus Aurelius",
        genre = "Life & Habits",
        category = "Life & Habits",
        description = "Private journals of the Roman Emperor on stoic duty, resilience, emotional mastery, and inner peace.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/2680/pg2680.txt",
        estimatedMinutes = 180,
        coverGradient = listOf(Color(0xFF232D3F), Color(0xFF354259)),
        accentColor = Color(0xFFF6E05E),
        quote = "You have power over your mind - not outside events. Realize this, and find strength."
    ),
    ClassicBookEntry(
        id = "classic_art_of_war",
        title = "The Art of War",
        author = "Sun Tzu",
        genre = "Life & Habits",
        category = "Life & Habits",
        description = "Ancient Chinese strategic wisdom on leadership, deception, conflict resolution, and self-possession.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/132/pg132.txt",
        estimatedMinutes = 90,
        coverGradient = listOf(Color(0xFF4A1521), Color(0xFF6B1D2F)),
        accentColor = Color(0xFFFEB2B2),
        quote = "In the midst of chaos, there is also opportunity."
    ),

    // --- 6. Quick Reads (< 45 mins) ---
    ClassicBookEntry(
        id = "classic_gift_of_magi",
        title = "The Gift of the Magi",
        author = "O. Henry",
        genre = "Quick Reads",
        category = "Quick Reads",
        description = "A heartwarming Christmas masterpiece about love, selfless sacrifice, and the true meaning of giving.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/7256/pg7256.txt",
        estimatedMinutes = 25,
        coverGradient = listOf(Color(0xFF5C2D1F), Color(0xFF7A3E2B)),
        accentColor = Color(0xFFFBD38D),
        quote = "Of all who give and receive gifts, such as they are wisest."
    ),
    ClassicBookEntry(
        id = "classic_christmas_carol",
        title = "A Christmas Carol",
        author = "Charles Dickens",
        genre = "Quick Reads",
        category = "Quick Reads",
        description = "The miserly Ebenezer Scrooge is visited by three spirits who transform his cold heart into joyful generosity.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/46/pg46.txt",
        estimatedMinutes = 95,
        coverGradient = listOf(Color(0xFF1E3A2F), Color(0xFF285E61)),
        accentColor = Color(0xFF81E6D9),
        quote = "I will honour Christmas in my heart, and try to keep it all the year."
    ),
    ClassicBookEntry(
        id = "classic_yellow_wallpaper",
        title = "The Yellow Wallpaper",
        author = "Charlotte Perkins Gilman",
        genre = "Quick Reads",
        category = "Quick Reads",
        description = "A chilling psychological tale documenting a woman's battle for autonomy and creativity in a confined room.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/1952/pg1952.txt",
        estimatedMinutes = 40,
        coverGradient = listOf(Color(0xFF5F4E12), Color(0xFF8D731B)),
        accentColor = Color(0xFFFEFCBF),
        quote = "I got out at last, in spite of you and Jane!"
    ),
    ClassicBookEntry(
        id = "classic_metamorphosis",
        title = "The Metamorphosis",
        author = "Franz Kafka",
        genre = "Quick Reads",
        category = "Quick Reads",
        description = "Traveling salesman Gregor Samsa awakens one morning to find himself transformed into a giant insect.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/5200/pg5200.txt",
        estimatedMinutes = 80,
        coverGradient = listOf(Color(0xFF2D3748), Color(0xFF4A5568)),
        accentColor = Color(0xFFCBD5E0),
        quote = "One morning Gregor Samsa awoke transformed in his bed into a monstrous vermin."
    ),

    // --- 7. Epic Classics & Drama ---
    ClassicBookEntry(
        id = "classic_monte_cristo",
        title = "The Count of Monte Cristo",
        author = "Alexandre Dumas",
        genre = "Epic Classics",
        category = "Epic Classics",
        description = "The ultimate saga of betrayal, hidden treasure, enduring patience, retribution, and redemption.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/1184/pg1184.txt",
        estimatedMinutes = 850,
        coverGradient = listOf(Color(0xFF321E1E), Color(0xFF532E2E)),
        accentColor = Color(0xFFFEB2B2),
        quote = "All human wisdom is contained in these two words: Wait and Hope."
    ),
    ClassicBookEntry(
        id = "classic_frankenstein",
        title = "Frankenstein",
        author = "Mary Shelley",
        genre = "Epic Classics",
        category = "Epic Classics",
        description = "The seminal sci-fi gothic tragedy of Victor Frankenstein and the sentient creature he brought to life.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/84/pg84.txt",
        estimatedMinutes = 260,
        coverGradient = listOf(Color(0xFF1E293B), Color(0xFF334155)),
        accentColor = Color(0xFF68D391),
        quote = "Beware; for I am fearless, and therefore powerful."
    ),
    ClassicBookEntry(
        id = "classic_dracula",
        title = "Dracula",
        author = "Bram Stoker",
        genre = "Epic Classics",
        category = "Epic Classics",
        description = "The definitive vampire classic detailing Count Dracula's attempt to move from Transylvania to England.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/345/pg345.txt",
        estimatedMinutes = 450,
        coverGradient = listOf(Color(0xFF2A0808), Color(0xFF4A1515)),
        accentColor = Color(0xFFFEB2B2),
        quote = "We learn from failure, not from success!"
    ),
    ClassicBookEntry(
        id = "classic_dorian_gray",
        title = "The Picture of Dorian Gray",
        author = "Oscar Wilde",
        genre = "Epic Classics",
        category = "Epic Classics",
        description = "A hedonistic young man sells his soul for eternal youth while his portrait absorbs the sins of his life.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/174/pg174.txt",
        estimatedMinutes = 240,
        coverGradient = listOf(Color(0xFF2A1B3D), Color(0xFF443152)),
        accentColor = Color(0xFFD6BCFA),
        quote = "The books that the world calls immoral are books that show the world its own shame."
    ),
    ClassicBookEntry(
        id = "classic_tale_two_cities",
        title = "A Tale of Two Cities",
        author = "Charles Dickens",
        genre = "Epic Classics",
        category = "Epic Classics",
        description = "Set in London and Paris during the French Revolution, depicting sacrifice, love, and redemption.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/98/pg98.txt",
        estimatedMinutes = 390,
        coverGradient = listOf(Color(0xFF521B28), Color(0xFF702437)),
        accentColor = Color(0xFFFED7E2),
        quote = "It was the best of times, it was the worst of times."
    ),
    ClassicBookEntry(
        id = "classic_walden",
        title = "Walden",
        author = "Henry David Thoreau",
        genre = "Epic Classics",
        category = "Epic Classics",
        description = "A reflection upon simple living in natural surroundings and personal declaration of independence.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/205/pg205.txt",
        estimatedMinutes = 280,
        coverGradient = listOf(Color(0xFF20382B), Color(0xFF335C45)),
        accentColor = Color(0xFFC6F6D5),
        quote = "I went to the woods because I wished to live deliberately."
    ),

    // --- 8. Faith & Spirit ---
    ClassicBookEntry(
        id = "classic_pilgrims_progress",
        title = "The Pilgrim's Progress",
        author = "John Bunyan",
        genre = "Faith & Spirit",
        category = "Faith & Spirit",
        description = "An epic allegorical journey of Christian from the City of Destruction to the Celestial City, navigating timeless perils of faith and endurance.",
        downloadUrl = "https://www.gutenberg.org/cache/epub/39452/pg39452.txt",
        estimatedMinutes = 320,
        coverGradient = listOf(Color(0xFF1A365D), Color(0xFF2B6CB0)),
        accentColor = Color(0xFF90CDF4),
        quote = "He that is down needs fear no fall; he that is low, no pride.",
        editions = listOf(
            BookEditionOption(
                id = "modern",
                name = "Modern English",
                description = "Jesse Lyman's edition",
                downloadUrl = "https://www.gutenberg.org/cache/epub/39452/pg39452.txt",
                tag = "Recommended"
            ),
            BookEditionOption(
                id = "original",
                name = "Original 1678 English",
                description = "The old unabridged version",
                downloadUrl = "https://www.gutenberg.org/cache/epub/131/pg131.txt",
                tag = "Classic 1678"
            )
        )
    ),
    ClassicBookEntry(
        id = "classic_good_morning_holy_spirit",
        title = "Good Morning, Holy Spirit",
        author = "Benny Hinn",
        genre = "Faith & Spirit",
        category = "Faith & Spirit",
        description = "A personal encounter with the Holy Spirit. This source requires library access; a direct download is unavailable. Open the source page for borrowing and purchase options, or import a copy you own.",
        downloadUrl = "https://archive.org/details/goodmorningholys00hinn",
        estimatedMinutes = 240,
        coverGradient = listOf(Color(0xFF3B1E54), Color(0xFF5B2E84)),
        accentColor = Color(0xFFD6BCFA),
        quote = "The Holy Spirit is not some shadowy, ethereal mist. He is a person, no less so than Jesus."
    ),
    ClassicBookEntry(
        id = "classic_power_of_imagination",
        title = "The Power of Imagination",
        author = "Andrew Wommack",
        genre = "Faith & Spirit",
        category = "Faith & Spirit",
        description = "Unlocking God's blueprint for your life by understanding the creative spiritual power of your inner imagination and faith.",
        downloadUrl = "https://www.awmi.net",
        estimatedMinutes = 210,
        coverGradient = listOf(Color(0xFF1B3B2B), Color(0xFF2E6B4B)),
        accentColor = Color(0xFF9AE6B4),
        quote = "Your imagination is like your spiritual womb; what you conceive there is what you will birth in your life."
    )
)

val CATALOG_CATEGORIES = listOf(
    "All",
    "🕊️ Faith & Spirit",
    "🧸 Family & Youth",
    "🔍 Mystery",
    "🗺️ Adventure",
    "❤️ Romance",
    "💡 Life & Habits",
    "⚡ Quick Reads",
    "🏛️ Epic Classics"
)

/**
 * Returns the deterministic Book of the Day shared across the entire app.
 */
fun getBookOfTheDay(): ClassicBookEntry {
    val dayIndex = (System.currentTimeMillis() / (1000 * 60 * 60 * 24)).toInt()
    return CURATED_CLASSICS[dayIndex.mod(CURATED_CLASSICS.size)]
}

/**
 * Renders an authentic published book cover if bundled in assets,
 * or gracefully falls back to a curated vintage cloth/leather-bound cover.
 */
@Composable
fun ClassicBookCover(
    book: ClassicBookEntry,
    modifier: Modifier = Modifier,
    width: Dp = 78.dp,
    height: Dp = 114.dp,
    large: Boolean = false
) {
    val context = LocalContext.current
    var coverBmp by remember(book.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(book.id) {
        coverBmp = withContext(Dispatchers.IO) {
            com.veritas.reader.BookCoverLoader.asset(context, book.id)
        }
    }

    val shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp, topStart = 2.dp, bottomStart = 2.dp)
    if (coverBmp != null) {
        Box(
            modifier = modifier
                .width(width)
                .height(height)
                .shadow(
                    elevation = if (large) 12.dp else 6.dp,
                    shape = shape
                )
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), shape)
        ) {
            Image(
                bitmap = coverBmp!!.asImageBitmap(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Box(
            modifier = modifier
                .width(width)
                .height(height)
                .shadow(
                    elevation = if (large) 12.dp else 6.dp,
                    shape = shape
                )
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            book.coverGradient.first().copy(alpha = 0.95f),
                            book.coverGradient.first(),
                            book.coverGradient.last()
                        )
                    )
                )
        ) {
            // Book Spine Crease Effect
            Box(
                modifier = Modifier
                    .width(if (large) 8.dp else 5.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Embossed gold/accent border inside cover
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (large) 14.dp else 9.dp,
                        end = if (large) 8.dp else 5.dp,
                        top = if (large) 8.dp else 5.dp,
                        bottom = if (large) 8.dp else 5.dp
                    )
                    .border(
                        BorderStroke(if (large) 1.5.dp else 1.dp, book.accentColor.copy(alpha = 0.5f)),
                        RoundedCornerShape(if (large) 5.dp else 3.dp)
                    )
                    .padding(if (large) 8.dp else 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = book.accentColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (large) 9.sp else 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "✦",
                            color = book.accentColor.copy(alpha = 0.8f),
                            fontSize = if (large) 10.sp else 6.sp
                        )
                        Spacer(modifier = Modifier.height(if (large) 4.dp else 2.dp))
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            fontSize = if (large) 13.sp else 9.sp,
                            lineHeight = if (large) 16.sp else 11.5.sp,
                            textAlign = TextAlign.Center,
                            maxLines = if (large) 4 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Vern Classic",
                        style = MaterialTheme.typography.labelSmall,
                        color = book.accentColor.copy(alpha = 0.85f),
                        fontSize = if (large) 7.sp else 5.5.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Universal Classics & Public Domain Catalog browser with:
 * - Inclusive curation for all ages, moods, and reading paces.
 * - Grid (Bookstore shelf) vs. List view toggle.
 * - Interactive Book Details Bottom Sheet with quotes, synopses, and 1-tap download/open.
 * - Direct 1-tap "Open" if already downloaded into the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicsCatalogDialog(
    existingDocuments: List<SavedDocument>,
    onDownloadBook: (ClassicBookEntry) -> Unit,
    onOpenBook: (SavedDocument) -> Unit = {},
    onOpenOceanOfPdf: (searchQuery: String) -> Unit = {},
    onOpenBookBrowser: (url: String, name: String, searchQuery: String) -> Unit = { _, _, q -> onOpenOceanOfPdf(q) },
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TextButton(onDismiss) { Text("Back to library") }
                ClassicsCatalogContent(existingDocuments, emptyMap(), onDownloadBook, {}, onOpenBook, onOpenBookBrowser)
            }
        }
    }
}
/**
 * Universal Classic Book Detail Bottom Sheet showing full synopsis, quote, and direct 1-tap download/open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicBookDetailSheet(
    book: ClassicBookEntry,
    existingDocuments: List<SavedDocument>,
    isDownloading: Boolean = false,
    onDismiss: () -> Unit,
    onDownloadBook: (ClassicBookEntry) -> Unit,
    onOpenBook: (SavedDocument) -> Unit,
    downloadState: com.veritas.reader.ClassicDownloadState = com.veritas.reader.ClassicDownloadState(),
    onCancelDownload: () -> Unit = {},
    editionDownloadStates: Map<String, com.veritas.reader.ClassicDownloadState> = emptyMap(),
    onCancelEdition: (ClassicBookEntry) -> Unit = {}
) {
    var editionId by rememberSaveable(book.id) { mutableStateOf(book.editions.firstOrNull()?.id) }
    val selectedBook = book.editions.firstOrNull { it.id == editionId }?.let { classicEdition(book, it) } ?: book
    val installedDoc = com.veritas.reader.findCatalogDocument(selectedBook, existingDocuments)
    val selectedState = if (book.editions.isEmpty()) downloadState else editionDownloadStates[selectedBook.id]
        ?.takeUnless { it.phase == com.veritas.reader.ClassicDownloadPhase.AVAILABLE } ?: com.veritas.reader.ClassicDownloadState()
    val selectedBusy = selectedState.busy || (book.editions.isEmpty() && isDownloading)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        shape = com.veritas.reader.VeritasPackStyle.sheetShape(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Large Book Cover
            ClassicBookCover(
                book = book,
                width = 110.dp,
                height = 160.dp,
                large = true
            )

            // Title & Author
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "by ${book.author}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Metadata Badges (Genre & Reading Time)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = book.genre,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "⏱️ ~${book.estimatedMinutes} min read",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quote Highlight Card
            if (book.quote.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "“${book.quote}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }

            if (book.editions.isNotEmpty()) {
                Text("Choose an edition", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
                book.editions.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { edition ->
                            Surface(modifier = Modifier.weight(1f).fillMaxHeight().testTag("edition_${edition.id}").clickable { editionId = edition.id },
                                shape = com.veritas.reader.VeritasPackStyle.cardShape(),
                                color = if (edition.id == editionId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        androidx.compose.material3.RadioButton(edition.id == editionId, { editionId = edition.id }, Modifier.size(24.dp))
                                        Text(edition.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    }
                                    Text(edition.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            // Full Synopsis
            Text(
                text = book.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (downloadState.message.isNotBlank()) Text(downloadState.message, color = MaterialTheme.colorScheme.error)
            if (selectedBusy) TextButton({ if (book.editions.isEmpty()) onCancelDownload() else onCancelEdition(selectedBook) }) { Text("Cancel download") }
            // Primary Action Button
            if (installedDoc != null) {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenBook(installedDoc)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open book", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        onDownloadBook(selectedBook)
                    },
                    enabled = !selectedBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (selectedBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedState.actionLabel)
                    } else {
                        Icon(if (book.hasDirectTextDownload) Icons.Filled.Download else Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (book.hasDirectTextDownload) selectedState.actionLabel else "Visit website", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
