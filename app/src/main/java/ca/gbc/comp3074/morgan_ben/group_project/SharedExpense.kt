package ca.gbc.comp3074.morgan_ben.group_project

import java.util.Locale

data class SharedExpense(
    val id: String,
    val description: String,
    val amountCents: Long,
    val payerUid: String,
    val payerLabel: String,
    val participantUids: List<String>,
    val creatorUid: String,
    val dateMillis: Long
) {
    fun shares(): Map<String, Long> {
        val participants = participantUids.sorted()
        return participants.mapIndexed { index, uid ->
            uid to amountCents / participants.size +
                if (index.toLong() < amountCents % participants.size) 1L else 0L
        }.toMap()
    }
}

internal const val MAX_EXPENSE_CENTS = 999_999_999L

internal fun parseExpenseCents(input: String): Long? {
    val text = input.trim()
    if (!text.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))) return null
    val parts = text.split('.')
    val dollars = parts[0].toLongOrNull() ?: return null
    if (dollars > MAX_EXPENSE_CENTS / 100) return null
    val cents = dollars * 100 + (parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0)
    return cents.takeIf { it in 1..MAX_EXPENSE_CENTS }
}

internal fun expenseMoney(cents: Long): String =
    String.format(Locale.US, "$%d.%02d", cents / 100, cents % 100)

// Positive means the user owes this person; negative means this person owes the user.
internal fun expenseDebts(uid: String, expenses: List<SharedExpense>): Map<String, Long> {
    val debts = mutableMapOf<String, Long>()
    expenses.forEach { expense ->
        val shares = expense.shares()
        if (expense.payerUid == uid) {
            shares.filterKeys { it != uid }.forEach { (person, share) ->
                debts[person] = (debts[person] ?: 0) - share
            }
        } else {
            debts[expense.payerUid] = (debts[expense.payerUid] ?: 0) + (shares[uid] ?: 0)
        }
    }
    return debts.filterValues { it != 0L }.toSortedMap()
}
