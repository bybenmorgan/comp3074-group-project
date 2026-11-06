package ca.gbc.comp3074.morgan_ben.group_project

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class ExpensesViewModel : ViewModel() {
    val changed = MutableLiveData(0)
    var items = emptyList<SharedExpense>()
        private set
    var loading = false
        private set
    var saving = false
        private set
    var error: Int? = null
        private set
    var ready = false
        private set
    var dialogOpen = false
    var description = ""
    var amount = ""
    var payerUid: String? = null
    private var uid = ""
    private var householdId = ""
    private var version = -1
    private var request = 0
    private var pending: SharedExpense? = null

    private fun current(): Boolean = HouseholdSession.isCurrent(uid, version) &&
        HouseholdSession.current()?.active?.id == householdId

    val matchesHousehold: Boolean get() = current()

    fun refresh() {
        val account = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val home = HouseholdSession.current()?.active ?: return
        if (uid != account || householdId != home.id || version != HouseholdSession.generation) {
            uid = account
            householdId = home.id
            version = HouseholdSession.generation
            request++
            saving = false
            pending = null
            clearDraft()
        } else if (saving) return
        val ticket = ++request
        items = emptyList()
        ready = false
        loading = true
        error = null
        notifyUi()
        FirestoreHouseholds(uid).load().addOnCompleteListener { membership ->
            if (ticket != request || !current()) return@addOnCompleteListener
            if (!membership.isSuccessful) {
                loadFailed()
                return@addOnCompleteListener
            }
            HouseholdSession.accept(uid, version, membership.result)
            val active = membership.result.active
            if (active == null) {
                loading = false
                notifyUi()
                return@addOnCompleteListener
            }
            if (householdId != active.id) {
                householdId = active.id
                pending = null
                clearDraft()
            }
            FirestoreExpenses(householdId, uid).load().addOnCompleteListener { expenses ->
                if (ticket != request || !current()) return@addOnCompleteListener
                loading = false
                if (expenses.isSuccessful) {
                    items = expenses.result
                    ready = true
                    error = if (pending != null) R.string.expense_save_failed else null
                } else error = R.string.expense_load_failed
                notifyUi()
            }
        }
    }

    fun save() {
        if (saving || loading || !ready || !current()) return
        if (pending == null) {
            val members = HouseholdSession.current()?.active?.members.orEmpty()
            val payer = members.firstOrNull { it.uid == payerUid } ?: return
            val cents = parseExpenseCents(amount) ?: return
            if (description.isBlank() || description.trim().length > 120) return
            pending = SharedExpense(
                FirestoreExpenses(householdId, uid).newId(), description.trim(), cents,
                payer.uid, payer.label, members.map { it.uid }.sorted(), uid, 0
            )
        }
        val expense = pending!!
        val ticket = request
        saving = true
        error = null
        dialogOpen = false
        notifyUi()
        FirestoreExpenses(householdId, uid).save(expense).addOnCompleteListener { task ->
            if (ticket != request || !current()) return@addOnCompleteListener
            saving = false
            if (task.isSuccessful) {
                pending = null
                clearDraft()
                refresh()
            } else {
                error = R.string.expense_save_failed
                notifyUi()
            }
        }
    }

    fun retry() {
        if (pending != null && ready) save() else refresh()
    }

    val failedSave: Boolean get() = pending != null && !saving && !loading

    fun discardFailedSave() {
        if (!failedSave) return
        pending = null
        clearDraft()
        refresh()
    }

    val canAdd: Boolean get() = ready && !loading && !saving && pending == null && current()

    private fun clearDraft() {
        dialogOpen = false
        description = ""
        amount = ""
        payerUid = null
    }

    private fun loadFailed() {
        loading = false
        error = R.string.expense_load_failed
        notifyUi()
    }

    private fun notifyUi() { changed.value = (changed.value ?: 0) + 1 }
}
