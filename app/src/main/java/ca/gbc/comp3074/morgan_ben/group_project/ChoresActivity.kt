package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChoresActivity : AppCompatActivity() {
    private val store by lazy { HouseholdRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_chores)
        bindHouseholdChrome(store)
        findViewById<TextView>(R.id.chores_today_date).text = HouseholdRepository.todayLong()
        findViewById<MaterialButton>(R.id.feature_dashboard).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.feature_groceries).setOnClickListener {
            startActivity(Intent(this, GroceriesActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.feature_pet_care).setOnClickListener {
            startActivity(Intent(this, PetActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.feature_expenses).setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.add_chore).setOnClickListener { showAddDialog() }
        renderList()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chores_main)) { v, insets ->
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_chore, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.chore_title_input)
        val assigneeInput = dialogView.findViewById<EditText>(R.id.chore_assignee_input)
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.chores_add)
            .setView(dialogView)
            .setPositiveButton(R.string.pet_save_appointment) { _, _ ->
                val title = titleInput.text.toString().trim().ifEmpty {
                    getString(R.string.chore_default_title)
                }
                val assigneeRaw = assigneeInput.text.toString().trim().ifEmpty {
                    getString(R.string.expense_payer_ben)
                }
                val assignee = if (assigneeRaw.startsWith("Assigned", true)) {
                    assigneeRaw
                } else {
                    getString(R.string.chore_assigned_to, assigneeRaw)
                }
                store.saveChores(
                    store.chores() + ChoreItem(
                        title,
                        assignee,
                        getString(R.string.chore_due_today),
                        false
                    )
                )
                renderList()
            }
            .setNegativeButton(R.string.pet_cancel, null)
            .show()
    }

    private fun renderList() {
        val list = findViewById<LinearLayout>(R.id.chore_list)
        val empty = findViewById<TextView>(R.id.chore_empty)
        val progress = findViewById<ProgressBar>(R.id.chores_progress)
        val progressCount = findViewById<TextView>(R.id.chores_progress_count)
        list.removeAllViews()
        val items = store.chores()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        items.forEachIndexed { index, item ->
            val row = inflater.inflate(R.layout.item_chore, list, false)
            bindItem(row, item, index)
            list.addView(row)
        }
        val done = items.count { it.done }
        progress.max = items.size.coerceAtLeast(1)
        progress.progress = done
        progressCount.text = getString(R.string.chores_progress_count, done, items.size)
    }

    private fun bindItem(row: View, item: ChoreItem, index: Int) {
        val check = row.findViewById<ImageView>(R.id.chore_check)
        row.findViewById<TextView>(R.id.chore_title).text = item.title
        row.findViewById<TextView>(R.id.chore_assignee).text = item.assignee
        row.findViewById<TextView>(R.id.chore_due).text = item.due
        check.setImageResource(if (item.done) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox)
        row.setOnClickListener {
            val items = store.chores().toMutableList()
            if (index in items.indices) {
                val current = items[index]
                items[index] = current.copy(done = !current.done)
                store.saveChores(items)
                renderList()
            }
        }
    }
}
