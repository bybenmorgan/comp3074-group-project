package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CreateJoinHouseholdActivity : AppCompatActivity() {
    private val request by viewModels<HouseholdViewModel>()
    private var resumed = false
    private var navigating = false
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
        setContentView(R.layout.activity_create_join_household)

        val householdNameLayout = findViewById<TextInputLayout>(R.id.household_name_layout)
        val householdName = findViewById<TextInputEditText>(R.id.household_name)
        val includePet = findViewById<SwitchCompat>(R.id.create_include_pet)
        val petNameLayout = findViewById<TextInputLayout>(R.id.create_pet_name_layout)
        val petName = findViewById<TextInputEditText>(R.id.create_pet_name)
        includePet.setOnCheckedChangeListener { _, checked ->
            petNameLayout.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) petNameLayout.error = null
        }
        findViewById<MaterialButton>(R.id.create_household).setOnClickListener {
            val name = householdName.text.toString().trim()
            val pet = petName.text.toString().trim()
            if (!validHouseholdName(name)) {
                householdNameLayout.error = getString(R.string.household_name_invalid)
            } else if (includePet.isChecked && pet.length > 80) {
                householdNameLayout.error = null
                petNameLayout.error = getString(R.string.household_pet_name_invalid)
            } else {
                householdNameLayout.error = null
                petNameLayout.error = null
                request.create(name, includePet.isChecked, if (includePet.isChecked) pet else "")
            }
        }

        val inviteCodeLayout = findViewById<TextInputLayout>(R.id.invite_code_layout)
        val inviteCode = findViewById<TextInputEditText>(R.id.invite_code)
        findViewById<MaterialButton>(R.id.join_household).setOnClickListener {
            val code = inviteCode.text.toString().trim()
            if (!validInviteCode(code)) {
                inviteCodeLayout.error = getString(R.string.household_code_invalid)
            } else {
                inviteCodeLayout.error = null
                request.join(code)
            }
        }

        findViewById<MaterialButton>(R.id.household_retry).setOnClickListener { request.load() }
        findViewById<MaterialButton>(R.id.household_logout).setOnClickListener {
            HouseholdSession.clear()
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            openLogin()
        }
        watchAccount()
        onBackPressedDispatcher.addCallback(this, pendingBack)
        request.changed.observe(this) { render() }
        if (!request.resolved && !request.busy && request.error == null) request.load()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.create_join_household_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, maxOf(systemBars.bottom, keyboard.bottom))
            insets
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        resumed = true
        if (!isFinishing && requireSignedIn()) render()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    private fun render() {
        if (isFinishing) return
        pendingBack.isEnabled = request.busy
        val enabled = request.resolved && !request.busy && !request.refreshRequired
        listOf(
            R.id.create_household, R.id.join_household, R.id.household_name, R.id.invite_code,
            R.id.create_include_pet, R.id.create_pet_name
        ).forEach { findViewById<View>(it).isEnabled = enabled }
        findViewById<View>(R.id.household_progress).visibility = if (request.busy) View.VISIBLE else View.GONE
        findViewById<View>(R.id.household_retry).visibility =
            if (!request.busy && request.error != null) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.household_logout).isEnabled = !request.busy
        findViewById<TextView>(R.id.household_status).text = getString(
            request.error ?: if (request.busy) R.string.household_loading else R.string.household_empty
        )
        if (resumed && request.resolved && !request.busy && !request.refreshRequired &&
            request.error == null && HouseholdSession.current()?.active != null) openDashboard()
    }

    private fun openDashboard() {
        if (navigating || !requireSignedIn()) return
        navigating = true
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
