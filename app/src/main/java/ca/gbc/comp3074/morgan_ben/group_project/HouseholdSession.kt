package ca.gbc.comp3074.morgan_ben.group_project

import com.google.firebase.auth.FirebaseAuth

object HouseholdSession {
    private var uid: String? = null
    private var membership: HouseholdMembership? = null
    var generation = 0
        private set

    init {
        FirebaseAuth.getInstance().addAuthStateListener { syncAccount() }
    }

    private fun syncAccount() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != currentUid) {
            clear()
            uid = currentUid
        }
    }

    fun clear() {
        membership = null
        uid = null
        generation++
    }

    fun invalidateMembership() {
        membership = null
    }

    fun current(): HouseholdMembership? {
        syncAccount()
        return membership
    }

    fun isCurrent(accountUid: String, version: Int): Boolean {
        syncAccount()
        return uid == accountUid && generation == version
    }

    fun accept(accountUid: String, version: Int, result: HouseholdMembership) {
        if (isCurrent(accountUid, version)) membership = result
    }
}
