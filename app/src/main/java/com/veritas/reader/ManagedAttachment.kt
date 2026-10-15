package com.veritas.reader

import android.content.Context
import com.veritas.reader.ui.screens.NoteBlock
import com.veritas.reader.ui.screens.NoteSharing
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class AttachmentKind { IMAGE, AUDIO, VIDEO, FILE }

/** Portable identity and owner association. Paths are relative to app-owned storage. */
data class ManagedAttachment(val id: String, val kind: AttachmentKind, val mimeType: String,
    val byteSize: Long, val durationMillis: Long = 0, val ownerId: String, val relativePath: String,
    val displayName: String = "") {
    fun toJson(): JSONObject = JSONObject().put("version", 1).put("id", id).put("kind", kind.name)
        .put("mimeType", mimeType).put("byteSize", byteSize).put("durationMillis", durationMillis)
        .put("ownerId", ownerId).put("relativePath", relativePath).put("displayName", displayName)
    fun resolve(context: Context): File? {
        val parts = relativePath.split('/')
        if (parts.size != 2 || parts[0] !in listOf("notes_media", "voice_notes") ||
            parts[1].isBlank() || parts[1] in listOf(".", "..") || parts[1].any { it == '\\' || it == ':' || it.isISOControl() }) return null
        return runCatching {
            val root = File(context.filesDir, parts[0]).canonicalFile
            File(root, parts[1]).canonicalFile.takeIf { it.parentFile == root && it.isFile }
        }.getOrNull()
    }
    companion object {
        fun fromJson(obj: JSONObject): ManagedAttachment? = runCatching {
            ManagedAttachment(obj.getString("id"), AttachmentKind.valueOf(obj.getString("kind")), obj.optString("mimeType", "application/octet-stream"),
                obj.optLong("byteSize").coerceAtLeast(0), obj.optLong("durationMillis").coerceAtLeast(0),
                obj.optString("ownerId"), obj.optString("relativePath"), obj.optString("displayName"))
        }.getOrNull()?.takeIf { it.id.isNotBlank() }
        fun list(array: JSONArray?): List<ManagedAttachment> = array?.let { values ->
            (0 until values.length()).mapNotNull { values.optJSONObject(it)?.let(::fromJson) }
        }.orEmpty()
    }
}

internal fun managedAttachment(context: Context, path: String, kind: AttachmentKind, owner: String,
    previous: ManagedAttachment? = null, durationMillis: Long = 0, displayName: String = ""): ManagedAttachment {
    val file = runCatching { if (path.startsWith("file://")) File(android.net.Uri.parse(path).path.orEmpty()) else File(path) }.getOrNull()
    val canonical = runCatching { file?.canonicalFile }.getOrNull()
    val owned = listOf("notes_media", "voice_notes").firstOrNull { folder ->
        canonical?.parentFile == File(context.filesDir, folder).canonicalFile
    }
    val relative = if (owned != null) "$owned/${canonical!!.name}" else previous?.relativePath.orEmpty()
    val extension = file?.extension.orEmpty().lowercase()
    val mime = when (extension) {
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"
        "amr" -> "audio/amr"; "m4a", "aac" -> "audio/mp4"; "wav" -> "audio/wav"; "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"; "pdf" -> "application/pdf"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> previous?.mimeType ?: "application/octet-stream"
    }
    val identity = previous?.id ?: UUID.nameUUIDFromBytes(("vern-media:" + file?.name.orEmpty()).toByteArray()).toString()
    return ManagedAttachment(identity, kind, mime, canonical?.takeIf { owned != null && it.isFile }?.length() ?: previous?.byteSize ?: 0,
        durationMillis.takeIf { it > 0 } ?: previous?.durationMillis ?: 0, owner, relative,
        displayName.ifBlank { previous?.displayName ?: file?.name.orEmpty() })
}

internal fun GeneralNote.withManagedAttachments(context: Context): GeneralNote {
    val items = NoteSharing.blocks(this).mapNotNull { block ->
        val path = NoteSharing.path(block) ?: return@mapNotNull null
        val kind = when (block) { is NoteBlock.Audio -> AttachmentKind.AUDIO; is NoteBlock.Image -> AttachmentKind.IMAGE
            is NoteBlock.Video -> AttachmentKind.VIDEO; else -> AttachmentKind.FILE }
        val previous = attachments.firstOrNull { it.relativePath.substringAfterLast('/') == File(path).name ||
            (it.relativePath.isBlank() && it.displayName == File(path).name) }
        managedAttachment(context, path, kind, "note:$id", previous, displayName = (block as? NoteBlock.File)?.fileName.orEmpty())
    }.distinctBy { it.id }
    return copy(attachments = items)
}
internal fun ReaderAnnotation.withManagedAudio(context: Context): ReaderAnnotation = copy(audioAttachment =
    audioPath?.takeIf { it.isNotBlank() }?.let { managedAttachment(context, it, AttachmentKind.AUDIO,
        "annotation:$stableKey", audioAttachment, audioDurationSeconds * 1000L) })
