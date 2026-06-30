package com.ionasalgados.app.di

import android.content.Context
import androidx.room.Room
import com.ionasalgados.app.data.local.IonaDatabase
import com.ionasalgados.app.data.local.ServerConfigStore
import com.ionasalgados.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.ionasalgados.app.BuildConfig
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IonaDatabase {
        return Room.databaseBuilder(context, IonaDatabase::class.java, "iona_database")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideProdutoDao(db: IonaDatabase): ProdutoDao = db.produtoDao()
    @Provides fun provideCategoriaDao(db: IonaDatabase): CategoriaDao = db.categoriaDao()
    @Provides fun providePedidoDao(db: IonaDatabase): PedidoDao = db.pedidoDao()
    @Provides fun provideItemPedidoDao(db: IonaDatabase): ItemPedidoDao = db.itemPedidoDao()
    @Provides fun provideClienteDao(db: IonaDatabase): ClienteDao = db.clienteDao()
    @Provides fun provideConfiguracaoDao(db: IonaDatabase): ConfiguracaoDao = db.configuracaoDao()
}
