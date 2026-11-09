package ca.gbc.comp3074.morgan_ben.group_project

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.util.Date

class FirestoreExpenses(private val householdId: String, private val uid: String) {
    private val db = FirebaseFirestore.getInstance()
    private val household = db.collection("households").document(householdId)
    private val expenses = household.collection("expenses")

    fun newId(): String = expenses.document().id

    fun load(): Task<List<SharedExpense>> = expenses.orderBy("date", Query.Direction.DESCENDING)
        .get(Source.SERVER).continueWith { task ->
            if (!task.isSuccessful) throw task.exception!!
            task.result.documents.map { doc ->
                val recorded = checkNotNull(doc.getTimestamp("date")).toDate().time
                SharedExpense(
                    doc.id, checkNotNull(doc.getString("description")),
                    checkNotNull(doc.getLong("amountCents")), checkNotNull(doc.getString("payerUid")),
                    checkNotNull(doc.getString("payerLabel")),
                    (doc.get("participantUids") as List<*>).map { it as String },
                    checkNotNull(doc.getString("creatorUid")),
                    recorded,
                    doc.getTimestamp("due")?.toDate()?.time ?: recorded
                )
            }.sortedWith(compareBy<SharedExpense> { it.dueMillis }.thenBy { it.id })
        }

    fun save(expense: SharedExpense): Task<Unit> = db.runTransaction { transaction ->
        check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Account changed" }
        val document = expenses.document(expense.id)
        val existing = transaction.get(document)
        val home = transaction.get(household)
        check(uid in (home.get("memberUids") as List<*>)) { "Membership changed" }
        if (!existing.exists()) {
            check((home.get("memberUids") as List<*>).toSet() == expense.participantUids.toSet()) {
                "Membership changed"
            }
            transaction.set(document, mapOf(
                "description" to expense.description,
                "amountCents" to expense.amountCents,
                "payerUid" to expense.payerUid,
                "payerLabel" to expense.payerLabel,
                "participantUids" to expense.participantUids,
                "creatorUid" to uid,
                "date" to FieldValue.serverTimestamp(),
                "due" to Timestamp(Date(expense.dueMillis))
            ))
        }
        Unit
    }
}
