package ca.gbc.comp3074.morgan_ben.group_project

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction

class FirestoreHouseholdBoard(private val householdId: String, private val uid: String) {
    private val db = FirebaseFirestore.getInstance()
    private val household = db.collection("households").document(householdId)
    private val chores = household.collection("chores")
    private val groceries = household.collection("groceries")
    private val appointments = household.collection("appointments")
    private val pet = household.collection("petCare").document("daily")

    fun newId(): String = chores.document().id

    fun load(includeChores: Boolean, includeGroceries: Boolean, includePet: Boolean): Task<HouseholdBoard> {
        val choreTask = if (includeChores) chores.get(Source.SERVER) else null
        val groceryTask = if (includeGroceries) groceries.get(Source.SERVER) else null
        val appointmentTask = if (includePet) appointments.get(Source.SERVER) else null
        val petTask = if (includePet) pet.get(Source.SERVER) else null
        val requested = listOfNotNull(choreTask, groceryTask, appointmentTask, petTask)
        return Tasks.whenAll(requested).continueWith { done ->
            if (!done.isSuccessful) {
                throw requested.firstNotNullOfOrNull { it.exception } ?: done.exception!!
            }
            HouseholdBoard(
                chores = choreTask?.result?.documents?.mapNotNull { it.toChore() }.orEmpty().let(::sortedChores),
                groceries = groceryTask?.result?.documents?.mapNotNull { it.toGrocery() }.orEmpty()
                    .let(::sortedGroceries),
                appointments = appointmentTask?.result?.documents?.mapNotNull { it.toAppointment() }.orEmpty()
                    .let(::sortedAppointments),
                pet = if (includePet) parsePetCare(petTask?.result?.data) else PetCareState()
            )
        }
    }

    fun addChore(item: ChoreItem): Task<Unit> = write { transaction ->
        transaction.set(chores.document(item.id), mapOf(
            "title" to item.title,
            "assignee" to item.assignee,
            "due" to item.due,
            "done" to item.done,
            "createdAt" to FieldValue.serverTimestamp()
        ))
    }

    fun setChoreDone(id: String, done: Boolean): Task<Unit> = write { transaction ->
        val document = chores.document(id)
        check(transaction.get(document).exists()) { "Chore was removed" }
        transaction.update(document, "done", done)
    }

    fun addGrocery(item: GroceryItem): Task<Unit> = write { transaction ->
        transaction.set(groceries.document(item.id), mapOf(
            "name" to item.name,
            "checked" to item.checked,
            "createdAt" to FieldValue.serverTimestamp()
        ))
    }

    fun setGroceryChecked(id: String, checked: Boolean): Task<Unit> = write { transaction ->
        val document = groceries.document(id)
        check(transaction.get(document).exists()) { "Grocery was removed" }
        transaction.update(document, "checked", checked)
    }

    fun setPetTask(key: String, checked: Boolean): Task<Unit> = write { transaction ->
        check(validPetTask(key))
        transaction.set(pet, mapOf(key to checked), SetOptions.merge())
    }

    fun addAppointment(item: AppointmentItem): Task<Unit> = write { transaction ->
        transaction.set(appointments.document(item.id), mapOf(
            "title" to item.title,
            "date" to item.date,
            "time" to item.time,
            "createdAt" to FieldValue.serverTimestamp()
        ))
    }

    private fun write(action: (Transaction) -> Unit): Task<Unit> =
        db.runTransaction { transaction ->
            check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Account changed" }
            val home = transaction.get(household)
            check(uid in (home.get("memberUids") as List<*>)) { "Membership changed" }
            action(transaction)
            Unit
        }

    private fun DocumentSnapshot.toChore(): ChoreItem? =
        data?.let { parseChore(id, it, createdAtMillis()) }

    private fun DocumentSnapshot.toGrocery(): GroceryItem? =
        data?.let { parseGrocery(id, it, createdAtMillis()) }

    private fun DocumentSnapshot.toAppointment(): AppointmentItem? =
        data?.let { parseAppointment(id, it, createdAtMillis()) }

    private fun DocumentSnapshot.createdAtMillis(): Long = getTimestamp("createdAt")?.toDate()?.time ?: 0L
}
