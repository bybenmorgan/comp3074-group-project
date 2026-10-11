package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChoreItem(
    val title: String,
    val assignee: String,
    val due: String,
    val done: Boolean
)

data class ExpenseItem(
    val name: String,
    val amount: Double,
    val payer: String,
    val date: String
)

data class GroceryItem(
    val name: String,
    val checked: Boolean
)

data class AppointmentItem(
    val title: String,
    val date: String,
    val time: String
)

class HouseholdRepository(context: Context) {
    private val app = context.applicationContext

    private val choresPrefs: SharedPreferences
        get() = app.getSharedPreferences(CHORES_PREFS, Context.MODE_PRIVATE)
    private val expensePrefs: SharedPreferences
        get() = app.getSharedPreferences(EXPENSE_PREFS, Context.MODE_PRIVATE)
    private val groceryPrefs: SharedPreferences
        get() = app.getSharedPreferences(GROCERY_PREFS, Context.MODE_PRIVATE)
    private val petPrefs: SharedPreferences
        get() = app.getSharedPreferences(PET_PREFS, Context.MODE_PRIVATE)

    fun chores(): List<ChoreItem> {
        val stored = choresPrefs.getString(KEY_CHORES, null)
        if (stored == null) {
            val starter = defaultChores()
            saveChores(starter)
            return starter
        }
        return stored.split("\n").filter { it.isNotBlank() }.map { line ->
            val parts = line.split("|")
            ChoreItem(
                title = parts.getOrElse(0) { app.getString(R.string.chore_default_title) },
                assignee = parts.getOrElse(1) { app.getString(R.string.chore_assigned_ben) },
                due = parts.getOrElse(2) { app.getString(R.string.chore_due_today) },
                done = parts.getOrNull(3)?.toBoolean() == true
            )
        }
    }

    fun saveChores(items: List<ChoreItem>) {
        val encoded = items.joinToString("\n") {
            "${it.title}|${it.assignee}|${it.due}|${it.done}"
        }
        choresPrefs.edit().putString(KEY_CHORES, encoded).apply()
    }

    fun expenses(): List<ExpenseItem> {
        val stored = expensePrefs.getString(KEY_EXPENSES, null)
        if (stored == null) {
            val starter = defaultExpenses()
            saveExpenses(starter)
            return starter
        }
        return stored.split("\n").filter { it.isNotBlank() }.map { line ->
            val parts = line.split("|")
            ExpenseItem(
                name = parts.getOrElse(0) { app.getString(R.string.expense_default_title) },
                amount = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                payer = parts.getOrElse(2) { app.getString(R.string.expense_payer_ben) },
                date = parts.getOrElse(3) { todayShort() }
            )
        }
    }

    fun saveExpenses(items: List<ExpenseItem>) {
        val encoded = items.joinToString("\n") {
            "${it.name}|${it.amount}|${it.payer}|${it.date}"
        }
        expensePrefs.edit().putString(KEY_EXPENSES, encoded).apply()
    }

    fun groceries(): List<GroceryItem> {
        val stored = groceryPrefs.getString(KEY_GROCERIES, null)
        if (stored == null) {
            val starter = defaultGroceries()
            saveGroceries(starter)
            return starter
        }
        return stored.split("\n").filter { it.isNotBlank() }.map { line ->
            val parts = line.split("|")
            GroceryItem(parts[0], parts.getOrNull(1)?.toBoolean() == true)
        }
    }

    fun saveGroceries(items: List<GroceryItem>) {
        val encoded = items.joinToString("\n") { "${it.name}|${it.checked}" }
        groceryPrefs.edit().putString(KEY_GROCERIES, encoded).apply()
    }

    fun petChecked(key: String): Boolean = petPrefs.getBoolean(key, false)

    fun setPetChecked(key: String, checked: Boolean) {
        petPrefs.edit().putBoolean(key, checked).apply()
    }

    fun appointments(): List<AppointmentItem> {
        val stored = petPrefs.getString(KEY_APPOINTMENTS, null)
        if (stored == null) {
            val starter = listOf(
                AppointmentItem(
                    app.getString(R.string.pet_default_appointment_title),
                    app.getString(R.string.pet_default_appointment_date),
                    app.getString(R.string.pet_default_appointment_time)
                )
            )
            saveAppointments(starter)
            return starter
        }
        return stored.split("\n").filter { it.isNotBlank() }.map { line ->
            val parts = line.split("|")
            AppointmentItem(
                parts.getOrElse(0) { line },
                parts.getOrElse(1) { "" },
                parts.getOrElse(2) { "" }
            )
        }
    }

    fun saveAppointments(items: List<AppointmentItem>) {
        val encoded = items.joinToString("\n") { "${it.title}|${it.date}|${it.time}" }
        petPrefs.edit().putString(KEY_APPOINTMENTS, encoded).apply()
    }

    fun youOwe(): Double {
        val you = app.getString(R.string.expense_payer_you)
        val hannah = app.getString(R.string.expense_payer_hannah)
        var paidByYou = 0.0
        var paidByOthers = 0.0
        expenses().forEach { expense ->
            if (expense.payer.equals(you, true) || expense.payer.equals(hannah, true)) {
                paidByYou += expense.amount
            } else {
                paidByOthers += expense.amount
            }
        }
        return (paidByOthers - paidByYou) / 2.0
    }

    private fun defaultChores(): List<ChoreItem> = listOf(
        ChoreItem(
            app.getString(R.string.chore_clean_washroom),
            app.getString(R.string.chore_assigned_ben),
            app.getString(R.string.chore_due_today),
            false
        ),
        ChoreItem(
            app.getString(R.string.chore_take_out_trash),
            app.getString(R.string.chore_assigned_hannah),
            app.getString(R.string.chore_due_today),
            false
        ),
        ChoreItem(
            app.getString(R.string.chore_vacuum_carpet),
            app.getString(R.string.chore_assigned_ben),
            app.getString(R.string.chore_due_today),
            false
        )
    )

    private fun defaultExpenses(): List<ExpenseItem> = listOf(
        ExpenseItem(app.getString(R.string.expense_internet), 80.00, app.getString(R.string.expense_payer_ben), app.getString(R.string.expense_internet_date)),
        ExpenseItem(app.getString(R.string.expense_groceries), 34.17, app.getString(R.string.expense_payer_hannah), app.getString(R.string.expense_groceries_date)),
        ExpenseItem(app.getString(R.string.expense_rent), 1800.00, app.getString(R.string.expense_payer_hannah), app.getString(R.string.expense_rent_date)),
        ExpenseItem(app.getString(R.string.expense_pet_supplies), 60.00, app.getString(R.string.expense_payer_ben), app.getString(R.string.expense_pet_supplies_date))
    )

    private fun defaultGroceries(): List<GroceryItem> = listOf(
        GroceryItem(app.getString(R.string.grocery_carrots), false),
        GroceryItem(app.getString(R.string.grocery_milk), false),
        GroceryItem(app.getString(R.string.grocery_eggs), false),
        GroceryItem(app.getString(R.string.grocery_bread), false),
        GroceryItem(app.getString(R.string.grocery_dog_food), false)
    )

    companion object {
        const val PET_MORNING_WALK = "morning_walk"
        const val PET_NIGHT_WALK = "night_walk"
        const val PET_BREAKFAST = "breakfast"
        const val PET_DINNER = "dinner"

        private const val CHORES_PREFS = "chores"
        private const val EXPENSE_PREFS = "expenses"
        private const val GROCERY_PREFS = "groceries"
        private const val PET_PREFS = "pet_care"
        private const val KEY_CHORES = "items"
        private const val KEY_EXPENSES = "items"
        private const val KEY_GROCERIES = "items"
        private const val KEY_APPOINTMENTS = "appointments"

        fun todayLong(): String = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())

        fun todayShort(): String = SimpleDateFormat("MMM. d", Locale.US).format(Date())

        fun todayHeader(): String = SimpleDateFormat("EEEE", Locale.US).format(Date())

        fun todayBadge(): String = SimpleDateFormat("d\nMMM", Locale.US).format(Date()).uppercase(Locale.US)
    }
}
