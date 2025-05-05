package com.example.aviv1.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manager pentru cheile API folosite în aplicație.
 * Stochează și gestionează cheile într-un mod securizat.
 */
class ApiKeyManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ApiKeyManager"
        private const val PREFS_NAME = "api_key_prefs"
        private const val IV_KEY = "iv_data"
        private const val ENCRYPTION_KEY = "encryption_key"
        
        // Hardcoded values that will be encrypted in storage
        private const val PICOVOICE_ACCESS_KEY = ""
        private const val OPENAI_API_KEY = ""
        
        @Volatile
        private var instance: ApiKeyManager? = null
        
        fun getInstance(context: Context): ApiKeyManager {
            return instance ?: synchronized(this) {
                instance ?: ApiKeyManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private var secretKey: SecretKey? = null
    private var iv: ByteArray? = null
    
    init {
        setupEncryption()
    }
    
    private fun setupEncryption() {
        try {
            // Verificăm dacă avem deja o cheie de criptare
            if (!prefs.contains(ENCRYPTION_KEY)) {
                // Generăm o nouă cheie de criptare
                val keyGenerator = KeyGenerator.getInstance("AES")
                keyGenerator.init(256) // Folosim AES-256
                secretKey = keyGenerator.generateKey()
                
                // Salvăm cheia de criptare
                val encodedKey = Base64.encodeToString(secretKey!!.encoded, Base64.DEFAULT)
                prefs.edit().putString(ENCRYPTION_KEY, encodedKey).apply()
                
                // Generăm un vector de inițializare
                val ivRandom = SecureRandom()
                iv = ByteArray(16)
                ivRandom.nextBytes(iv!!)
                
                // Salvăm vectorul de inițializare
                val encodedIv = Base64.encodeToString(iv, Base64.DEFAULT)
                prefs.edit().putString(IV_KEY, encodedIv).apply()
                
                // Criptăm și salvăm cheile API
                encryptAndSaveKeys()
            } else {
                // Încărcăm cheia de criptare existentă
                val encodedKey = prefs.getString(ENCRYPTION_KEY, null)
                if (encodedKey != null) {
                    val keyBytes = Base64.decode(encodedKey, Base64.DEFAULT)
                    secretKey = SecretKeySpec(keyBytes, "AES")
                }
                
                // Încărcăm vectorul de inițializare
                val encodedIv = prefs.getString(IV_KEY, null)
                if (encodedIv != null) {
                    iv = Base64.decode(encodedIv, Base64.DEFAULT)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la configurarea criptării: ${e.message}")
        }
    }
    
    private fun encryptAndSaveKeys() {
        try {
            // Salvăm cheia Picovoice
            val encryptedPicovoiceKey = encrypt(PICOVOICE_ACCESS_KEY)
            prefs.edit().putString("picovoice_key", encryptedPicovoiceKey).apply()
            
            // Salvăm cheia OpenAI
            val encryptedOpenAIKey = encrypt(OPENAI_API_KEY)
            prefs.edit().putString("openai_key", encryptedOpenAIKey).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la criptarea cheilor: ${e.message}")
        }
    }
    
    private fun encrypt(text: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la criptare: ${e.message}")
            ""
        }
    }
    
    private fun decrypt(encryptedText: String): String {
        return try {
            val encrypted = Base64.decode(encryptedText, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la decriptare: ${e.message}")
            ""
        }
    }
    
    /**
     * Returnează cheia de acces Picovoice.
     */
    fun getPicovoiceAccessKey(): String {
        val encryptedKey = prefs.getString("picovoice_key", null)
        return if (encryptedKey != null) {
            decrypt(encryptedKey)
        } else {
            // Dacă nu există încă o cheie salvată, o criptăm și o salvăm
            encryptAndSaveKeys()
            PICOVOICE_ACCESS_KEY
        }
    }
    
    /**
     * Returnează cheia API OpenAI.
     */
    fun getOpenAIApiKey(): String {
        val encryptedKey = prefs.getString("openai_key", null)
        return if (encryptedKey != null) {
            decrypt(encryptedKey)
        } else {
            // Dacă nu există încă o cheie salvată, o criptăm și o salvăm
            encryptAndSaveKeys()
            OPENAI_API_KEY
        }
    }
} 