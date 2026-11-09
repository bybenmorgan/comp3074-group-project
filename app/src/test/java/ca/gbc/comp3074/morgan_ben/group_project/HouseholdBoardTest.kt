package ca.gbc.comp3074.morgan_ben.group_project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseholdBoardTest {
    @Test
    fun choresKeepMemberWrittenFieldsAndIgnoreBlankTitles() {
        val chore = parseChore("chore-1", mapOf("title" to "Sweep", "assigneeUid" to "bob", "done" to true), 5)
        assertEquals(ChoreItem("chore-1", "Sweep", "bob", "", true, 5), chore)
        assertNull(parseChore("blank", mapOf("title" to "  ")))
        assertNull(parseChore("", mapOf("title" to "Sweep")))
    }

    @Test
    fun groceriesAndAppointmentsRejectOversizedText() {
        assertEquals(GroceryItem("g", "Milk", false, 2), parseGrocery("g", mapOf("name" to "Milk"), 2))
        assertNull(parseGrocery("g", mapOf("name" to "m".repeat(121))))
        assertNull(parseAppointment("a", mapOf("title" to "Vet", "date" to "d".repeat(41), "time" to "9:00")))
        assertEquals(
            AppointmentItem("a", "Vet", "Oct. 12", "9:00", 3),
            parseAppointment("a", mapOf("title" to "Vet", "date" to "Oct. 12", "time" to "9:00"), 3)
        )
    }

    @Test
    fun listsSortByCreationThenId() {
        val chores = sortedChores(listOf(
            ChoreItem("b", "Later", "Ada", "Today", false, 20),
            ChoreItem("a", "Earlier", "Ben", "Today", true, 10),
            ChoreItem("c", "Same", "Ada", "Today", false, 10)
        ))
        assertEquals(listOf("a", "c", "b"), chores.map { it.id })
    }

    @Test
    fun petFlagsFollowTheHouseholdTaskKeys() {
        val pet = parsePetCare(mapOf(
            HouseholdRepository.PET_MORNING_WALK to true,
            HouseholdRepository.PET_DINNER to true,
            "extra" to true
        ))
        assertTrue(pet.checked(HouseholdRepository.PET_MORNING_WALK))
        assertFalse(pet.checked(HouseholdRepository.PET_NIGHT_WALK))
        assertTrue(pet.with(HouseholdRepository.PET_BREAKFAST, true).breakfast)
        assertEquals(PetCareState(), parsePetCare(null))
        assertTrue(validPetTask(HouseholdRepository.PET_NIGHT_WALK))
        assertFalse(validPetTask("bath"))
    }
}
