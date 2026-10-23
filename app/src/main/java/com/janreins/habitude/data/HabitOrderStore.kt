package com.janreins.habitude.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Where the order you put your habits in is kept: a list of habit ids. */
interface HabitOrderStore {
    val order: Flow<List<Long>>

    fun setOrder(ids: List<Long>)

    /** No saved order: habits show in the order they were made. */
    object None : HabitOrderStore {
        override val order: Flow<List<Long>> = flowOf(emptyList())
        override fun setOrder(ids: List<Long>) = Unit
    }
}
