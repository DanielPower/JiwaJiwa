package ca.danielpower.jstudy.audio

import android.content.Context
import android.net.ConnectivityManager
import ca.danielpower.jstudy.BuildConfig
import ca.danielpower.jstudy.core.Recording
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The stories' recordings, which don't come with the app: each is downloaded
 * from where the recorder uploads them ([BuildConfig.AUDIO_URL]) the first
 * time it's wanted, and kept on the phone. A clip's name is a hash of what's
 * said, so there's no list of them: a line not recorded yet is simply not
 * there, and the phone's voice reads it. Kana's clips come with the app.
 */
class Clips(
    context: Context,
    /** Whether downloading on mobile data is fine without asking (a setting). */
    private val onMobileData: () -> Boolean,
    private val base: String = BuildConfig.AUDIO_URL.trimEnd('/'),
) {
    private val dir = File(context.applicationContext.filesDir, "clips")
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // A few at a time, in the order asked for, so a chapter's first lines come first.
    private val slots = Semaphore(4)
    private val loading = HashMap<String, Deferred<File?>>()
    // Clips the server said aren't recorded, not asked for again until the app restarts.
    private val missing = HashSet<String>()

    /** Whether the phone is on a connection that may cost money, such as mobile data. */
    fun metered(): Boolean = connectivity?.isActiveNetworkMetered ?: false

    /** Whether clips can be downloaded now without asking: not on mobile data, or it's allowed. */
    fun mayDownload(): Boolean = !metered() || onMobileData()

    /** [clip] on the phone already, or null. A clip that comes with the app has no file. */
    fun local(clip: Recording): File? = File(dir, clip.file).takeIf { it.isFile }

    /** Start downloading whichever of [clips] aren't on the phone yet, in order. */
    fun fetch(clips: List<Recording?>) {
        for (clip in clips) if (clip != null && !clip.bundled) load(clip)
    }

    /**
     * [clip]'s file once it's downloaded, waiting at most [millis], or null
     * when it can't be had: not recorded, offline, or on mobile data without
     * leave to download (unless it's already on its way, as [fetch] was asked).
     */
    suspend fun await(clip: Recording, millis: Long): File? {
        local(clip)?.let { return it }
        val pending = synchronized(loading) { loading[clip.file] } ?: if (mayDownload()) load(clip) else return null
        return withTimeoutOrNull(millis) { pending.await() }
    }

    private fun load(clip: Recording): Deferred<File?> = synchronized(loading) {
        local(clip)?.let { return CompletableDeferred(it) }
        if (clip.file in missing) return CompletableDeferred(null)
        loading[clip.file]?.let { return it }
        // Once done it's on the phone, or failed and can be tried again, say once back online.
        scope.async { slots.withPermit { download(clip) } }
            .also { loading[clip.file] = it }
            .also { it.invokeOnCompletion { synchronized(loading) { loading.remove(clip.file) } } }
    }

    /** Saves [clip] under a temporary name first, so a download cut off never leaves half a clip. */
    private fun download(clip: Recording): File? {
        if (base.isEmpty()) return null
        val out = File(dir, clip.file)
        return runCatching {
            val connection = URL("$base/${clip.file}").openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            try {
                // Not recorded yet, or anything else: the phone's voice reads it.
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND || code == HttpURLConnection.HTTP_FORBIDDEN) {
                    synchronized(loading) { missing += clip.file }
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                out.parentFile?.mkdirs()
                val tmp = File(out.parentFile, "${out.name}.tmp")
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (tmp.renameTo(out)) out else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
