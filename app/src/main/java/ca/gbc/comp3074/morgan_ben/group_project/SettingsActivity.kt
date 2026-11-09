package ca.gbc.comp3074.morgan_ben.group_project

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {
    private val request by viewModels<HouseholdViewModel>()
    private var resumed = false
    private val pendingBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSignedIn()) return
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_settings)
        watchAccount()
        bindHomeButton()
        onBackPressedDispatcher.addCallback(this, pendingBack)
        findViewById<MaterialButton>(R.id.add_household).setOnClickListener { showAddDialog() }
        findViewById<MaterialButton>(R.id.settings_join_household).setOnClickListener { showJoinDialog() }
        findViewById<MaterialButton>(R.id.household_retry).setOnClickListener { request.load() }
        findViewById<MaterialButton>(R.id.copy_invite_code).setOnClickListener {
            val code = HouseholdSession.current()?.active?.id ?: return@setOnClickListener
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(getString(R.string.invite_code), code))
            Toast.makeText(this, R.string.household_code_copied, Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.logout).setOnClickListener {
            HouseholdSession.clear()
            FirebaseAuth.getInstance().signOut()
            openLogin()
        }
        request.changed.observe(this) { renderList() }
        if (!request.resolved && !request.busy && request.error == null) request.load()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        resumed = true
        if (!isFinishing && requireSignedIn()) renderList()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_household, null)
        val name = view.findViewById<EditText>(R.id.household_name_input)
        val petSwitch = view.findViewById<SwitchCompat>(R.id.household_include_pet)
        val petName = view.findViewById<EditText>(R.id.household_pet_name_input)
        petSwitch.setOnCheckedChangeListener { _, checked ->
            petName.visibility = if (checked) View.VISIBLE else View.GONE
        }
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.household_add).setView(view)
            .setPositiveButton(R.string.create_household, null)
            .setNegativeButton(R.string.pet_cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val input = name.text.toString().trim()
                if (!validHouseholdName(input)) name.error = getString(R.string.household_name_invalid)
                else if (petName.text.length > 80) petName.error = getString(R.string.household_pet_name_invalid)
                else {
                    request.create(input, petSwitch.isChecked, petName.text.toString().trim())
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showJoinDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.invite_code)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            maxLines = 1
        }
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.join_household).setView(input)
            .setPositiveButton(R.string.join_household, null)
            .setNegativeButton(R.string.pet_cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = input.text.toString().trim()
                if (!validInviteCode(code)) input.error = getString(R.string.household_code_invalid)
                else { request.join(code); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun renderList() {
        if (isFinishing) return
        pendingBack.isEnabled = request.busy
        val membership = HouseholdSession.current()
        val enabled = request.resolved && !request.busy && !request.refreshRequired
        listOf(R.id.add_household, R.id.settings_join_household, R.id.copy_invite_code)
            .forEach { findViewById<View>(it).isEnabled = enabled }
        findViewById<View>(R.id.logout).isEnabled = !request.busy
        findViewById<View>(R.id.household_progress).visibility = if (request.busy) View.VISIBLE else View.GONE
        findViewById<View>(R.id.household_retry).visibility =
            if (!request.busy) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.household_status).apply {
            text = request.error?.let(::getString) ?: if (request.busy) getString(R.string.household_loading) else ""
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.current_household_name).text = membership?.active?.name.orEmpty()
        findViewById<TextView>(R.id.household_invite_code).text = membership?.active?.id.orEmpty()
        findViewById<TextView>(R.id.household_members).text =
            membership?.active?.members.orEmpty().joinToString("\n") { it.label }
        val list = findViewById<LinearLayout>(R.id.household_list)
        list.removeAllViews()
        membership?.households.orEmpty().forEach { household ->
            val row = layoutInflater.inflate(R.layout.item_household, list, false)
            row.findViewById<TextView>(R.id.household_name).text = household.name
            row.findViewById<View>(R.id.household_current).visibility =
                if (household.id == membership?.activeId) View.VISIBLE else View.GONE
            row.findViewById<TextView>(R.id.household_pet_name).text = if (household.hasPet) {
                getString(R.string.household_pet_is, household.petName.ifBlank { getString(R.string.household_pet_name_hint) })
            } else getString(R.string.household_no_pet)
            row.findViewById<SwitchCompat>(R.id.household_pet_switch).apply {
                isChecked = household.hasPet
                isEnabled = enabled
                setOnCheckedChangeListener { _, checked ->
                    if (checked) showPetDialog(household, true)
                    else request.pet(household, false, household.petName)
                }
            }
            row.isClickable = false
            row.findViewById<MaterialButton>(R.id.household_edit_pet).apply {
                isEnabled = enabled
                setOnClickListener { if (enabled) showPetDialog(household) }
            }
            row.findViewById<MaterialButton>(R.id.household_switch).apply {
                val current = household.id == membership?.activeId
                visibility = if (current) View.GONE else View.VISIBLE
                isEnabled = enabled
                setOnClickListener { if (enabled) request.select(household.id) }
            }
            row.findViewById<MaterialButton>(R.id.household_remove).apply {
                setText(R.string.household_leave)
                isEnabled = enabled
                setOnClickListener { confirmLeave(household) }
            }
            list.addView(row)
        }
        if (resumed && request.resolved && !request.busy && !request.refreshRequired &&
            request.error == null && membership?.active == null) openHouseholdSetup()
    }

    private fun showPetDialog(household: Household, enable: Boolean = household.hasPet) {
        val view = layoutInflater.inflate(R.layout.dialog_add_household, null)
        view.findViewById<EditText>(R.id.household_name_input).visibility = View.GONE
        val petSwitch = view.findViewById<SwitchCompat>(R.id.household_include_pet)
        val petName = view.findViewById<EditText>(R.id.household_pet_name_input)
        petSwitch.isChecked = enable
        petName.setText(household.petName)
        petName.visibility = if (enable) View.VISIBLE else View.GONE
        petSwitch.setOnCheckedChangeListener { _, checked ->
            petName.visibility = if (checked) View.VISIBLE else View.GONE
        }
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.household_pet_settings).setView(view)
            .setPositiveButton(R.string.pet_save_appointment, null)
            .setNegativeButton(R.string.pet_cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = petName.text.toString().trim()
                if (name.length > 80) petName.error = getString(R.string.household_pet_name_invalid)
                else {
                    request.pet(household, petSwitch.isChecked, name)
                    dialog.dismiss()
                }
            }
        }
        dialog.setOnDismissListener { renderList() }
        dialog.show()
    }

    private fun confirmLeave(household: Household) {
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Household_Dialog)
            .setTitle(R.string.household_leave)
            .setMessage(getString(R.string.household_leave_confirm, household.name))
            .setPositiveButton(R.string.household_leave) { _, _ -> request.leave(household.id) }
            .setNegativeButton(R.string.pet_cancel, null).show()
    }
}
