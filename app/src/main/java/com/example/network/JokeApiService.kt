package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class JokeResponse(
    @Json(name = "type") val type: String = "single",
    @Json(name = "joke") val joke: String? = null,
    @Json(name = "setup") val setup: String? = null,
    @Json(name = "delivery") val delivery: String? = null,
    @Json(name = "category") val category: String? = "Programming",
    @Json(name = "error") val error: Boolean = false
) {
    val fullJokeText: String
        get() = when {
            type == "twopart" && setup != null && delivery != null -> "$setup\n\n💬 $delivery"
            !joke.isNullOrBlank() -> joke
            else -> "Why do programmers prefer dark mode? Because light attracts bugs!"
        }
}

interface JokeApiService {
    @GET("joke/Programming,Misc?blacklistFlags=nsfw,religious,political,racist,sexist,explicit")
    suspend fun getRandomJoke(): JokeResponse
}

object JokeRetrofitClient {
    private const val BASE_URL = "https://v2.jokeapi.dev/"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: JokeApiService by lazy {
        retrofit.create(JokeApiService::class.java)
    }
}
