package com.veritas.reader

import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.view.View
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
                        level = entry.level
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
            navigateToPdfPage(hit.targetPageIndex)
            Toast.makeText(this, "Jumped to page ${hit.targetPageIndex + 1}", Toast.LENGTH_SHORT).show()
            return true
        }

        return false
    }

    internal fun VeritasPdfViewerActivity.showTableOfContentsDialog() {
        if (outlineHost != null) return
        showChrome(keepVisible = true)
        val pageCount = runCatching { pdfView?.pdfDocument?.pageCount }.getOrNull() ?: 1
        val currentPage = (pdfView?.firstVisiblePage ?: 0) + 1
        val tocItems = (if (pdfTocItems.isNotEmpty()) pdfTocItems else buildFallbackToc(pageCount))
            .filterNot { com.veritas.reader.ui.screens.isSelfReferentialTocHeading(it.title) }
        val documentLinks = allDocumentLinks
        val entries = tocItems.map { item ->
            com.veritas.reader.ui.screens.SmartOutlineEntry(
                index = item.pageIndex, title = item.title, preview = "", isHeading = true,
                level = item.level, pageNumber = item.pageNumber, source = "PDF bookmarks"
            )
        }
        val location = IntArray(2)
        val anchor = outlineButton
        anchor?.getLocationOnScreen(location)
        val origin = if (anchor != null) androidx.compose.ui.geometry.Offset(
            location[0] + anchor.width / 2f, location[1] + anchor.height / 2f
        ) else androidx.compose.ui.geometry.Offset.Unspecified
        val host = androidx.compose.ui.platform.ComposeView(this)
        val parent = findViewById<android.view.ViewGroup>(android.R.id.content)
        fun dismissOutline() {
            parent.removeView(host)
            host.disposeComposition()
            outlineHost = null
            showChrome()
        }
        host.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        outlineHost = host
        parent.addView(host, android.view.ViewGroup.LayoutParams(0, 0))
        host.setContent {
            androidx.compose.material3.MaterialTheme(
                colorScheme = veritasReaderColorScheme(pdfChromeSettings, activity),
                typography = com.veritas.reader.ui.veritasTypography(com.veritas.reader.ui.VeritasUiFont.fromId(pdfChromeSettings.uiFontId)),
                shapes = veritasPackShapes(pdfChromeSettings.themePackId)
            ) {
                com.veritas.reader.ui.screens.ReaderOutlineScreen(
                    title = "Table of contents",
                    documentTitle = com.veritas.reader.ui.screens.resolveHumanDocumentTitle(document?.title.orEmpty(), "PDF"),
                    entries = entries,
                    currentIndex = currentPage - 1,
                    currentPage = currentPage,
                    pageCount = pageCount,
                    origin = origin,
                    reduceMotion = pdfChromeSettings.reduceMotion,
                    onJumpToDestination = { page, _ ->
                        if (page != null) navigateToPdfPage(page - 1)
                        dismissOutline()
                    },
                    onDismiss = { dismissOutline() },
                    showLinks = true,
                    links = documentLinks.map { link ->
                        com.veritas.reader.ui.screens.OutlineLink(
                            title = link.label,
                            detail = if (!link.url.isNullOrBlank()) "Web link · Page ${link.pageNumber}"
                                else "Jump to page ${(link.targetPageIndex ?: 0) + 1} · Page ${link.pageNumber}",
                            searchText = link.url.orEmpty()
                        )
                    },
                    onOpenLink = { index ->
                        val link = documentLinks[index]
                        dismissOutline()
                        if (!link.url.isNullOrBlank()) {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
                                .onFailure { Toast.makeText(activity, "Could not open link: ${link.url}", Toast.LENGTH_SHORT).show() }
                        } else link.targetPageIndex?.let { navigateToPdfPage(it) }
                    }
                )
            }
        }
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
