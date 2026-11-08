package ca.gbc.comp3074.morgan_ben.group_project

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth

class HouseholdBoardViewModel : ViewModel() {
    val changed = MutableLiveData(0)
    var chores = emptyList<ChoreItem>()
        private set
    var groceries = emptyList<GroceryItem>()
        private set
    var appointments = emptyList<AppointmentItem>()
        private set
    var pet = PetCareState()
        private set
    var loading = false
        private set
    var saving = false
        private set
    var error: Int? = null
        private set
    var ready = false
        private set

    private var uid = ""
    private var householdId = ""
    private var version = -1
    private var request = 0
    private var pending: Pending? = null
    private var includeChores = true
    private var includeGroceries = true
    private var includePet = true

    private sealed class Pending {
        data class AddChore(val item: ChoreItem) : Pending()
        data class ChoreDone(val id: String, val done: Boolean) : Pending()
        data class AddGrocery(val item: GroceryItem) : Pending()
        data class GroceryChecked(val id: String, val checked: Boolean) : Pending()
        data class PetTask(val key: String, val checked: Boolean) : Pending()
        data class AddAppointment(val item: AppointmentItem) : Pending()
    }

    private fun current(): Boolean = HouseholdSession.isCurrent(uid, version) &&
        HouseholdSession.current()?.active?.id == householdId

    val canEdit: Boolean
        get() = ready && !loading && !saving && pending == null && current()

    val failedSave: Boolean
        get() = pending != null && !saving && !loading

    fun refresh(chores: Boolean = true, groceries: Boolean = true, pet: Boolean = true) {
        val account = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val home = HouseholdSession.current()?.active ?: return
        includeChores = chores
        includeGroceries = groceries
        includePet = pet
        if (uid != account || householdId != home.id || version != HouseholdSession.generation) {
            uid = account
            householdId = home.id
            version = HouseholdSession.generation
            request++
            saving = false
            pending = null
            this.chores = emptyList()
            this.groceries = emptyList()
            appointments = emptyList()
            this.pet = PetCareState()
            ready = false
        } else if (saving) return
        val ticket = ++request
        loading = true
        error = null
        notifyUi()
        FirestoreHouseholdBoard(householdId, uid)
            .load(includeChores, includeGroceries, includePet)
            .addOnCompleteListener { task ->
                if (ticket != request || !current()) return@addOnCompleteListener
                loading = false
                if (task.isSuccessful) {
                    applyLoaded(task.result)
                    ready = true
                    error = if (pending != null) R.string.list_save_failed else null
                } else error = R.string.list_load_failed
                notifyUi()
            }
    }

    fun addChore(title: String, assignee: String, due: String) {
        if (!canEdit || chores.size >= MAX_HOUSEHOLD_LIST) return
        if (!validListText(title, 120) || assignee.length > 80 || !validListText(due, 40)) {
            failSave()
            return
        }
        pending = Pending.AddChore(ChoreItem(newId(), title.trim(), assignee, due, false))
        commit()
    }

    fun setChoreDone(id: String, done: Boolean) {
        if (!canEdit || chores.none { it.id == id }) return
        pending = Pending.ChoreDone(id, done)
        commit()
    }

    fun addGrocery(name: String) {
        if (!canEdit || groceries.size >= MAX_HOUSEHOLD_LIST) return
        if (!validListText(name, 120)) {
            failSave()
            return
        }
        pending = Pending.AddGrocery(GroceryItem(newId(), name.trim(), false))
        commit()
    }

    fun setGroceryChecked(id: String, checked: Boolean) {
        if (!canEdit || groceries.none { it.id == id }) return
        pending = Pending.GroceryChecked(id, checked)
        commit()
    }

    fun setPetTask(key: String, checked: Boolean) {
        if (!canEdit || !validPetTask(key)) return
        pending = Pending.PetTask(key, checked)
        commit()
    }

    fun addAppointment(title: String, date: String, time: String) {
        if (!canEdit || appointments.size >= MAX_HOUSEHOLD_LIST) return
        if (!validListText(title, 120) || date.length > 40 || time.length > 40 ||
            date.isBlank() || time.isBlank()
        ) {
            failSave()
            return
        }
        pending = Pending.AddAppointment(AppointmentItem(newId(), title.trim(), date, time))
        commit()
    }

    fun retry() {
        if (pending != null && ready) commit() else refresh(includeChores, includeGroceries, includePet)
    }

    fun discardFailedSave() {
        if (!failedSave) return
        pending = null
        refresh(includeChores, includeGroceries, includePet)
    }

    override fun onCleared() {
        request++
    }

    private fun applyLoaded(board: HouseholdBoard) {
        if (includeChores) chores = board.chores
        if (includeGroceries) groceries = board.groceries
        if (includePet) {
            appointments = board.appointments
            pet = board.pet
        }
    }

    private fun newId(): String = FirestoreHouseholdBoard(householdId, uid).newId()

    private fun commit() {
        val change = pending ?: return
        if (saving || !ready || !current()) return
        val remote = FirestoreHouseholdBoard(householdId, uid)
        val ticket = request
        saving = true
        error = null
        notifyUi()
        val task = try {
            change.task(remote)
        } catch (exception: Exception) {
            saving = false
            error = R.string.list_save_failed
            notifyUi()
            return
        }
        task.addOnCompleteListener { result ->
            if (ticket != request || !current()) return@addOnCompleteListener
            saving = false
            if (result.isSuccessful) {
                pending = null
                refresh(includeChores, includeGroceries, includePet)
            } else {
                error = R.string.list_save_failed
                notifyUi()
            }
        }
    }

    private fun Pending.task(remote: FirestoreHouseholdBoard): Task<Unit> = when (this) {
        is Pending.AddChore -> remote.addChore(item)
        is Pending.ChoreDone -> remote.setChoreDone(id, done)
        is Pending.AddGrocery -> remote.addGrocery(item)
        is Pending.GroceryChecked -> remote.setGroceryChecked(id, checked)
        is Pending.PetTask -> remote.setPetTask(key, checked)
        is Pending.AddAppointment -> remote.addAppointment(item)
    }

    private fun failSave() {
        error = R.string.list_save_failed
        notifyUi()
    }

    private fun notifyUi() {
        changed.value = (changed.value ?: 0) + 1
    }
}
