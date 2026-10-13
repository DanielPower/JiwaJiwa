package ca.danielpower.jstudy.data

import ca.danielpower.jstudy.core.RenshuuTerm
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONException
import org.json.JSONObject

class RenshuuException(message: String) : Exception(message)

/** Reads your studied vocabulary from the Renshuu API (https://api.renshuu.org/docs/). */
class RenshuuClient(private val apiKey: String) {

    /**
     * Every vocabulary word you've studied, with its mastery. [onPage] is
     * called after each page with the page number and the page count.
     */
    fun studiedVocab(onPage: (page: Int, pages: Int) -> Unit = { _, _ -> }): List<RenshuuTerm> {
        val terms = mutableListOf<RenshuuTerm>()
        var page = 1
        var pages = 1
        while (page <= pages && page <= MAX_PAGES) {
            val root = get("/list/all/vocab?pg=$page")
            val contents = root.optJSONObject("contents") ?: root
            pages = contents.optInt("total_pg", 1)
            val items = contents.optJSONArray("terms") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                val term = items.optJSONObject(i) ?: continue
                terms += RenshuuTerm(
                    jmdictId = term.optString("edict_ent").toLongOrNull(),
                    mastery = term.optJSONObject("user_data")?.optInt("mastery_avg_perc", 0) ?: 0,
                    text = term.optString("kanji_full").ifBlank { term.optString("hiragana_full") },
                )
            }
            onPage(page, pages)
            page++
        }
        return terms
    }

    private fun get(path: String): JSONObject {
        val connection = try {
            (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
            }
        } catch (e: IOException) {
            throw RenshuuException("Couldn't reach Renshuu. Check your connection and try again.")
        }
        try {
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                throw RenshuuException("Couldn't reach Renshuu. Check your connection and try again.")
            }
            when {
                code == 401 || code == 403 -> throw RenshuuException("Renshuu didn't accept that API key.")
                code == 429 -> throw RenshuuException("Renshuu is limiting requests right now. Try again later.")
                code !in 200..299 -> throw RenshuuException("Renshuu returned an error ($code). Try again later.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw RenshuuException("Renshuu sent a reply jstudy couldn't read.")
            }
        } catch (e: IOException) {
            throw RenshuuException("The connection to Renshuu was interrupted. Try again.")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://api.renshuu.org/v1"
        private const val TIMEOUT_MS = 20_000

        /** A safety stop; at 50 words a page this is far more than any account has. */
        private const val MAX_PAGES = 1_000
    }
}
