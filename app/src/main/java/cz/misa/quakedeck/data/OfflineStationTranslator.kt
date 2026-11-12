package cz.misa.quakedeck.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

enum class OfflineStationTranslationStatus {
    CHECKING,
    NOT_DOWNLOADED,
    DOWNLOADING,
    DELETING,
    READY,
    FAILED
}

/**
 * Small, persistent Japanese-to-English place-name cache backed by ML Kit's
 * on-device model. Nothing is sent to a translation server.
 */
class OfflineStationTranslator(context: Context) : AutoCloseable {
    private companion object {
        const val MODEL_READY_KEY = "japanese_to_english_model_ready"
    }

    private val appContext = context.applicationContext
    private val cache = appContext.getSharedPreferences(
        "quakedeck_offline_station_translations",
        Context.MODE_PRIVATE
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )
    private val pendingCallbacks = mutableMapOf<String, MutableList<(String?) -> Unit>>()
    @Volatile private var closed = false

    override fun close() {
        synchronized(pendingCallbacks) {
            if (closed) return
            closed = true
            pendingCallbacks.clear()
        }
        mainHandler.removeCallbacksAndMessages(null)
        translator.close()
    }

    @Volatile
    var status: OfflineStationTranslationStatus = OfflineStationTranslationStatus.CHECKING
        private set

    fun refreshStatus(onStatusChanged: (OfflineStationTranslationStatus) -> Unit) {
        // ML Kit's downloaded-model list is device-wide. A Japanese model from
        // another translator is not proof that QuakeDeck's JA→EN download was
        // requested, so keep an app-owned success marker as the primary gate.
        if (!cache.getBoolean(MODEL_READY_KEY, false)) {
            publish(OfflineStationTranslationStatus.NOT_DOWNLOADED, onStatusChanged)
            return
        }
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                publish(
                    if (models.any { it.language == TranslateLanguage.JAPANESE }) {
                        OfflineStationTranslationStatus.READY
                    } else {
                        OfflineStationTranslationStatus.NOT_DOWNLOADED
                    },
                    onStatusChanged
                )
                if (models.none { it.language == TranslateLanguage.JAPANESE }) {
                    cache.edit { remove(MODEL_READY_KEY) }
                }
            }
            .addOnFailureListener {
                publish(OfflineStationTranslationStatus.NOT_DOWNLOADED, onStatusChanged)
            }
    }

    fun download(onStatusChanged: (OfflineStationTranslationStatus) -> Unit) {
        publish(OfflineStationTranslationStatus.DOWNLOADING, onStatusChanged)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                cache.edit { putBoolean(MODEL_READY_KEY, true) }
                publish(OfflineStationTranslationStatus.READY, onStatusChanged)
            }
            .addOnFailureListener {
                publish(OfflineStationTranslationStatus.FAILED, onStatusChanged)
            }
    }

    fun delete(onStatusChanged: (OfflineStationTranslationStatus) -> Unit) {
        publish(OfflineStationTranslationStatus.DELETING, onStatusChanged)
        val japaneseModel = TranslateRemoteModel.Builder(TranslateLanguage.JAPANESE).build()
        RemoteModelManager.getInstance()
            .deleteDownloadedModel(japaneseModel)
            .addOnSuccessListener {
                // A test reset must also remove cached results so every raw
                // place returns to its download-guidance path.
                cache.edit { clear() }
                publish(OfflineStationTranslationStatus.NOT_DOWNLOADED, onStatusChanged)
            }
            .addOnFailureListener {
                publish(OfflineStationTranslationStatus.FAILED, onStatusChanged)
            }
    }

    fun translate(japanese: String, onResult: (String?) -> Unit) {
        if (closed || japanese.isBlank() || status != OfflineStationTranslationStatus.READY) {
            onResult(null)
            return
        }
        cache.getString(japanese, null)?.let(onResult)
            ?: synchronized(pendingCallbacks) {
                if (closed) return
                pendingCallbacks[japanese]?.add(onResult) ?: run {
                    pendingCallbacks[japanese] = mutableListOf(onResult)
                    translator.translate(japanese)
                        .addOnSuccessListener { translated ->
                            if (!closed) cache.edit { putString(japanese, translated) }
                            complete(japanese, translated)
                        }
                        .addOnFailureListener { complete(japanese, null) }
                }
            }
    }

    private fun complete(japanese: String, translated: String?) {
        val callbacks = synchronized(pendingCallbacks) {
            pendingCallbacks.remove(japanese).orEmpty()
        }
        mainHandler.post { if (!closed) callbacks.forEach { it(translated) } }
    }

    private fun publish(
        newStatus: OfflineStationTranslationStatus,
        onStatusChanged: (OfflineStationTranslationStatus) -> Unit
    ) {
        if (closed) return
        status = newStatus
        mainHandler.post { if (!closed) onStatusChanged(newStatus) }
    }
}
