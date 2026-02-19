package com.virtualcoverage.signalmap.di

import com.virtualcoverage.signalmap.data.remote.SignalApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Backend server URL.
     * 
     * For development: Use your computer's local IP address (not localhost/127.0.0.1)
     * because the phone connects over Wi-Fi.
     *
     * To find your IP: Run 'ipconfig' in PowerShell and look for
     * "Wireless LAN adapter Wi-Fi" → "IPv4 Address"
     * 
     * For production: Replace with your deployed server URL.
     */
    private const val BASE_URL = "http://192.168.0.109:3000"
    // Your computer's Wi-Fi IP. Update if your IP changes.
    // Find IP: Run 'ipconfig' in PowerShell → "Wi-Fi" → "IPv4 Address"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)  // Longer for batch uploads
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideSignalApiService(retrofit: Retrofit): SignalApiService {
        return retrofit.create(SignalApiService::class.java)
    }
}
