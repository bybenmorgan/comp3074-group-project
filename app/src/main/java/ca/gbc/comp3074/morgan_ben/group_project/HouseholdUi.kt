package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.bindHouseholdChrome(store: HouseholdRepository) {
    bindHomeButton()
    findViewById<View>(R.id.open_settings)?.setOnClickListener {
        if (this !is SettingsActivity) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    val hasPet = store.currentHousehold().hasPet
    findViewById<View>(R.id.feature_pet_care)?.visibility =
        if (hasPet) View.VISIBLE else View.GONE
    findViewById<View>(R.id.pet_status_card)?.visibility =
        if (hasPet) View.VISIBLE else View.GONE
}

fun AppCompatActivity.bindHomeButton() {
    val goHome = View.OnClickListener { openDashboard() }
    findViewById<View>(R.id.open_home)?.let { title ->
        val enabled = this !is MainActivity
        title.isClickable = enabled
        title.isFocusable = enabled
        title.contentDescription = if (enabled) getString(R.string.open_home) else null
        title.setOnClickListener(if (enabled) goHome else null)
    }
    if (this is MainActivity) return
    findViewById<View>(R.id.feature_dashboard)?.let { button ->
        button.isClickable = true
        button.isFocusable = true
        button.setOnClickListener(goHome)
    }
}

internal fun AppCompatActivity.openDashboard() {
    if (this is MainActivity || !requireHousehold()) return
    startActivity(Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    })
}
