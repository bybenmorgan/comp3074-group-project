package ca.gbc.comp3074.morgan_ben.group_project

data class HouseholdMember(val uid: String, val label: String)

data class Household(
    val id: String,
    val name: String,
    val hasPet: Boolean,
    val petName: String,
    val memberUids: List<String>,
    internal val memberLabels: Map<String, String> = emptyMap()
) {
    val members: List<HouseholdMember>
        get() = memberUids.map { uid ->
            HouseholdMember(uid, memberLabels[uid]?.takeIf { it.isNotBlank() } ?: uid)
        }
}

data class HouseholdMembership(val households: List<Household>, val activeId: String?) {
    val active: Household? get() = households.firstOrNull { it.id == activeId }
}

internal fun validInviteCode(code: String): Boolean = code.matches(Regex("[A-Za-z0-9]{20}"))

internal fun validHouseholdName(name: String): Boolean = name.isNotBlank() && name.length <= 80

internal fun resolveMembership(
    uid: String,
    households: List<Household>,
    savedActiveId: String?
): HouseholdMembership {
    val joined = households.filter { uid in it.memberUids }.sortedBy { it.id }
    val activeId = savedActiveId?.takeIf { id -> joined.any { it.id == id } }
        ?: joined.firstOrNull()?.id
    return HouseholdMembership(joined, activeId)
}
