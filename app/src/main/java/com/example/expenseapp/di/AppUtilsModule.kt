package com.example.expenseapp.di

import com.example.expenseapp.core.util.FakeOCREngine
import com.example.expenseapp.core.util.OCREngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppUtilsModule {

    @Provides
    @Singleton
    fun provideOCREngine(): OCREngine = FakeOCREngine()
}
