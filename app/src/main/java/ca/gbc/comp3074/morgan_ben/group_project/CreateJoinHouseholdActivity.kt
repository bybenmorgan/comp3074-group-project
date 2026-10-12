package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CreateJoinHouseholdActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_create_join_household)

        val householdNameLayout = findViewById<TextInputLayout>(R.id.household_name_layout)
        val householdName = findViewById<TextInputEditText>(R.id.household_name)
        findViewById<MaterialButton>(R.id.create_household).setOnClickListener {
            if (householdName.text.toString().isBlank()) {
                householdNameLayout.error = getString(R.string.household_name_required)
            } else {
                householdNameLayout.error = null
                openDashboard()
            }
        }

        val inviteCodeLayout = findViewById<TextInputLayout>(R.id.invite_code_layout)
        val inviteCode = findViewById<TextInputEditText>(R.id.invite_code)
        findViewById<MaterialButton>(R.id.join_household).setOnClickListener {
            if (inviteCode.text.toString().isBlank()) {
                inviteCodeLayout.error = getString(R.string.invite_code_required)
            } else {
                inviteCodeLayout.error = null
                openDashboard()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.create_join_household_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, maxOf(systemBars.bottom, keyboard.bottom))
            insets
        }
    }

    private fun openDashboard() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
