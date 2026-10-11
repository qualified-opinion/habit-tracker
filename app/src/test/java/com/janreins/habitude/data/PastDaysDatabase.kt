package com.janreins.habitude.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

internal fun pastDaysDatabase(): HabitudeDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext<Context>(), HabitudeDatabase::class.java,
).allowMainThreadQueries().build()
