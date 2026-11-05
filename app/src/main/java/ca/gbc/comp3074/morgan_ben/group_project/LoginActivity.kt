package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

class LoginActivity : AppCompatActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var emailLayout: TextInputLayout
    private lateinit var email: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var password: TextInputEditText
    private lateinit var confirmationLayout: TextInputLayout
    private lateinit var confirmation: TextInputEditText
    private lateinit var submit: MaterialButton
    private lateinit var switchMode: MaterialButton
    private lateinit var back: OnBackPressedCallback
    private var registering = false
    private val request by viewModels<AccountRequestState>()
    private var errorMessage: Int? = null
    private var navigating = false
    private var resumed = false

    class AccountRequestState : ViewModel() {
        var task: Task<AuthResult>? = null
        var registering = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.dashboard_cream)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor),
            navigationBarStyle = SystemBarStyle.light(systemBarColor, systemBarColor)
        )
        setContentView(R.layout.activity_login)

        registering = savedInstanceState?.getBoolean("registering") ?: false
        errorMessage = savedInstanceState?.getInt("auth_error")?.takeIf { it != 0 }
        emailLayout = findViewById(R.id.email_layout)
        email = findViewById(R.id.email)
        passwordLayout = findViewById(R.id.password_layout)
        password = findViewById(R.id.password)
        confirmationLayout = findViewById(R.id.confirm_password_layout)
        confirmation = findViewById(R.id.confirm_password)
        submit = findViewById(R.id.login)
        switchMode = findViewById(R.id.create_account)
        submit.setOnClickListener { submitAccount() }
        switchMode.setOnClickListener { changeMode() }
        password.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                submitAccount()
                true
            } else false
        }
        confirmation.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                submitAccount()
                true
            } else false
        }
        back = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (request.task == null) changeMode()
            }
        }
        onBackPressedDispatcher.addCallback(this, back)
        renderForm()

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

    override fun onStart() {
        super.onStart()
        request.task?.let(::observeRequest)
    }

    override fun onPostResume() {
        super.onPostResume()
        resumed = true
        renderForm()
        handleResultOrSession()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("registering", registering)
        outState.putInt("auth_error", errorMessage ?: 0)
        super.onSaveInstanceState(outState)
    }

    private fun changeMode() {
        if (request.task != null || navigating) return
        registering = !registering
        password.text?.clear()
        confirmation.text?.clear()
        emailLayout.error = null
        passwordLayout.error = null
        confirmationLayout.error = null
        errorMessage = null
        renderForm()
    }

    private fun submitAccount() {
        if (request.task != null || navigating) return
        val emailInput = email.text.toString().trim()
        val passwordInput = password.text.toString()
        val errors = validateAccountInput(
            emailInput, passwordInput, if (registering) confirmation.text.toString() else null
        )
        emailLayout.error = errors.email?.let(::getString)
        passwordLayout.error = errors.password?.let(::getString)
        confirmationLayout.error = errors.confirmation?.let(::getString)
        errorMessage = null
        if (!errors.isValid) {
            renderForm()
            return
        }

        val task = try {
            if (registering) auth.createUserWithEmailAndPassword(emailInput, passwordInput)
            else auth.signInWithEmailAndPassword(emailInput, passwordInput)
        } catch (exception: Exception) {
            errorMessage = authenticationError(exception)
            renderForm()
            return
        }
        request.task = task
        request.registering = registering
        renderForm()
        observeRequest(task)
    }

    private fun observeRequest(task: Task<AuthResult>) {
        task.addOnCompleteListener(this) {
            if (request.task === task) handleResultOrSession()
        }
    }

    private fun handleResultOrSession() {
        if (!resumed || isFinishing || isDestroyed || navigating) return
        val task = request.task
        if (task != null) {
            if (!task.isComplete) return
            request.task = null
            if (task.isSuccessful && auth.currentUser != null) {
                openAccountScreen(request.registering)
            } else {
                errorMessage = authenticationError(task.exception)
                renderForm()
            }
        } else if (auth.currentUser != null) {
            openAccountScreen(false)
        }
    }

    private fun openAccountScreen(newAccount: Boolean) {
        navigating = true
        password.text?.clear()
        confirmation.text?.clear()
        val destination = if (newAccount) CreateJoinHouseholdActivity::class.java
        else MainActivity::class.java
        startActivity(Intent(this, destination).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun renderForm() {
        val loading = request.task != null
        findViewById<TextView>(R.id.account_title).setText(
            if (registering) R.string.create_account else R.string.login
        )
        confirmationLayout.visibility = if (registering) View.VISIBLE else View.GONE
        password.imeOptions = if (registering) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE
        emailLayout.isEnabled = !loading
        passwordLayout.isEnabled = !loading
        confirmationLayout.isEnabled = !loading
        submit.isEnabled = !loading
        switchMode.isEnabled = !loading
        submit.setText(if (registering) R.string.create_account else R.string.login)
        switchMode.setText(if (registering) R.string.back_to_login else R.string.new_here_create_account)
        findViewById<View>(R.id.auth_progress).visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.auth_status).apply {
            visibility = if (loading || errorMessage != null) View.VISIBLE else View.GONE
            text = when {
                loading -> getString(if (registering) R.string.creating_account else R.string.logging_in)
                else -> errorMessage?.let(::getString).orEmpty()
            }
        }
        back.isEnabled = loading || registering
    }

    private fun authenticationError(exception: Exception?): Int = when (exception) {
        is FirebaseNetworkException -> R.string.auth_network_error
        is FirebaseTooManyRequestsException -> R.string.auth_too_many_requests
        is FirebaseAuthException -> when (exception.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.auth_email_in_use
            "ERROR_INVALID_EMAIL" -> R.string.email_invalid
            "ERROR_WEAK_PASSWORD" -> R.string.auth_weak_password
            "ERROR_USER_DISABLED" -> R.string.auth_user_disabled
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS",
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> R.string.auth_invalid_credentials
            "ERROR_OPERATION_NOT_ALLOWED" -> R.string.auth_not_enabled
            else -> if (registering) R.string.registration_failed else R.string.login_failed
        }
        else -> if (registering) R.string.registration_failed else R.string.login_failed
    }
}
