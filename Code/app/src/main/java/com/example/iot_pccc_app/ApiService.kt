package com.example.iot_pccc_app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// JSON trả về từ /sensor
data class SensorResponse(
    val temp: Double? = null,
    val gas: Double? = null,
    val timestamp: String? = null
)

interface ApiService {
    @GET("/sensor")
    suspend fun getSensor(): SensorResponse
}

object ApiClient {

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    lateinit var api: ApiService

    // ipWithPort: ví dụ "192.168.1.16:8000"
    fun initClient(ipWithPort: String) {
        val baseUrl = "http://$ipWithPort/"

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit!!.create(ApiService::class.java)
    }
}
