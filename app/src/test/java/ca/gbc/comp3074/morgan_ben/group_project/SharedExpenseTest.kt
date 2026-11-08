package ca.gbc.comp3074.morgan_ben.group_project

import org.junit.Assert.*
import org.junit.Test

class SharedExpenseTest {
    private fun expense(cents: Long, payer: String = "alice", people: List<String> = listOf("alice", "bob")) =
        SharedExpense("id", "Food", cents, payer, "$payer@example.com", people, "alice", 1)

    @Test fun parsesWholeCentsExactly() {
        mapOf("1" to 100L, "1.2" to 120L, "1.23" to 123L, "0.01" to 1L,
            " 24.50 " to 2450L, "0001.09" to 109L, "9999999.99" to MAX_EXPENSE_CENTS)
            .forEach { (input, expected) -> assertEquals(input, expected, parseExpenseCents(input)) }
    }

    @Test fun rejectsInvalidOrExcessPrecisionAmounts() {
        listOf("", " ", "0", "0.00", "-1", "+1", "1.234", "1.000", "1.", ".50", "$1.00",
            "1,000", "1,20", "NaN", "Infinity", "1e2", "10000000", "9999999.999",
            "9999999999999999999999999", "1\n2").forEach { assertNull(it, parseExpenseCents(it)) }
    }

    @Test fun formatsCentsWithoutFloatingPoint() {
        assertEquals("$0.01", expenseMoney(1))
        assertEquals("$34.17", expenseMoney(3417))
        assertEquals("$9999999.99", expenseMoney(MAX_EXPENSE_CENTS))
    }

    @Test fun singleMemberOwesNobody() {
        val item = expense(101, people = listOf("alice"))
        assertEquals(mapOf("alice" to 101L), item.shares())
        assertTrue(expenseDebts("alice", listOf(item)).isEmpty())
    }

    @Test fun twoMembersAndDifferentPayers() {
        assertEquals(mapOf("alice" to 50L), expenseDebts("bob", listOf(expense(100))))
        assertEquals(mapOf("bob" to -50L), expenseDebts("alice", listOf(expense(100))))
        assertEquals(mapOf("bob" to 50L), expenseDebts("alice", listOf(expense(100, "bob"))))
    }

    @Test fun threeMembersSplitRemainderInUidOrder() {
        val item = expense(100, "carol", listOf("carol", "bob", "alice"))
        assertEquals(mapOf("alice" to 34L, "bob" to 33L, "carol" to 33L), item.shares())
        assertEquals(100L, item.shares().values.sum())
        assertEquals(mapOf("carol" to 34L), expenseDebts("alice", listOf(item)))
        assertEquals(mapOf("alice" to -34L, "bob" to -33L), expenseDebts("carol", listOf(item)))
    }

    @Test fun amountSmallerThanParticipantCountStillBalances() {
        val item = expense(1, "bob", listOf("carol", "bob", "alice"))
        assertEquals(mapOf("alice" to 1L, "bob" to 0L, "carol" to 0L), item.shares())
        assertEquals(mapOf("alice" to -1L), expenseDebts("bob", listOf(item)))
    }

    @Test fun oppositeDebtsOffsetOnlyBetweenTheSamePeople() {
        val items = listOf(expense(100), expense(60, "bob"))
        assertEquals(mapOf("bob" to -20L), expenseDebts("alice", items))
        assertEquals(mapOf("alice" to 20L), expenseDebts("bob", items))
        assertTrue(expenseDebts("alice", listOf(expense(100), expense(100, "bob"))).isEmpty())
    }

    @Test fun zeroNetCanStillHaveSeparateDebts() {
        val items = listOf(expense(100, "bob"), expense(100, people = listOf("alice", "carol")))
        val debts = expenseDebts("alice", items)
        assertEquals(0L, debts.values.sum())
        assertEquals(mapOf("bob" to 50L, "carol" to -50L), debts)
    }

    @Test fun membershipChangesDoNotChangeHistoricalShares() {
        val old = expense(101)
        val new = expense(101, people = listOf("alice", "carol", "dave"))
        assertEquals(mapOf("alice" to 51L, "bob" to 50L), old.shares())
        assertEquals(mapOf("alice" to 34L, "carol" to 34L, "dave" to 33L), new.shares())
        assertEquals(mapOf("alice" to 50L), expenseDebts("bob", listOf(old, new)))
        assertEquals("alice@example.com", old.payerLabel)
    }

    @Test fun unrelatedUserHasNoDebts() {
        assertTrue(expenseDebts("carol", listOf(expense(100))).isEmpty())
    }

    @Test fun allSharesAndAllBalancesConserveMoney() {
        val people = listOf("alice", "bob", "carol", "dave")
        val items = people.mapIndexed { i, payer -> expense(101L + i, payer, people.reversed()) }
        items.forEach { assertEquals(it.amountCents, it.shares().values.sum()) }
        assertEquals(0L, people.sumOf { expenseDebts(it, items).values.sum() })
    }
}
