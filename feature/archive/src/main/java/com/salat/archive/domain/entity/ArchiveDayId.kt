package com.salat.archive.domain.entity

import java.util.Calendar
import java.util.Date

data class ArchiveDayId(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
) : Comparable<ArchiveDayId> {

    override fun compareTo(other: ArchiveDayId): Int {
        return compareValuesBy(this, other, ArchiveDayId::year, ArchiveDayId::month, ArchiveDayId::dayOfMonth)
    }

    fun toDate(): Date {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    fun nextDay(): ArchiveDayId {
        val calendar = Calendar.getInstance().apply { time = toDate() }
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return from(calendar.time)
    }

    companion object {
        fun from(date: Date): ArchiveDayId {
            val calendar = Calendar.getInstance().apply { time = date }
            return ArchiveDayId(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH),
                dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
            )
        }
    }
}
