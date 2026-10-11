package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import kotlin.math.abs

class ExpensesActivity : AppCompatActivity() {
    private val store by lazy { HouseholdRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_expenses)
        bindHouseholdChrome(store)
        findViewById<MaterialButton>(R.id.feature_dashboard).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.feature_chores).setOnClickListener {
            startActivity(Intent(this, ChoresActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.feature_groceries).setOnClickListener {
            startActivity(Intent(this, GroceriesActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.feature_pet_care).setOnClickListener {
            startActivity(Intent(this, PetActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.add_expense).setOnClickListener { showAddDialog() }
        renderList()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.expenses_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        bindHouseholdChrome(store)
        renderList()
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.expense_title_input)
        val amountInput = dialogView.findViewById<EditText>(R.id.expense_amount_input)
        val payerInput = dialogView.findViewById<EditText>(R.id.expense_payer_input)
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.expenses_add)
            .setView(dialogView)
            .setPositiveButton(R.string.pet_save_appointment) { _, _ ->
                val name = titleInput.text.toString().trim().ifEmpty {
                    getString(R.string.expense_default_title)
                }
                val amount = amountInput.text.toString().replace("$", "").toDoubleOrNull() ?: 0.0
                val payer = payerInput.text.toString().trim().ifEmpty {
                    getString(R.string.expense_payer_ben)
                }
                store.saveExpenses(
                    listOf(
                        ExpenseItem(name, amount, payer, HouseholdRepository.todayShort())
                    ) + store.expenses()
                )
                renderList()
            }
            .setNegativeButton(R.string.pet_cancel, null)
            .show()
    }

    private fun renderList() {
        val list = findViewById<LinearLayout>(R.id.expense_list)
        val empty = findViewById<TextView>(R.id.expense_empty)
        list.removeAllViews()
        val items = store.expenses()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        items.forEach { item ->
            val row = inflater.inflate(R.layout.item_expense, list, false)
            bindItem(row, item)
            list.addView(row)
        }
        val balance = store.youOwe()
        val label = findViewById<TextView>(R.id.expenses_owe_label)
        val amount = findViewById<TextView>(R.id.expenses_balance)
        label.setText(
            when {
                abs(balance) < 0.005 -> R.string.expenses_settled
                balance < 0 -> R.string.expenses_you_are_owed
                else -> R.string.expenses_you_owe
            }
        )
        amount.text = String.format(Locale.US, "$%.2f", abs(balance))
    }

    private fun bindItem(row: View, item: ExpenseItem) {
        row.findViewById<ImageView>(R.id.expense_icon).setImageResource(iconFor(item.name))
        row.findViewById<TextView>(R.id.expense_name).text = item.name
        row.findViewById<TextView>(R.id.expense_payer).text =
            getString(R.string.expense_paid_by, item.payer)
        row.findViewById<TextView>(R.id.expense_amount).text =
            String.format(Locale.US, "$%.2f", item.amount)
        row.findViewById<TextView>(R.id.expense_date).text = item.date
    }

    private fun iconFor(name: String): Int {
        val lower = name.lowercase(Locale.US)
        return when {
            "internet" in lower || "wifi" in lower -> R.drawable.ic_internet
            "groc" in lower || "food" in lower -> R.drawable.ic_groceries
            "rent" in lower -> R.drawable.ic_rent
            "pet" in lower || "dog" in lower -> R.drawable.ic_pet
            else -> R.drawable.ic_expenses
        }
    }
}
