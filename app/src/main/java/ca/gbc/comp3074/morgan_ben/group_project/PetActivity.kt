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

class PetActivity : AppCompatActivity() {
    private val store by lazy { HouseholdRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_pet)
        if (!store.currentHousehold().hasPet) {
            finish()
            return
        }
        bindHouseholdChrome(store)
        findViewById<TextView>(R.id.pet_name_label).text = store.currentHousehold().petName
        findViewById<MaterialButton>(R.id.feature_dashboard).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.feature_chores).setOnClickListener {
            startActivity(Intent(this, ChoresActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.feature_groceries).setOnClickListener {
            startActivity(Intent(this, GroceriesActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.feature_expenses).setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
            finish()
        }
        findViewById<MaterialButton>(R.id.add_appointment).setOnClickListener {
            showAddAppointmentDialog()
        }
        bindTask(
            R.id.task_morning_walk,
            HouseholdRepository.PET_MORNING_WALK,
            getString(R.string.pet_morning_walk),
            getString(R.string.pet_morning_walk_detail)
        )
        bindTask(
            R.id.task_night_walk,
            HouseholdRepository.PET_NIGHT_WALK,
            getString(R.string.pet_night_walk),
            getString(R.string.pet_night_walk_detail)
        )
        bindTask(
            R.id.task_breakfast,
            HouseholdRepository.PET_BREAKFAST,
            getString(R.string.pet_breakfast),
            getString(R.string.pet_breakfast_detail)
        )
        bindTask(
            R.id.task_dinner,
            HouseholdRepository.PET_DINNER,
            getString(R.string.pet_dinner),
            getString(R.string.pet_dinner_detail)
        )
        renderAppointments()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pet_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun bindTask(rowId: Int, key: String, title: String, subtitle: String) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.pet_task_title).text = title
        row.findViewById<TextView>(R.id.pet_task_subtitle).text = subtitle
        val check = row.findViewById<ImageView>(R.id.pet_task_check)
        val done = row.findViewById<TextView>(R.id.pet_task_done)
        updateCheck(check, done, title, store.petChecked(key))
        row.setOnClickListener {
            val checked = !store.petChecked(key)
            store.setPetChecked(key, checked)
            updateCheck(check, done, title, checked)
        }
    }

    private fun updateCheck(check: ImageView, done: TextView, title: String, checked: Boolean) {
        check.setImageResource(if (checked) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox)
        done.visibility = if (checked) View.VISIBLE else View.GONE
        check.contentDescription = getString(
            if (checked) R.string.pet_task_checked else R.string.pet_task_unchecked,
            title
        )
    }

    private fun showAddAppointmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_appointment, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.appointment_title_input)
        val dateInput = dialogView.findViewById<EditText>(R.id.appointment_date_input)
        val timeInput = dialogView.findViewById<EditText>(R.id.appointment_time_input)
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.pet_add_appointment)
            .setView(dialogView)
            .setPositiveButton(R.string.pet_save_appointment) { _, _ ->
                val title = titleInput.text.toString().trim().ifEmpty {
                    getString(R.string.pet_appointment_default_title)
                }
                val date = dateInput.text.toString().trim().ifEmpty {
                    HouseholdRepository.todayShort()
                }
                val time = timeInput.text.toString().trim().ifEmpty {
                    getString(R.string.pet_appointment_default_time)
                }
                store.saveAppointments(store.appointments() + AppointmentItem(title, date, time))
                renderAppointments()
            }
            .setNegativeButton(R.string.pet_cancel, null)
            .show()
    }

    private fun renderAppointments() {
        val list = findViewById<LinearLayout>(R.id.appointment_list)
        val empty = findViewById<TextView>(R.id.appointment_empty)
        list.removeAllViews()
        val appointments = store.appointments()
        empty.visibility = if (appointments.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        appointments.forEach { item ->
            val row = inflater.inflate(R.layout.item_pet_appointment, list, false)
            row.findViewById<TextView>(R.id.appointment_title).text = item.title
            row.findViewById<TextView>(R.id.appointment_time).text = item.time
            row.findViewById<TextView>(R.id.appointment_date).text = item.date
            list.addView(row)
        }
    }
}
