package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private val store by lazy { HouseholdRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_main)
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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        bindDashboard()
    }

    private fun bindDashboard() {
        findViewById<TextView>(R.id.events_day).text = HouseholdRepository.todayHeader()
        findViewById<TextView>(R.id.events_date).text = HouseholdRepository.todayBadge()

        val neededGroceries = store.groceries().filter { !it.checked }.map { it.name }
        findViewById<TextView>(R.id.notice_one).text = if (neededGroceries.isEmpty()) {
            getString(R.string.notice_all_caught_up)
        } else {
            getString(R.string.notice_need_item, neededGroceries[0])
        }
        findViewById<TextView>(R.id.notice_two).text = if (neededGroceries.size > 1) {
            getString(R.string.notice_need_item, neededGroceries[1])
        } else {
            getString(R.string.notice_maintenance)
        }

        val openChores = store.chores().filter { !it.done }.map { it.title }
        val nextAppointment = store.appointments().firstOrNull()
        findViewById<TextView>(R.id.event_one).text =
            openChores.getOrNull(0) ?: getString(R.string.event_no_chores)
        findViewById<TextView>(R.id.event_two).text =
            openChores.getOrNull(1) ?: getString(R.string.event_bill)
        findViewById<TextView>(R.id.event_three).text = if (nextAppointment != null) {
            "${nextAppointment.title}, ${nextAppointment.time}"
        } else {
            getString(R.string.event_appointment)
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
