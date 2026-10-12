package ca.gbc.comp3074.morgan_ben.group_project

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.bindHouseholdChrome(store: HouseholdRepository) {
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
