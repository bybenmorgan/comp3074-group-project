package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HouseholdRepository(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun currentHousehold(): Household = checkNotNull(HouseholdSession.current()?.active)

    companion object {
        const val PET_MORNING_WALK = "morning_walk"
        const val PET_NIGHT_WALK = "night_walk"
        const val PET_BREAKFAST = "breakfast"
        const val PET_DINNER = "dinner"

        fun todayLong(): String = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())

        fun todayShort(): String = SimpleDateFormat("MMM. d", Locale.US).format(Date())

        fun todayHeader(): String = SimpleDateFormat("EEEE", Locale.US).format(Date())

        fun todayBadge(): String = SimpleDateFormat("d\nMMM", Locale.US).format(Date()).uppercase(Locale.US)
    }
}
