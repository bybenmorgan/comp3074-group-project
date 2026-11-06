package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private val store by lazy { HouseholdRepository(this) }
    private var expenseRequest = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireHousehold()) return
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_main)
        watchAccount()
        bindHouseholdChrome(store)
        findViewById<MaterialButton>(R.id.feature_chores).setOnClickListener {
            startActivity(Intent(this, ChoresActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.feature_groceries).setOnClickListener {
            startActivity(Intent(this, GroceriesActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.feature_pet_care).setOnClickListener {
            startActivity(Intent(this, PetActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.feature_expenses).setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
        }
        findViewById<View>(R.id.dashboard_expenses).setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.dashboard_expense_retry).setOnClickListener {
            if (requireHousehold()) refreshExpenses()
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (!requireHousehold()) return
        bindHouseholdChrome(store)
        bindDashboard()
        refreshExpenses()
    }

    override fun onPause() {
        expenseRequest++
        super.onPause()
    }

    private fun refreshExpenses() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val householdId = HouseholdSession.current()?.active?.id ?: return
        val version = HouseholdSession.generation
        val ticket = ++expenseRequest
        findViewById<TextView>(R.id.dashboard_expense_label).setText(R.string.expense_loading)
        findViewById<TextView>(R.id.dashboard_expense_balance).setText(R.string.expense_balance_unknown)
        findViewById<View>(R.id.dashboard_expense_retry).visibility = View.GONE
        FirestoreHouseholds(uid).load().addOnCompleteListener(this) { membership ->
            if (ticket != expenseRequest || !HouseholdSession.isCurrent(uid, version) ||
                HouseholdSession.current()?.active?.id != householdId) return@addOnCompleteListener
            if (!membership.isSuccessful) {
                showExpenseError()
                return@addOnCompleteListener
            }
            HouseholdSession.accept(uid, version, membership.result)
            if (!requireHousehold()) return@addOnCompleteListener
            bindHouseholdChrome(store)
            bindDashboard()
            val activeId = store.currentHousehold().id
            FirestoreExpenses(activeId, uid).load().addOnCompleteListener(this) expensesLoaded@ { expenses ->
                if (ticket != expenseRequest || !HouseholdSession.isCurrent(uid, version) ||
                    HouseholdSession.current()?.active?.id != activeId) return@expensesLoaded
                if (!expenses.isSuccessful) {
                    showExpenseError()
                    return@expensesLoaded
                }
                val balance = expenseDebts(uid, expenses.result).values.sum()
                findViewById<TextView>(R.id.dashboard_expense_label).setText(when {
                    balance > 0 -> R.string.expense_net_owe
                    balance < 0 -> R.string.expense_net_owed
                    else -> R.string.expense_net_zero
                })
                findViewById<TextView>(R.id.dashboard_expense_balance).text = expenseMoney(abs(balance))
            }
        }
    }

    private fun showExpenseError() {
        findViewById<TextView>(R.id.dashboard_expense_label).setText(R.string.expense_load_failed)
        findViewById<View>(R.id.dashboard_expense_retry).visibility = View.VISIBLE
    }

    private fun bindDashboard() {
        findViewById<TextView>(R.id.events_day).text = HouseholdRepository.todayHeader()
        findViewById<TextView>(R.id.events_date).text = HouseholdRepository.todayBadge()

        val groceries = store.groceries()
        val neededGroceries = groceries.filter { !it.checked }.map { it.name }
        findViewById<TextView>(R.id.notice_one).text = when {
            groceries.isEmpty() -> getString(R.string.dashboard_no_groceries)
            neededGroceries.isEmpty() -> getString(R.string.notice_all_caught_up)
            else -> getString(R.string.notice_need_item, neededGroceries[0])
        }
        findViewById<TextView>(R.id.notice_two).apply {
            visibility = if (neededGroceries.size > 1) View.VISIBLE else View.GONE
            text = neededGroceries.getOrNull(1)?.let { getString(R.string.notice_need_item, it) }
        }

        val household = store.currentHousehold()
        val openChores = store.chores().filter { !it.done }.map { it.title }
        val nextAppointment = if (household.hasPet) store.appointments().firstOrNull() else null
        findViewById<TextView>(R.id.event_one).text =
            openChores.getOrNull(0) ?: getString(R.string.event_no_chores)
        findViewById<TextView>(R.id.event_two).apply {
            visibility = if (openChores.size > 1) View.VISIBLE else View.GONE
            text = openChores.getOrNull(1)
        }
        findViewById<TextView>(R.id.event_three).apply {
            visibility = if (nextAppointment != null) View.VISIBLE else View.GONE
            text = nextAppointment?.let {
                listOf(it.title, it.date, it.time).filter { part -> part.isNotBlank() }.joinToString(", ")
            }
        }

        bindPetStatus(
            findViewById(R.id.pet_walked_status),
            store.petChecked(HouseholdRepository.PET_MORNING_WALK) ||
                store.petChecked(HouseholdRepository.PET_NIGHT_WALK)
        )
        bindPetStatus(
            findViewById(R.id.pet_fed_status),
            store.petChecked(HouseholdRepository.PET_BREAKFAST) ||
                store.petChecked(HouseholdRepository.PET_DINNER)
        )
    }

    private fun bindPetStatus(view: TextView, done: Boolean) {
        view.text = getString(if (done) R.string.pet_done else R.string.pet_not_yet)
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0,
            0,
            if (done) R.drawable.ic_check else 0,
            0
        )
    }
}
