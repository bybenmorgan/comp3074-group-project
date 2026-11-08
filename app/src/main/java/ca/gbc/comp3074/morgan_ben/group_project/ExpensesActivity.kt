package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.app.DatePickerDialog
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ExpensesActivity : AppCompatActivity() {
    private val store by lazy { HouseholdRepository(this) }
    private val model by viewModels<ExpensesViewModel>()
    private var addDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireHousehold()) return
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_expenses)
        watchAccount()
        bindHouseholdChrome(store)
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
        findViewById<MaterialButton>(R.id.expense_retry).setOnClickListener { model.retry() }
        findViewById<MaterialButton>(R.id.expense_discard).setOnClickListener { model.discardFailedSave() }
        model.changed.observe(this) {
            if (!requireHousehold()) return@observe
            bindHouseholdChrome(store)
            renderList()
            if (!model.dialogOpen || model.loading) {
                addDialog?.dismiss()
                addDialog = null
            }
            if (model.dialogOpen && addDialog == null && !model.loading) showAddDialog()
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.expenses_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (!requireHousehold()) return
        bindHouseholdChrome(store)
        model.refresh()
    }

    private fun showAddDialog() {
        if (!model.canAdd || addDialog != null) return
        model.dialogOpen = true
        val members = HouseholdSession.current()?.active?.members.orEmpty()
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.expense_title_input)
        val amountInput = dialogView.findViewById<EditText>(R.id.expense_amount_input)
        val dueInput = dialogView.findViewById<TextView>(R.id.expense_due_input)
        val payerInput = dialogView.findViewById<Spinner>(R.id.expense_payer_input)
        titleInput.setText(model.description)
        amountInput.setText(model.amount)
        fun showDue() {
            dueInput.text = getString(R.string.expense_due, mediumDate(model.dueMillis))
        }
        showDue()
        dueInput.setOnClickListener {
            val current = Calendar.getInstance().apply { timeInMillis = model.dueMillis }
            DatePickerDialog(this, { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day, 0, 0, 0)
                picked.set(Calendar.MILLISECOND, 0)
                model.dueMillis = picked.timeInMillis
                showDue()
            }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show()
        }
        titleInput.doAfterTextChanged { model.description = it.toString() }
        amountInput.doAfterTextChanged { model.amount = it.toString() }
        payerInput.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.expense_select_payer)) + members.map { it.label })
        payerInput.setSelection(members.indexOfFirst { it.uid == model.payerUid } + 1)
        payerInput.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                model.payerUid = members.getOrNull(position - 1)?.uid
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { model.payerUid = null }
        }
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.expenses_add)
            .setView(dialogView)
            .setPositiveButton(R.string.pet_save_appointment, null)
            .setNegativeButton(R.string.pet_cancel) { _, _ -> model.dialogOpen = false }
            .create()
        addDialog = dialog
        dialog.setOnCancelListener { model.dialogOpen = false }
        dialog.setOnDismissListener { addDialog = null }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val validTitle = model.description.isNotBlank() && model.description.trim().length <= 120
            val validAmount = parseExpenseCents(model.amount) != null
            titleInput.error = if (validTitle) null else getString(R.string.expense_description_invalid)
            amountInput.error = if (validAmount) null else getString(R.string.expense_amount_invalid)
            val validPayer = members.any { it.uid == model.payerUid }
            dialogView.findViewById<TextView>(R.id.expense_payer_error).visibility =
                if (validPayer) View.GONE else View.VISIBLE
            if (validTitle && validAmount && validPayer) model.save()
        }
    }

    override fun onDestroy() {
        addDialog?.dismiss()
        super.onDestroy()
    }

    private fun renderList() {
        val list = findViewById<LinearLayout>(R.id.expense_list)
        val empty = findViewById<TextView>(R.id.expense_empty)
        list.removeAllViews()
        val ready = model.ready && model.matchesHousehold
        val items = if (model.matchesHousehold) model.items else emptyList()
        empty.visibility = if (ready && items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.add_expense).isEnabled = model.canAdd
        findViewById<TextView>(R.id.expense_status).apply {
            visibility = if (model.loading || model.saving || model.error != null) View.VISIBLE else View.GONE
            text = getString(model.error ?: if (model.saving) R.string.expense_saving else R.string.expense_loading)
        }
        findViewById<MaterialButton>(R.id.expense_retry).visibility =
            if (model.error != null && !model.loading && !model.saving) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.expense_discard).visibility =
            if (model.failedSave) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        items.forEach { item ->
            val row = inflater.inflate(R.layout.item_expense, list, false)
            bindItem(row, item)
            list.addView(row)
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val debts = expenseDebts(uid, items)
        val balance = debts.values.sum()
        val label = findViewById<TextView>(R.id.expenses_owe_label)
        val amount = findViewById<TextView>(R.id.expenses_balance)
        label.setText(
            when {
                balance == 0L -> R.string.expense_net_zero
                balance < 0 -> R.string.expense_net_owed
                else -> R.string.expense_net_owe
            }
        )
        amount.text = if (ready) expenseMoney(abs(balance)) else getString(R.string.expense_balance_unknown)
        val labels = items.asReversed().associate { it.payerUid to it.payerLabel } +
            HouseholdSession.current()?.active?.members.orEmpty().associate { it.uid to it.label }
        findViewById<TextView>(R.id.expense_breakdown).text = debts.entries.joinToString("\n") { (person, cents) ->
            getString(if (cents > 0) R.string.expense_owe_person else R.string.expense_person_owes,
                labels[person] ?: person, expenseMoney(abs(cents)))
        }
    }

    private fun bindItem(row: View, item: SharedExpense) {
        row.findViewById<ImageView>(R.id.expense_icon).setImageResource(iconFor(item.description))
        row.findViewById<TextView>(R.id.expense_name).text = item.description
        row.findViewById<TextView>(R.id.expense_payer).text =
            getString(R.string.expense_paid_by, item.payerLabel)
        row.findViewById<TextView>(R.id.expense_amount).text =
            expenseMoney(item.amountCents)
        row.findViewById<TextView>(R.id.expense_date).text =
            getString(R.string.expense_due, mediumDate(item.dueMillis))
    }

    private fun mediumDate(millis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

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
