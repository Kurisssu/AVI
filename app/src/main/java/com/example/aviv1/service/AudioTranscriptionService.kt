package com.example.aviv1.service

import ai.picovoice.leopard.Leopard
import ai.picovoice.leopard.LeopardException
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Serviciu pentru transcrierea audio folosind Picovoice Leopard.
 */
class AudioTranscriptionService(private val context: Context) {

    private var leopard: Leopard? = null

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    companion object {
        private const val TAG = "AudioTranscriptionSvc"
        private const val MODEL_PATH = "leopard_params.pv"
        private const val SAMPLE_RATE = 16000 // 16 kHz
    }

    /**
     * Verifică dacă modelul Leopard este disponibil în directorul assets.
     * @return true dacă modelul există, false în caz contrar
     */
    private fun isModelAvailable(): Boolean {
        return try {
            val assetFiles = context.assets.list("")
            assetFiles?.contains(MODEL_PATH) == true
        } catch (e: IOException) {
            Log.e(TAG, "Eroare la verificarea fișierelor din assets: ${e.message}", e)
            false
        }
    }

    /**
     * Inițializează serviciul de transcriere audio.
     */
    suspend fun initialize(accessKey: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Inițializare serviciu de transcriere audio")
            
            // Verificăm dacă modelul există
            if (!isModelAvailable()) {
                val errorMessage = "Modelul Leopard ($MODEL_PATH) nu a fost găsit în directorul assets. " +
                                  "Transcrierea nu va fi disponibilă."
                Log.e(TAG, errorMessage)
                _error.value = errorMessage
                throw IOException(errorMessage)
            }
            
            // Inițializare Leopard pentru transcriere
            try {
                leopard = Leopard.Builder()
                    .setAccessKey(accessKey)
                    .setModelPath(MODEL_PATH)
                    .setEnableAutomaticPunctuation(true)
                    .build(context)
                
                Log.d(TAG, "Serviciu de transcriere audio inițializat cu succes")
            } catch (e: Exception) {
                val errorMessage = "Eroare la inițializarea Leopard: ${e.message}. " +
                                  "Asigurați-vă că fișierul \"$MODEL_PATH\" există în directorul assets."
                Log.e(TAG, errorMessage, e)
                _error.value = errorMessage
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eroare neașteptată: ${e.message}", e)
            _error.value = "Eroare neașteptată: ${e.message}"
            throw e
        }
    }

    /**
     * Transcrie un segment de date audio PCM.
     * 
     * @param pcmData Datele audio PCM (16-bit, mono, 16kHz)
     * @return Transcrierea audio sau null în caz de eroare
     */
    suspend fun transcribePcmData(pcmData: ShortArray): TranscriptionResult = withContext(Dispatchers.IO) {
        try {
            _isProcessing.value = true
            _error.value = null
            
            Log.d(TAG, "Transcrierea a ${pcmData.size} eșantioane audio")
            
            leopard?.let {
                // API-ul nou Leopard returnează un obiect LeopardTranscript în loc de un String
                val transcriptionResult = it.process(pcmData)
                
                // Extragem textul transcris din obiectul LeopardTranscript
                val transcriptionText = transcriptionResult.getTranscriptString()
                
                val result = TranscriptionResult(
                    text = transcriptionText,
                    words = emptyList(), // API-ul nou nu mai oferă informații despre cuvinte individuale în formatul nostru
                    isSuccessful = true
                )
                
                Log.d(TAG, "Transcriere finalizată: '${result.text}'")
                return@withContext result
            } ?: run {
                Log.e(TAG, "Leopard nu este inițializat")
                _error.value = "Serviciul de transcriere nu este inițializat"
                return@withContext TranscriptionResult(isSuccessful = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la transcrierea audio: ${e.message}", e)
            _error.value = "Eroare la transcrierea audio: ${e.message}"
            return@withContext TranscriptionResult(isSuccessful = false, errorMessage = e.message)
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Extrage și transcrie un segment specific dintr-un array de date PCM mai mare.
     * 
     * @param pcmData Întregul set de date audio
     * @param startSec Momentul de început al segmentului în secunde
     * @param endSec Momentul de sfârșit al segmentului în secunde
     * @return Transcrierea audio sau null în caz de eroare
     */
    suspend fun transcribeSegment(
        pcmData: ShortArray, 
        startSec: Float, 
        endSec: Float
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        try {
            val startSample = (startSec * SAMPLE_RATE).toInt()
            val endSample = (endSec * SAMPLE_RATE).toInt()
            
            // Validăm limitele
            if (startSample < 0 || endSample > pcmData.size || startSample >= endSample) {
                Log.e(TAG, "Limite de segment invalide: $startSample-$endSample (din ${pcmData.size})")
                _error.value = "Limite de segment invalide"
                return@withContext TranscriptionResult(isSuccessful = false)
            }
            
            // Extragem segmentul
            val segmentLength = endSample - startSample
            val segmentData = ShortArray(segmentLength)
            System.arraycopy(pcmData, startSample, segmentData, 0, segmentLength)
            
            Log.d(TAG, "Extras segment audio de la $startSec la $endSec " +
                       "(${segmentData.size} eșantioane)")
            
            // Transcrierea segmentului
            return@withContext transcribePcmData(segmentData)
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la extragerea segmentului: ${e.message}", e)
            _error.value = "Eroare la extragerea segmentului: ${e.message}"
            return@withContext TranscriptionResult(isSuccessful = false, errorMessage = e.message)
        }
    }
    
    /**
     * Salvează datele PCM ca fișier WAV temporar și apoi îl transcrie.
     * Util pentru debugging și pentru lucrul cu segmente mai lungi.
     */
    suspend fun transcribePcmAsWav(pcmData: ShortArray): TranscriptionResult = withContext(Dispatchers.IO) {
        try {
            _isProcessing.value = true
            _error.value = null
            
            // Creăm un fișier temporar
            val tempFile = File.createTempFile("audio_segment", ".wav", context.cacheDir)
            
            // Scriem datele PCM într-un format WAV valid
            FileOutputStream(tempFile).use { fos ->
                // Header WAV
                writeWavHeader(fos, pcmData.size)
                
                // Datele PCM
                val byteBuffer = ByteBuffer.allocate(pcmData.size * 2)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                for (sample in pcmData) {
                    byteBuffer.putShort(sample)
                }
                fos.write(byteBuffer.array())
            }
            
            Log.d(TAG, "Fișier WAV temporar creat: ${tempFile.absolutePath}")
            
            // Transcrierea fișierului WAV
            leopard?.let {
                val transcriptionResult = it.processFile(tempFile.absolutePath)
                
                // Extragem textul transcris din obiectul LeopardTranscript
                val transcriptionText = transcriptionResult.getTranscriptString()
                
                val result = TranscriptionResult(
                    text = transcriptionText,
                    words = emptyList(), // API-ul nou nu mai oferă informații despre cuvinte individuale în formatul nostru
                    isSuccessful = true
                )
                
                Log.d(TAG, "Transcriere fișier finalizată: '${result.text}'")
                
                // Ștergem fișierul temporar
                tempFile.delete()
                
                return@withContext result
            } ?: run {
                Log.e(TAG, "Leopard nu este inițializat")
                _error.value = "Serviciul de transcriere nu este inițializat"
                tempFile.delete()
                return@withContext TranscriptionResult(isSuccessful = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la transcrierea audio ca WAV: ${e.message}", e)
            _error.value = "Eroare la transcrierea audio: ${e.message}"
            return@withContext TranscriptionResult(isSuccessful = false, errorMessage = e.message)
        } finally {
            _isProcessing.value = false
        }
    }
    
    /**
     * Scrie headerul unui fișier WAV.
     */
    private fun writeWavHeader(outputStream: FileOutputStream, pcmDataSize: Int) {
        try {
            val totalDataLen = pcmDataSize * 2 + 36
            val byteRate = SAMPLE_RATE * 2
            
            outputStream.write("RIFF".toByteArray())
            outputStream.write(intToByteArray(totalDataLen))
            outputStream.write("WAVE".toByteArray())
            outputStream.write("fmt ".toByteArray())
            outputStream.write(intToByteArray(16)) // Sub-chunk size
            outputStream.write(shortToByteArray(1)) // Format = 1 (PCM)
            outputStream.write(shortToByteArray(1)) // Channels = 1 (mono)
            outputStream.write(intToByteArray(SAMPLE_RATE)) // Sample rate
            outputStream.write(intToByteArray(byteRate)) // Byte rate
            outputStream.write(shortToByteArray(2)) // Block align
            outputStream.write(shortToByteArray(16)) // Bits per sample
            outputStream.write("data".toByteArray())
            outputStream.write(intToByteArray(pcmDataSize * 2)) // Data size
        } catch (e: IOException) {
            Log.e(TAG, "Eroare la scrierea headerului WAV: ${e.message}", e)
            throw e
        }
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return ByteArray(4).apply {
            this[0] = (value and 0xFF).toByte()
            this[1] = ((value shr 8) and 0xFF).toByte()
            this[2] = ((value shr 16) and 0xFF).toByte()
            this[3] = ((value shr 24) and 0xFF).toByte()
        }
    }
    
    private fun shortToByteArray(value: Int): ByteArray {
        return ByteArray(2).apply {
            this[0] = (value and 0xFF).toByte()
            this[1] = ((value shr 8) and 0xFF).toByte()
        }
    }

    /**
     * Eliberează resursele utilizate de serviciul de transcriere.
     */
    fun release() {
        try {
            Log.d(TAG, "Eliberare resurse Leopard")
            leopard?.delete()
            leopard = null
            _error.value = null
            _isProcessing.value = false
            Log.d(TAG, "Resurse eliberate cu succes")
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la eliberarea resurselor: ${e.message}", e)
        }
    }
}

/**
 * Model de date pentru rezultatul transcrierii
 */
data class TranscriptionResult(
    val text: String = "",
    val words: List<TranscribedWord> = emptyList(),
    val isSuccessful: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Model de date pentru un cuvânt transcris
 */
data class TranscribedWord(
    val word: String,
    val startSec: Float,
    val endSec: Float,
    val confidence: Float
) 