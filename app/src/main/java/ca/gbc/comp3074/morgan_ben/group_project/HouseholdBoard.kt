package ca.gbc.comp3074.morgan_ben.group_project

internal const val MAX_HOUSEHOLD_LIST = 100

data class ChoreItem(
    val id: String,
    val title: String,
    val assignee: String,
    val due: String,
    val done: Boolean,
    val createdAtMillis: Long = 0
)

data class GroceryItem(
    val id: String,
    val name: String,
    val checked: Boolean,
    val createdAtMillis: Long = 0
)

data class AppointmentItem(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val createdAtMillis: Long = 0
)

data class PetCareState(
    val morningWalk: Boolean = false,
    val nightWalk: Boolean = false,
    val breakfast: Boolean = false,
    val dinner: Boolean = false
) {
    fun checked(key: String): Boolean = when (key) {
        HouseholdRepository.PET_MORNING_WALK -> morningWalk
        HouseholdRepository.PET_NIGHT_WALK -> nightWalk
        HouseholdRepository.PET_BREAKFAST -> breakfast
        HouseholdRepository.PET_DINNER -> dinner
        else -> false
    }

    fun with(key: String, checked: Boolean): PetCareState = when (key) {
        HouseholdRepository.PET_MORNING_WALK -> copy(morningWalk = checked)
        HouseholdRepository.PET_NIGHT_WALK -> copy(nightWalk = checked)
        HouseholdRepository.PET_BREAKFAST -> copy(breakfast = checked)
        HouseholdRepository.PET_DINNER -> copy(dinner = checked)
        else -> this
    }
}

data class HouseholdBoard(
    val chores: List<ChoreItem> = emptyList(),
    val groceries: List<GroceryItem> = emptyList(),
    val appointments: List<AppointmentItem> = emptyList(),
    val pet: PetCareState = PetCareState()
)

internal fun validListText(value: String, max: Int): Boolean =
    value.isNotBlank() && value.length <= max

internal fun parseChore(id: String, data: Map<String, Any?>, createdAtMillis: Long = 0): ChoreItem? {
    if (id.isEmpty()) return null
    val title = data["title"] as? String ?: return null
    if (!validListText(title, 120)) return null
    val assignee = (data["assignee"] as? String) ?: (data["assigneeUid"] as? String).orEmpty()
    if (assignee.length > 80) return null
    val due = data["due"] as? String ?: ""
    if (due.length > 40) return null
    return ChoreItem(id, title.trim(), assignee, due, data["done"] as? Boolean ?: false, createdAtMillis)
}

internal fun parseGrocery(id: String, data: Map<String, Any?>, createdAtMillis: Long = 0): GroceryItem? {
    if (id.isEmpty()) return null
    val name = data["name"] as? String ?: return null
    if (!validListText(name, 120)) return null
    return GroceryItem(id, name.trim(), data["checked"] as? Boolean ?: false, createdAtMillis)
}

internal fun parseAppointment(
    id: String,
    data: Map<String, Any?>,
    createdAtMillis: Long = 0
): AppointmentItem? {
    if (id.isEmpty()) return null
    val title = data["title"] as? String ?: return null
    if (!validListText(title, 120)) return null
    val date = data["date"] as? String ?: ""
    val time = data["time"] as? String ?: ""
    if (date.length > 40 || time.length > 40) return null
    return AppointmentItem(id, title.trim(), date, time, createdAtMillis)
}

internal fun parsePetCare(data: Map<String, Any?>?): PetCareState {
    fun flag(key: String) = data?.get(key) as? Boolean ?: false
    return PetCareState(
        flag(HouseholdRepository.PET_MORNING_WALK),
        flag(HouseholdRepository.PET_NIGHT_WALK),
        flag(HouseholdRepository.PET_BREAKFAST),
        flag(HouseholdRepository.PET_DINNER)
    )
}

internal fun sortedChores(items: List<ChoreItem>): List<ChoreItem> =
    items.sortedWith(compareBy<ChoreItem> { it.createdAtMillis }.thenBy { it.id })

internal fun sortedGroceries(items: List<GroceryItem>): List<GroceryItem> =
    items.sortedWith(compareBy<GroceryItem> { it.createdAtMillis }.thenBy { it.id })

internal fun sortedAppointments(items: List<AppointmentItem>): List<AppointmentItem> =
    items.sortedWith(compareBy<AppointmentItem> { it.createdAtMillis }.thenBy { it.id })

internal fun validPetTask(key: String): Boolean = key in setOf(
    HouseholdRepository.PET_MORNING_WALK,
    HouseholdRepository.PET_NIGHT_WALK,
    HouseholdRepository.PET_BREAKFAST,
    HouseholdRepository.PET_DINNER
)
