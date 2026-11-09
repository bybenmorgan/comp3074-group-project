package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.auth.FirebaseAuth

internal fun AppCompatActivity.openLogin() {
    findViewById<View>(android.R.id.content)?.visibility = View.INVISIBLE
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

internal fun AppCompatActivity.openHouseholdSetup() {
    findViewById<View>(android.R.id.content)?.visibility = View.INVISIBLE
    startActivity(Intent(this, CreateJoinHouseholdActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
    finish()
}

internal fun AppCompatActivity.requireHousehold(): Boolean {
    if (isFinishing || !requireSignedIn()) return false
    if (HouseholdSession.current()?.active != null) return true
    openHouseholdSetup()
    return false
}

internal fun AppCompatActivity.watchAccount() {
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid
    val listener = FirebaseAuth.AuthStateListener {
        if (!isFinishing && it.currentUser?.uid != uid) {
            HouseholdSession.clear()
            openLogin()
        }
    }
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { auth.addAuthStateListener(listener) }
        override fun onStop(owner: LifecycleOwner) { auth.removeAuthStateListener(listener) }
    })
}
