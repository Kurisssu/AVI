package com.example.aviv1.service

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Model pentru solicitare API
data class AIRequestBody(
    val model: String = "gpt-3.5-turbo",
    val messages: List<AIMessage>,
    val temperature: Double = 0.7
)

data class AIMessage(
    val role: String,
    val content: String
)

// Model pentru răspuns API
data class AIResponseBody(
    val id: String,
    val created: Long,
    val model: String,
    val choices: List<AIChoice>,
    val usage: AIUsage
)

data class AIChoice(
    val index: Int,
    @SerializedName("message")
    val message: AIMessage,
    @SerializedName("finish_reason")
    val finishReason: String
)

data class AIUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

// Interfața pentru API
interface AIApiInterface {
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun generateResponse(
        @Body requestBody: AIRequestBody
    ): Response<AIResponseBody>
}

class AIApiService {
    companion object {
        private const val BASE_URL = "https://api.openai.com/"
        private const val API_KEY = "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" // Trebuie completat cu cheia API reală

        private val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        private val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Authorization", "Bearer $API_KEY")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService: AIApiInterface = retrofit.create(AIApiInterface::class.java)
    }

    suspend fun getAIResponse(userMessages: List<AIMessage>): String {
        try {
            // Adaugăm un sistem prompt pentru a instrui modelul să extragă și să răspundă la întrebări
            val systemPrompt = AIMessage(
                role = "system",
                content = "Ești un asistent vocal inteligent. Extrage întrebările din conversația utilizatorului și răspunde-le cu informații utile și precise. Dacă nu există întrebări explicite, interpretează intenția utilizatorului și oferă un răspuns util."
            )
            
            val allMessages = mutableListOf(systemPrompt)
            allMessages.addAll(userMessages)
            
            val requestBody = AIRequestBody(
                messages = allMessages
            )
            
            val response = apiService.generateResponse(requestBody)
            
            if (response.isSuccessful) {
                response.body()?.let { aiResponse ->
                    if (aiResponse.choices.isNotEmpty()) {
                        return aiResponse.choices[0].message.content
                    }
                }
            }
            
            return "Nu am putut genera un răspuns. Te rog să încerci din nou."
        } catch (e: Exception) {
            e.printStackTrace()
            return "S-a produs o eroare: ${e.message}"
        }
    }
} 