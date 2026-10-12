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

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_login)

        val emailLayout = findViewById<TextInputLayout>(R.id.email_layout)
        val email = findViewById<TextInputEditText>(R.id.email)
        val passwordLayout = findViewById<TextInputLayout>(R.id.password_layout)
        val password = findViewById<TextInputEditText>(R.id.password)
        findViewById<MaterialButton>(R.id.login).setOnClickListener {
            val emailIsBlank = email.text.toString().isBlank()
            val passwordIsBlank = password.text.toString().isBlank()
            emailLayout.error = if (emailIsBlank) getString(R.string.email_required) else null
            passwordLayout.error = if (passwordIsBlank) getString(R.string.password_required) else null
            if (!emailIsBlank && !passwordIsBlank) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        findViewById<MaterialButton>(R.id.create_account).setOnClickListener {
            startActivity(Intent(this, CreateJoinHouseholdActivity::class.java))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left, systemBars.top, systemBars.right,
                maxOf(systemBars.bottom, keyboard.bottom)
            )
            insets
        }
    }
}
