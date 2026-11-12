package com.veritas.reader

import android.os.Bundle
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshots.SnapshotStateSet
import org.json.JSONArray
import org.json.JSONObject

internal val PastedQuizSaver = Saver<QuizSet?, String>(
    save = { it?.toJson()?.toString().orEmpty() },
    restore = { if (it.isBlank()) null else runCatching { QuizSet.fromJson(JSONObject(it)) }.getOrNull() }
)

internal val QuizQuestionsSaver = Saver<List<QuizQuestion>, String>(
    save = { questions -> JSONArray().apply { questions.forEach { put(it.toJson()) } }.toString() },
    restore = { raw -> runCatching { JSONArray(raw).let { array -> List(array.length()) { QuizQuestion.fromJson(array.getJSONObject(it)) } } }.getOrNull() }
)

internal val QuizAnswersSaver = listSaver<SnapshotStateMap<Int, String>, Bundle>(
    save = { answers -> answers.map { (index, answer) -> Bundle().apply { putInt("index", index); putString("answer", answer) } } },
    restore = { saved -> mutableStateMapOf<Int, String>().apply { saved.forEach { put(it.getInt("index"), it.getString("answer").orEmpty()) } } }
)

internal val QuizIndicesSaver = listSaver<SnapshotStateSet<Int>, Int>(
    save = { it.toList() },
    restore = { mutableStateSetOf<Int>().apply { addAll(it) } }
)
