package com.landnull.orbitalkeys

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

object OrbitalExporter {

    fun exportLayout(context: Context, nodes: List<KeyNode>) {
        val root = JSONObject()
        val keysArray = JSONArray()

        for (node in nodes) {
            val keyObj = JSONObject().apply {
                put("label", node.label)
                put("x", node.bounds.left)
                put("y", node.bounds.top)
                put("width", node.bounds.width())
                put("height", node.bounds.height())
                put("isSticky", node.isSticky)
            }
            keysArray.put(keyObj)
        }
        root.put("layout_nodes", keysArray)
        root.put("timestamp", System.currentTimeMillis())

        saveJsonToDownloads(context, "orbitalkeys_layout_${System.currentTimeMillis()}.json", root.toString(2))
    }

    fun exportSwipeCorpus(context: Context, points: List<PointTrace>, duration: Long, length: Float) {
        val root = JSONObject()
        val pointsArray = JSONArray()

        for (p in points) {
            val ptObj = JSONObject().apply {
                put("x", p.x)
                put("y", p.y)
                put("t", p.timeMs)
            }
            pointsArray.put(ptObj)
        }

        root.put("duration_ms", duration)
        root.put("path_length_px", length)
        root.put("vectors", pointsArray)

        saveJsonToDownloads(context, "orbitalkeys_corpus_${System.currentTimeMillis()}.json", root.toString(2))
    }

    private fun saveJsonToDownloads(context: Context, filename: String, jsonContent: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/OrbitalKeys")
        }

        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { os ->
                os.write(jsonContent.toByteArray())
            }
        }
    }
}
