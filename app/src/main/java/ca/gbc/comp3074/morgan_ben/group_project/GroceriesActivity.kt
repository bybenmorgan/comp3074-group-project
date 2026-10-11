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

class GroceriesActivity : AppCompatActivity() {
    private val store by lazy { HouseholdRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_groceries)
        findViewById<MaterialButton>(R.id.feature_dashboard).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.feature_chores).setOnClickListener {
            startActivity(Intent(this, ChoresActivity::class.java))
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
        findViewById<MaterialButton>(R.id.add_grocery).setOnClickListener { showAddItemDialog() }
        renderList()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.groceries_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun showAddItemDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_grocery, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.grocery_name_input)
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.groceries_add)
            .setView(dialogView)
            .setPositiveButton(R.string.pet_save_appointment) { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty {
                    getString(R.string.groceries_item_default)
                }
                store.saveGroceries(store.groceries() + GroceryItem(name, false))
                renderList()
            }
            .setNegativeButton(R.string.pet_cancel, null)
            .show()
    }

    private fun renderList() {
        val list = findViewById<LinearLayout>(R.id.grocery_list)
        val empty = findViewById<TextView>(R.id.grocery_empty)
        val neededCount = findViewById<TextView>(R.id.groceries_needed_count)
        val progress = findViewById<ProgressBar>(R.id.groceries_progress)
        val progressCount = findViewById<TextView>(R.id.groceries_progress_count)
        list.removeAllViews()
        val items = store.groceries()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        items.forEachIndexed { index, item ->
            val row = inflater.inflate(R.layout.item_grocery, list, false)
            bindItem(row, item, index)
            list.addView(row)
        }
        val checked = items.count { it.checked }
        val remaining = items.size - checked
        neededCount.text = getString(R.string.groceries_needed_count, remaining)
        progress.max = items.size.coerceAtLeast(1)
        progress.progress = checked
        progressCount.text = getString(R.string.groceries_progress_count, checked, items.size)
    }

    private fun bindItem(row: View, item: GroceryItem, index: Int) {
        val check = row.findViewById<ImageView>(R.id.grocery_check)
        val done = row.findViewById<TextView>(R.id.grocery_done)
        row.findViewById<TextView>(R.id.grocery_name).text = item.name
        check.setImageResource(if (item.checked) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox)
        done.visibility = if (item.checked) View.VISIBLE else View.GONE
        check.contentDescription = getString(
            if (item.checked) R.string.groceries_item_checked else R.string.groceries_item_unchecked,
            item.name
        )
        row.setOnClickListener {
            val items = store.groceries().toMutableList()
            if (index in items.indices) {
                items[index] = items[index].copy(checked = !items[index].checked)
                store.saveGroceries(items)
                renderList()
            }
        }
    }
}
