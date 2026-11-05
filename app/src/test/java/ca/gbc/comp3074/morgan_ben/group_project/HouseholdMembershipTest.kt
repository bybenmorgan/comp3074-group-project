package ca.gbc.comp3074.morgan_ben.group_project

import org.junit.Assert.*
import org.junit.Test

class HouseholdMembershipTest {
    private fun household(id: String, vararg members: String) =
        Household(id, "Home", false, "", members.toList())

    @Test fun inviteCodesRequireAnExactCaseSensitiveDocumentId() {
        assertTrue(validInviteCode("aB0123456789CDefGHiJ"))
        listOf("", " ", "ABC123", "a".repeat(19), "a".repeat(21),
            "a".repeat(19) + "/", "a".repeat(19) + "é", " " + "a".repeat(20))
            .forEach { assertFalse(it, validInviteCode(it)) }
    }

    @Test fun householdNameHasUsefulBounds() {
        assertFalse(validHouseholdName("  "))
        assertTrue(validHouseholdName("Our home"))
        assertTrue(validHouseholdName("a".repeat(80)))
        assertFalse(validHouseholdName("a".repeat(81)))
    }

    @Test fun noMembershipRoutesToSetupEvenWithAnOldSelection() {
        val result = resolveMembership("alice", emptyList(), "old-home")
        assertTrue(result.households.isEmpty())
        assertNull(result.active)
        assertNull(result.activeId)
    }

    @Test fun anotherAccountsMembershipCannotBecomeActive() {
        val result = resolveMembership("alice", listOf(household("bob-home", "bob")), "bob-home")
        assertTrue(result.households.isEmpty())
        assertNull(result.active)
    }

    @Test fun multipleMembershipsKeepTheUsersSelection() {
        val a = household("a", "alice", "bob")
        val b = household("b", "alice")
        val result = resolveMembership("alice", listOf(b, a), "b")
        assertEquals(listOf(a, b), result.households)
        assertEquals(b, result.active)
        assertEquals(listOf("alice", "bob"), result.households.first().memberUids)
    }

    @Test fun leavingActiveHouseholdSelectsADeterministicRemainingMembership() {
        val result = resolveMembership("alice", listOf(
            household("z", "alice"), household("left", "bob"), household("a", "alice")
        ), "left")
        assertEquals("a", result.activeId)
        assertEquals(listOf("a", "z"), result.households.map { it.id })
    }

    @Test fun leavingLastMembershipDoesNotKeepAPrototypeHousehold() {
        val result = resolveMembership("alice", listOf(household("left", "bob")), "left")
        assertNull(result.active)
        assertTrue(result.households.isEmpty())
    }
}
