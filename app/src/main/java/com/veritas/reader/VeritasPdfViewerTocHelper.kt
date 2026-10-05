package com.veritas.reader

import android.content.Intent
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun VeritasPdfViewerActivity.loadPdfMetadataAndLinks(uri: Uri) {
        if (isExtractingToc) return
        isExtractingToc = true
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                PDFBoxResourceLoader.init(applicationContext)
                val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(10L * 1024 * 1024).apply {
                    setTempDir(java.io.File(cacheDir, "pdfbox_temp").apply { mkdirs() })
                }
                contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream, memorySetting).use { pdDoc ->
                        val toc = extractTocFromPdf(pdDoc)
                        withContext(Dispatchers.Main) {
                            pdfTocItems = toc
                        }
                        val links = extractLinksFromPdf(pdDoc)
                        val allLinks = links.values.flatten()
                        withContext(Dispatchers.Main) {
                            pdfLinksByPage = links
                            allDocumentLinks = allLinks
                        }
                    }
                }
            }
            isExtractingToc = false
        }
    }

    internal fun VeritasPdfViewerActivity.extractTocFromPdf(pdDocument: PDDocument): List<PdfTocItem> {
        val outline = pdDocument.documentCatalog.documentOutline ?: return emptyList()
        val pageToIndexMap = HashMap<PDPage, Int>(pdDocument.numberOfPages)
        pdDocument.pages.forEachIndexed { idx, page -> pageToIndexMap[page] = idx }
        val result = mutableListOf<PdfTocItem>()

        fun walkOutline(node: PDOutlineNode, level: Int) {
            var current: PDOutlineItem? = node.firstChild
            while (current != null) {
                val rawTitle = current.title.orEmpty().trim()
                // Native bookmarks are authored structure, not inferred contents rows.
                val title = rawTitle.replace(Regex("\\s+"), " ").trim()
                val targetPage = resolveDestinationPage(current, pdDocument, pageToIndexMap)
                if (title.isNotBlank() && targetPage != null && targetPage in 0 until pdDocument.numberOfPages) {
                    result.add(
                        PdfTocItem(
                            title = title,
                            pageNumber = targetPage + 1,
                            pageIndex = targetPage,
                            level = level
                        )
                    )
                }
                if (current.hasChildren()) {
                    walkOutline(current, level + 1)
                }
                current = current.nextSibling
            }
        }

        walkOutline(outline, 0)
        return result.distinctBy { Triple(it.title, it.pageNumber, it.level) }
    }

    private fun resolveDestinationPage(
        item: PDOutlineItem,
        pdDoc: PDDocument,
        pageToIndexMap: Map<PDPage, Int>? = null
    ): Int? {
        return runCatching {
            var dest = item.destination
            if (dest == null && item.action is PDActionGoTo) {
                dest = (item.action as PDActionGoTo).destination
            }
            if (dest is PDPageDestination) {
                val p = dest.page
                if (p != null) {
                    val idx = pageToIndexMap?.get(p) ?: pdDoc.pages.indexOf(p)
                    if (idx >= 0) return idx
                }
                val pageNumber = dest.pageNumber
                if (pageNumber >= 0) return pageNumber
            } else if (dest is PDNamedDestination) {
                val pageDest = pdDoc.documentCatalog.findNamedDestinationPage(dest)
                if (pageDest is PDPageDestination) {
                    val p = pageDest.page
                    if (p != null) {
                        val idx = pageToIndexMap?.get(p) ?: pdDoc.pages.indexOf(p)
                        if (idx >= 0) return idx
                    }
                    if (pageDest.pageNumber in 0 until pdDoc.numberOfPages) return pageDest.pageNumber
                }
            }
            null
        }.getOrNull()
    }

    private fun extractLinksFromPdf(pdDocument: PDDocument): Map<Int, List<PdfLinkItem>> {
        val linksMap = mutableMapOf<Int, MutableList<PdfLinkItem>>()
        try {
            pdDocument.pages.forEachIndexed { pageIdx, page ->
                val pageLinks = mutableListOf<PdfLinkItem>()
                val mediaBox = page.mediaBox ?: page.cropBox
                val pageHeight = mediaBox?.height ?: 0f
                val pageWidth = mediaBox?.width ?: 0f

                for (ann in page.annotations) {
                    if (ann is PDAnnotationLink) {
                        val rect = ann.rectangle ?: continue
                        val action = ann.action
                        var dest = ann.destination
                        if (dest == null && action is PDActionGoTo) {
                            dest = action.destination
                        }
                        var uriString: String? = null
                        var targetPage: Int? = null
                        if (action is PDActionURI) {
                            uriString = action.uri
                        } else if (dest is PDPageDestination) {
                            val p = dest.page
                            if (p != null) {
                                val idx = pdDocument.pages.indexOf(p)
                                if (idx >= 0) targetPage = idx
                            } else if (dest.pageNumber >= 0) {
                                targetPage = dest.pageNumber
                            }
                        } else if (dest is PDNamedDestination) {
                            val named = pdDocument.documentCatalog.findNamedDestinationPage(dest)
                            if (named is PDPageDestination) {
                                val p = named.page
                                if (p != null) {
                                    val idx = pdDocument.pages.indexOf(p)
                                    if (idx >= 0) targetPage = idx
                                }
                            }
                        }

                        if (!uriString.isNullOrBlank() || targetPage != null) {
                            val left = rect.lowerLeftX / (if (pageWidth > 0f) pageWidth else 1f)
                            val top = (pageHeight - rect.upperRightY) / (if (pageHeight > 0f) pageHeight else 1f)
                            val right = rect.upperRightX / (if (pageWidth > 0f) pageWidth else 1f)
                            val bottom = (pageHeight - rect.lowerLeftY) / (if (pageHeight > 0f) pageHeight else 1f)
                            val label = when {
                                !uriString.isNullOrBlank() -> uriString
                                targetPage != null -> "Jump to Page ${targetPage + 1}"
                                else -> "Link"
                            }
                            pageLinks.add(
                                PdfLinkItem(
                                    pageNumber = pageIdx + 1,
                                    bounds = RectF(left, top, right, bottom),
                                    label = label,
                                    url = uriString,
                                    targetPageIndex = targetPage
                                )
                            )
                        }
                    }
                }
                if (pageLinks.isNotEmpty()) {
                    linksMap[pageIdx] = pageLinks
                }
            }
        } catch (_: Throwable) {}
        return linksMap
    }

    internal fun VeritasPdfViewerActivity.buildFallbackToc(pageCount: Int): List<PdfTocItem> {
        val model = readerTextModel
        if (model != null && model.sentences.isNotEmpty()) {
            val smartEntries = com.veritas.reader.ui.screens.buildSmartOutline(
                model.sentences.map { it.text },
                model
            )
            val validPageEntries = smartEntries.mapNotNull { entry ->
                val p = entry.pageNumber ?: return@mapNotNull null
                if (p in 1..pageCount) {
                    PdfTocItem(
                        title = entry.title,
                        pageNumber = p,
                        pageIndex = p - 1,
                        level = entry.level.coerceIn(0, 3)
                    )
                } else null
            }
            if (validPageEntries.isNotEmpty()) {
                return validPageEntries
            }
        }
        if (pageCount > 1) {
            val step = maxOf(1, pageCount / 5)
            val list = mutableListOf<PdfTocItem>()
            for (p in 1..pageCount step step) {
                val name = when (p) {
                    1 -> "Document Start (Page 1)"
                    else -> "Page $p"
                }
                list.add(PdfTocItem(name, p, p - 1, 0))
            }
            if (list.none { it.pageNumber == pageCount }) {
                list.add(PdfTocItem("Document End (Page $pageCount)", pageCount, pageCount - 1, 0))
            }
            return list
        }
        return listOf(PdfTocItem("Page 1", 1, 0, 0))
    }

    internal fun VeritasPdfViewerActivity.handleLinkTap(view: View, tapX: Float, tapY: Float): Boolean {
        val visiblePage = runCatching { pdfView?.firstVisiblePage }.getOrNull() ?: return false
        val pageLinks = pdfLinksByPage[visiblePage] ?: return false
        if (pageLinks.isEmpty()) return false

        val w = view.width.toFloat()
        val h = view.height.toFloat()
        if (w <= 0f || h <= 0f) return false
        val normX = tapX / w
        val normY = tapY / h

        val hit = pageLinks.firstOrNull { link ->
            val paddingX = 0.04f
            val paddingY = 0.04f
            normX >= (link.bounds.left - paddingX) &&
            normX <= (link.bounds.right + paddingX) &&
            normY >= (link.bounds.top - paddingY) &&
            normY <= (link.bounds.bottom + paddingY)
        } ?: return false

        if (!hit.url.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(hit.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                activity.startActivity(intent)
                Toast.makeText(this, "Opening link: ${hit.url}", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Could not open link: ${hit.url}", Toast.LENGTH_SHORT).show()
            }
            return true
        }

        if (hit.targetPageIndex != null) {
            pdfView?.scrollToPage(hit.targetPageIndex)
            Toast.makeText(this, "Jumped to page ${hit.targetPageIndex + 1}", Toast.LENGTH_SHORT).show()
            return true
        }

        return false
    }

    internal fun VeritasPdfViewerActivity.showTableOfContentsDialog() {
        showChrome(keepVisible = true)
        val pageCount = runCatching { pdfView?.pdfDocument?.pageCount }.getOrNull() ?: 1
        val currentPage = (pdfView?.firstVisiblePage ?: 0) + 1

        val allTocItems = (if (pdfTocItems.isNotEmpty()) pdfTocItems else buildFallbackToc(pageCount))
            .filterNot { com.veritas.reader.ui.screens.isSelfReferentialTocHeading(it.title) }
        val displayedToc = allTocItems
        val displayedLinks = allDocumentLinks

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 16.dp, 20.dp, 16.dp)
        }

        // Header Title Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 10.dp)
        }
        val title = TextView(this).apply {
            text = "Table of Contents"
            setTextColor(colorTextPrimary)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pageBadge = TextView(this).apply {
            text = "Current: Page $currentPage"
            setTextColor(colorPrimary)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(colorSyncBackground, 6.dp)
            setPadding(8.dp, 3.dp, 8.dp, 3.dp)
        }
        headerRow.addView(title)
        headerRow.addView(pageBadge)
        rootLayout.addView(headerRow)

        // Search bar
        val searchBox = EditText(this).apply {
            hint = "Search chapters & links..."
            setHintTextColor(colorTextSecondary)
            setTextColor(colorTextPrimary)
            textSize = 13.5f
            background = rounded(colorSyncBackground, 10.dp)
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
            maxLines = 1
        }
        rootLayout.addView(searchBox)

        // Tab Row: Chapters vs Links
        var selectedTab = 0 // 0 = Chapters, 1 = Links
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10.dp, 0, 8.dp)
        }
        val chaptersTab = TextView(this).apply {
            text = "📑 Chapters (${allTocItems.size})"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimary)
            background = rounded(colorSyncBackground, 8.dp)
            setPadding(12.dp, 6.dp, 12.dp, 6.dp)
        }
        val linksTab = TextView(this).apply {
            text = "🔗 Links (${allDocumentLinks.size})"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextSecondary)
            background = rounded(colorSurfaceVariant, 8.dp)
            setPadding(12.dp, 6.dp, 12.dp, 6.dp)
        }
        tabRow.addView(chaptersTab)
        tabRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(8.dp, 1) })
        tabRow.addView(linksTab)
        rootLayout.addView(tabRow)

        // Content list container
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            val maxH = (resources.displayMetrics.heightPixels * 0.45f).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxH)
            isVerticalScrollBarEnabled = true
            addView(listContainer)
        }
        rootLayout.addView(scroll)

        var dialogRef: androidx.appcompat.app.AlertDialog? = null

        fun renderContent() {
            listContainer.removeAllViews()
            val query = searchBox.text.toString().trim().lowercase()

            if (selectedTab == 0) {
                val filtered = if (query.isBlank()) displayedToc else displayedToc.filter {
                    it.title.lowercase().contains(query) || "page ${it.pageNumber}".contains(query)
                }
                if (filtered.isEmpty()) {
                    listContainer.addView(TextView(activity).apply {
                        text = "No matching chapters found"
                        setTextColor(colorTextSecondary)
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setPadding(0, 24.dp, 0, 24.dp)
                    })
                } else {
                    filtered.forEach { item ->
                        val isCurrent = item.pageNumber == currentPage
                        val itemRow = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding((12 + item.level * 16).dp, 10.dp, 12.dp, 10.dp)
                            isClickable = true
                            isFocusable = true
                            background = if (isCurrent) {
                                rounded(colorSyncBackground, 10.dp)
                            } else {
                                StateListDrawable().apply {
                                    addState(intArrayOf(android.R.attr.state_pressed), rounded(colorSyncBackground, 10.dp))
                                }
                            }
                            setOnClickListener {
                                pdfView?.scrollToPage(item.pageIndex)
                                dialogRef?.dismiss()
                                Toast.makeText(activity, "Jumped to ${item.title} (Page ${item.pageNumber})", Toast.LENGTH_SHORT).show()
                            }
                        }

                        val titleView = TextView(activity).apply {
                            text = if (item.level > 0) "• ${item.title}" else item.title
                            setTextColor(if (isCurrent) colorPrimary else colorTextPrimary)
                            textSize = if (item.level == 0) 14f else 13f
                            typeface = if (item.level == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                            maxLines = 2
                            ellipsize = TextUtils.TruncateAt.END
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginEnd = 8.dp
                            }
                        }
                        val pageChip = TextView(activity).apply {
                            text = "p. ${item.pageNumber}"
                            setTextColor(if (isCurrent) colorPrimary else colorTextSecondary)
                            textSize = 11f
                            typeface = Typeface.DEFAULT_BOLD
                            setPadding(6.dp, 2.dp, 6.dp, 2.dp)
                            background = rounded(if (isCurrent) colorBackground else colorSurfaceVariant, 6.dp)
                        }
                        itemRow.addView(titleView)
                        itemRow.addView(pageChip)
                        listContainer.addView(itemRow)
                    }
                }
            } else {
                // Links Tab
                val filtered = if (query.isBlank()) displayedLinks else displayedLinks.filter {
                    it.label.lowercase().contains(query) || (it.url?.lowercase()?.contains(query) == true)
                }
                if (filtered.isEmpty()) {
                    listContainer.addView(TextView(activity).apply {
                        text = if (allDocumentLinks.isEmpty()) "No hyperlinks detected in this document" else "No matching links found"
                        setTextColor(colorTextSecondary)
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setPadding(0, 24.dp, 0, 24.dp)
                    })
                } else {
                    filtered.forEach { link ->
                        val itemRow = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(12.dp, 10.dp, 12.dp, 10.dp)
                            isClickable = true
                            isFocusable = true
                            background = StateListDrawable().apply {
                                addState(intArrayOf(android.R.attr.state_pressed), rounded(colorSyncBackground, 10.dp))
                            }
                            setOnClickListener {
                                dialogRef?.dismiss()
                                if (!link.url.isNullOrBlank()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching {
                                        activity.startActivity(intent)
                                        Toast.makeText(activity, "Opening link: ${link.url}", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(activity, "Could not open link: ${link.url}", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (link.targetPageIndex != null) {
                                    pdfView?.scrollToPage(link.targetPageIndex)
                                    Toast.makeText(activity, "Jumped to page ${link.targetPageIndex + 1}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        val icon = ImageView(activity).apply {
                            setImageResource(if (!link.url.isNullOrBlank()) R.drawable.ic_m3_open_in_new else R.drawable.ic_m3_jump_page)
                            setColorFilter(colorPrimary)
                            layoutParams = LinearLayout.LayoutParams(20.dp, 20.dp).apply {
                                marginEnd = 10.dp
                            }
                        }
                        val linkCol = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        val linkTitle = TextView(activity).apply {
                            text = link.label
                            setTextColor(colorTextPrimary)
                            textSize = 13f
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        }
                        val linkSub = TextView(activity).apply {
                            text = if (!link.url.isNullOrBlank()) "Web Link • Found on Page ${link.pageNumber}" else "Jump to Page ${(link.targetPageIndex ?: 0) + 1} • On Page ${link.pageNumber}"
                            setTextColor(colorTextSecondary)
                            textSize = 10.5f
                        }
                        linkCol.addView(linkTitle)
                        linkCol.addView(linkSub)

                        itemRow.addView(icon)
                        itemRow.addView(linkCol)
                        listContainer.addView(itemRow)
                    }
                }
            }
        }

        chaptersTab.setOnClickListener {
            selectedTab = 0
            chaptersTab.setTextColor(colorPrimary)
            chaptersTab.background = rounded(colorSyncBackground, 8.dp)
            linksTab.setTextColor(colorTextSecondary)
            linksTab.background = rounded(colorSurfaceVariant, 8.dp)
            renderContent()
        }

        linksTab.setOnClickListener {
            selectedTab = 1
            linksTab.setTextColor(colorPrimary)
            linksTab.background = rounded(colorSyncBackground, 8.dp)
            chaptersTab.setTextColor(colorTextSecondary)
            chaptersTab.background = rounded(colorSurfaceVariant, 8.dp)
            renderContent()
        }

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderContent()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        renderContent()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(rootLayout)
            .setPositiveButton("Jump to Page...") { _, _ ->
                showJumpToPageDialog()
            }
            .setNegativeButton("Close", null)
            .create()
        dialogRef = dialog
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorSurface))
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(colorPrimary)
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(colorTextSecondary)
    }




data class PdfTocItem(
    val title: String,
    val pageNumber: Int,
    val pageIndex: Int,
    val level: Int = 0
)

data class PdfLinkItem(
    val pageNumber: Int,
    val bounds: RectF,
    val label: String,
    val url: String? = null,
    val targetPageIndex: Int? = null
)

