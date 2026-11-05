package ca.gbc.comp3074.morgan_ben.group_project

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException

class HouseholdViewModel : ViewModel() {
    private val uid = FirebaseAuth.getInstance().currentUser!!.uid
    private val version: Int
    private val store = FirestoreHouseholds(uid)
    val changed = MutableLiveData(0)
    var busy = false
        private set
    var resolved = false
        private set
    var refreshRequired = false
        private set
    var error: Int? = null
        private set

    init {
        HouseholdSession.current()
        version = HouseholdSession.generation
    }

    fun load() {
        if (busy || !HouseholdSession.isCurrent(uid, version)) return
        busy = true
        error = null
        notifyUi()
        store.load().addOnCompleteListener { task ->
            if (!HouseholdSession.isCurrent(uid, version)) return@addOnCompleteListener
            busy = false
            if (task.isSuccessful) {
                HouseholdSession.accept(uid, version, task.result)
                resolved = true
                refreshRequired = false
            } else {
                error = if (refreshRequired) R.string.household_saved_refresh else requestError(task.exception)
            }
            notifyUi()
        }
    }

    fun create(name: String, hasPet: Boolean = false, petName: String = "") {
        if (!validHouseholdName(name)) return fail(R.string.household_name_invalid)
        if (petName.length > 80) return fail(R.string.household_pet_name_invalid)
        write { store.create(name, hasPet, petName) }
    }

    fun join(code: String) {
        if (!validInviteCode(code)) return fail(R.string.household_code_invalid)
        write(joining = true) { store.join(code) }
    }

    fun select(id: String) = write { store.select(id) }
    fun leave(id: String) = write { store.leave(id) }
    fun pet(household: Household, hasPet: Boolean, name: String) {
        if (name.length > 80) return fail(R.string.household_pet_name_invalid)
        write { store.updatePet(household.id, hasPet, name) }
    }

    private fun write(joining: Boolean = false, action: () -> Task<Unit>) {
        if (busy || !resolved || refreshRequired || !HouseholdSession.isCurrent(uid, version)) return
        busy = true
        error = null
        notifyUi()
        val task = try {
            action()
        } catch (exception: Exception) {
            busy = false
            error = requestError(exception)
            notifyUi()
            return
        }
        task.addOnCompleteListener { task ->
            if (!HouseholdSession.isCurrent(uid, version)) return@addOnCompleteListener
            busy = false
            if (task.isSuccessful) {
                refreshRequired = true
                HouseholdSession.invalidateMembership()
                load()
            } else {
                val code = (task.exception as? FirebaseFirestoreException)?.code
                error = if (joining && code in listOf(
                        FirebaseFirestoreException.Code.NOT_FOUND,
                        FirebaseFirestoreException.Code.PERMISSION_DENIED
                    )) R.string.household_code_not_found else requestError(task.exception)
                notifyUi()
            }
        }
    }

    private fun fail(message: Int) {
        error = message
        notifyUi()
    }

    private fun requestError(exception: Exception?): Int = when ((exception as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            R.string.household_network_error
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.household_permission_error
        FirebaseFirestoreException.Code.FAILED_PRECONDITION -> R.string.household_setup_error
        else -> R.string.household_request_error
    }

    private fun notifyUi() { changed.value = (changed.value ?: 0) + 1 }
}
