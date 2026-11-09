package ca.gbc.comp3074.morgan_ben.group_project

import androidx.core.util.PatternsCompat

internal data class AccountInputErrors(
    val email: Int?,
    val password: Int?,
    val confirmation: Int?
) {
    val isValid: Boolean
        get() = email == null && password == null && confirmation == null
}

internal fun validateAccountInput(
    email: String,
    password: String,
    confirmation: String? = null
): AccountInputErrors = AccountInputErrors(
    email = when {
        email.isEmpty() -> R.string.email_required
        !PatternsCompat.EMAIL_ADDRESS.matcher(email).matches() -> R.string.email_invalid
        else -> null
    },
    password = when {
        password.isEmpty() -> R.string.password_required
        confirmation != null && password.length < 6 -> R.string.password_too_short
        else -> null
    },
    confirmation = when {
        confirmation == null -> null
        confirmation.isEmpty() -> R.string.confirm_password_required
        confirmation != password -> R.string.passwords_do_not_match
        else -> null
    }
)
