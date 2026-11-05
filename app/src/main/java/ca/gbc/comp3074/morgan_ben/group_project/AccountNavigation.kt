package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

internal fun AppCompatActivity.openLogin() {
    startActivity(Intent(this, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
    finish()
}

internal fun AppCompatActivity.requireSignedIn(): Boolean {
    if (FirebaseAuth.getInstance().currentUser != null) return true
    openLogin()
    return false
}
