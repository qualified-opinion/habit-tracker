package com.janreins.habitude.domain

/**
 * The order you put your habits in. Kept as a list of habit ids; habits missing from it (new
 * ones, say) go after the rest, oldest first.
 */
object HabitOrder {

    fun sort(items: List<HabitWithEntries>, order: List<Long>): List<HabitWithEntries> {
        val rank = order.withIndex().associate { (index, id) -> id to index }
        return items.sortedWith(compareBy({ rank[it.habit.id] ?: Int.MAX_VALUE }, { it.habit.id }))
    }

    /**
     * [all] (every habit id, in order) after moving [id] one place up ([delta] -1) or down (+1)
     * among [visible], the habits shown together. It swaps places with its neighbour there, so
     * habits shown elsewhere keep their spots. Unchanged when it's already at that end.
     */
    fun move(all: List<Long>, visible: List<Long>, id: Long, delta: Int): List<Long> {
        val from = visible.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in visible.indices) return all
        val other = visible[to]
        return all.map {
            when (it) {
                id -> other
                other -> id
                else -> it
            }
        }
    }

    fun encode(ids: List<Long>): String = ids.joinToString(",")

    fun decode(text: String?): List<Long> = text.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
}
