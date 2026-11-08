package ca.gbc.comp3074.morgan_ben.group_project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountValidationTest {
    @Test
    fun loginRequiresEmailAndPassword() {
        val errors = validateAccountInput("", "")
        assertEquals(R.string.email_required, errors.email)
        assertEquals(R.string.password_required, errors.password)
        assertFalse(errors.isValid)
    }

    @Test
    fun malformedEmailIsRejected() {
        for (email in listOf("roommate", "roommate@", "@example.com", "a b@example.com")) {
            assertEquals(R.string.email_invalid, validateAccountInput(email, "secret").email)
        }
    }

    @Test
    fun loginDoesNotApplyRegistrationPasswordMinimum() {
        assertTrue(validateAccountInput("roommate@example.com", "x").isValid)
    }

    @Test
    fun registrationRequiresSixCharactersAndConfirmation() {
        val errors = validateAccountInput("roommate@example.com", "12345", "")
        assertEquals(R.string.password_too_short, errors.password)
        assertEquals(R.string.confirm_password_required, errors.confirmation)
        assertFalse(errors.isValid)
        assertTrue(validateAccountInput("roommate@example.com", "123456", "123456").isValid)
    }

    @Test
    fun confirmationMustMatchCaseAndWhitespaceExactly() {
        for (confirmation in listOf("Secret", " secret", "secret ")) {
            val errors = validateAccountInput("roommate@example.com", "secret", confirmation)
            assertEquals(R.string.passwords_do_not_match, errors.confirmation)
            assertFalse(errors.isValid)
        }
    }

    @Test
    fun passwordWhitespaceIsSignificant() {
        assertTrue(validateAccountInput("roommate@example.com", " pass ", " pass ").isValid)
        assertTrue(validateAccountInput("roommate@example.com", "      ", "      ").isValid)
        assertNull(validateAccountInput("roommate@example.com", " ").password)
    }
}
