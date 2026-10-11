package ca.gbc.comp3074.morgan_ben.group_project

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {
    private val store by lazy { HouseholdRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_settings)
        bindHouseholdChrome(store)
        findViewById<MaterialButton>(R.id.add_household).setOnClickListener { showAddDialog() }
        renderList()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_household, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.household_name_input)
        val petSwitch = dialogView.findViewById<SwitchCompat>(R.id.household_include_pet)
        val petNameInput = dialogView.findViewById<EditText>(R.id.household_pet_name_input)
        petSwitch.setOnCheckedChangeListener { _, checked ->
            petNameInput.visibility = if (checked) View.VISIBLE else View.GONE
        }
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.household_add)
            .setView(dialogView)
            .setPositiveButton(R.string.pet_save_appointment) { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty {
                    getString(R.string.household_default_name)
                }
                val petName = petNameInput.text.toString().trim().ifEmpty {
                    getString(R.string.pet_name)
                }
                store.addHousehold(name, petSwitch.isChecked, petName)
                renderList()
            }
            .setNegativeButton(R.string.pet_cancel, null)
            .show()
    }

    private fun renderList() {
        val current = store.currentHousehold()
        findViewById<TextView>(R.id.current_household_name).text = current.name
        val list = findViewById<LinearLayout>(R.id.household_list)
        list.removeAllViews()
        val inflater = LayoutInflater.from(this)
        store.households().forEach { household ->
            val row = inflater.inflate(R.layout.item_household, list, false)
            bindHousehold(row, household, household.id == current.id)
            list.addView(row)
        }
    }

    private fun bindHousehold(row: View, household: Household, selected: Boolean) {
        row.findViewById<TextView>(R.id.household_name).text = household.name
        row.findViewById<View>(R.id.household_current).visibility =
            if (selected) View.VISIBLE else View.GONE
        val petSwitch = row.findViewById<SwitchCompat>(R.id.household_pet_switch)
        val petName = row.findViewById<TextView>(R.id.household_pet_name)
        petSwitch.setOnCheckedChangeListener(null)
        petSwitch.isChecked = household.hasPet
        petName.text = if (household.hasPet) {
            getString(R.string.household_pet_is, household.petName)
        } else {
            getString(R.string.household_no_pet)
        }
        petSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                promptPetName(household)
            } else {
                store.updateHousehold(household.copy(hasPet = false))
                renderList()
            }
        }
        row.setOnClickListener {
            store.setActiveHousehold(household.id)
            renderList()
        }
        row.findViewById<MaterialButton>(R.id.household_remove).setOnClickListener {
            confirmRemove(household)
        }
    }

    private fun promptPetName(household: Household) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_household, null)
        dialogView.findViewById<EditText>(R.id.household_name_input).visibility = View.GONE
        dialogView.findViewById<SwitchCompat>(R.id.household_include_pet).visibility = View.GONE
        val petNameInput = dialogView.findViewById<EditText>(R.id.household_pet_name_input)
        petNameInput.setText(household.petName)
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.household_pet_name_hint)
            .setView(dialogView)
            .setPositiveButton(R.string.pet_save_appointment) { _, _ ->
                val petName = petNameInput.text.toString().trim().ifEmpty {
                    getString(R.string.pet_name)
                }
                store.updateHousehold(household.copy(hasPet = true, petName = petName))
                renderList()
            }
            .setNegativeButton(R.string.pet_cancel) { _, _ -> renderList() }
            .show()
    }

    private fun confirmRemove(household: Household) {
        if (store.households().size <= 1) {
            MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
                .setTitle(R.string.household_remove)
                .setMessage(R.string.household_keep_one)
                .setPositiveButton(R.string.pet_save_appointment, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.household_remove)
            .setMessage(getString(R.string.household_remove_confirm, household.name))
            .setPositiveButton(R.string.household_remove) { _, _ ->
                store.removeHousehold(household.id)
                renderList()
            }
            .setNegativeButton(R.string.pet_cancel, null)
            .show()
    }
}
