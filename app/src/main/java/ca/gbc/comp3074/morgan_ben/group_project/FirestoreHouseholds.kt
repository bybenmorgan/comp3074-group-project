package ca.gbc.comp3074.morgan_ben.group_project

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source

class FirestoreHouseholds(private val uid: String) {
    private val db = FirebaseFirestore.getInstance()
    private val households = db.collection("households")
    private val account = db.collection("users").document(uid)

    fun load(): Task<HouseholdMembership> {
        val list = households.whereArrayContains("memberUids", uid).get(Source.SERVER)
        val selection = account.get(Source.SERVER)
        return Tasks.whenAll(list, selection).continueWithTask {
            if (!it.isSuccessful) return@continueWithTask Tasks.forException(
                list.exception ?: selection.exception ?: it.exception!!
            )
            val joined = list.result.documents.map { doc ->
                Household(
                    doc.id, doc.getString("name").orEmpty(),
                    doc.getBoolean("hasPet") ?: false, doc.getString("petName").orEmpty(),
                    (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
                )
            }
            val savedId = selection.result.getString("activeHouseholdId")
            val result = resolveMembership(uid, joined, savedId)
            if (savedId == result.activeId) Tasks.forResult(result)
            else select(result.activeId).continueWith { saved ->
                if (!saved.isSuccessful) throw saved.exception!!
                result
            }
        }
    }

    fun create(name: String, hasPet: Boolean, petName: String): Task<Unit> {
        require(validHouseholdName(name))
        require(petName.length <= 80)
        val household = households.document()
        return db.runTransaction { transaction ->
            checkAccount()
            transaction.get(account)
            transaction.set(household, mapOf(
                "name" to name, "hasPet" to hasPet, "petName" to petName,
                "memberUids" to listOf(uid)
            ))
            transaction.set(account, mapOf("activeHouseholdId" to household.id), SetOptions.merge())
            Unit
        }
    }

    fun join(code: String): Task<Unit> {
        require(validInviteCode(code))
        // reading the private account makes this an online transacton even before membership exists
        return db.runTransaction { transaction ->
            checkAccount()
            transaction.get(account)
            transaction.update(households.document(code), "memberUids", FieldValue.arrayUnion(uid))
            transaction.set(account, mapOf("activeHouseholdId" to code), SetOptions.merge())
            Unit
        }
    }

    fun select(id: String?): Task<Unit> = db.runTransaction { transaction ->
        checkAccount()
        transaction.get(account)
        transaction.set(account, mapOf("activeHouseholdId" to id), SetOptions.merge())
        Unit
    }

    fun leave(id: String): Task<Unit> = db.runTransaction { transaction ->
        checkAccount()
        val user = transaction.get(account)
        val household = households.document(id)
        val data = transaction.get(household)
        check((data.get("memberUids") as? List<*>)?.contains(uid) == true)
        transaction.update(household, "memberUids", FieldValue.arrayRemove(uid))
        if (user.getString("activeHouseholdId") == id) {
            transaction.set(account, mapOf("activeHouseholdId" to null), SetOptions.merge())
        }
        Unit
    }

    fun updatePet(id: String, hasPet: Boolean, petName: String): Task<Unit> {
        require(petName.length <= 80)
        return db.runTransaction { transaction ->
            checkAccount()
            transaction.get(account)
            transaction.update(households.document(id), mapOf("hasPet" to hasPet, "petName" to petName))
            Unit
        }
    }

    private fun checkAccount() {
        check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Account changed" }
    }
}
