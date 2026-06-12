package com.example.taxi.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Para emulador Android: 10.0.2.2 apunta a localhost del host
    // Para dispositivo físico: usa la IP de tu PC en la red local (ej: 192.168.1.X)
    private const val BASE_URL = "http://10.0.2.2:3000/"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
