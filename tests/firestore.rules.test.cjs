const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, deleteDoc, arrayUnion, arrayRemove,
  collection, getDocs, query, where, writeBatch, runTransaction, serverTimestamp,
  Timestamp, orderBy } = require('firebase/firestore');
const assert = require('node:assert/strict');

// refuse to run unless emulators:exec provides a local Firestore endpoint
if (!/^127\.0\.0\.1:\d+$/.test(process.env.FIRESTORE_EMULATOR_HOST || '')) {
  throw new Error('Run with the local Firestore emulator, never a live project.');
}

async function main() {
  const env = await initializeTestEnvironment({
    projectId: 'demo-household-review',
    firestore: { rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8') }
  });
  let checks = 0;
  async function allowed(task) { await assertSucceeds(task); checks++; }
  async function denied(task) { await assertFails(task); checks++; }
  try {
    await env.clearFirestore();
    const alice = env.authenticatedContext('alice', { email: 'alice@example.com' }).firestore();
    const bob = env.authenticatedContext('bob', { email: 'bob@example.com' }).firestore();
    const eve = env.authenticatedContext('eve', { email: 'eve@example.com' }).firestore();
    const guest = env.unauthenticatedContext().firestore();
    const code = 'aB0123456789CDefGHiJ';
    const second = '12345678901234567890';
    const path = `households/${code}`;
    const home = db => doc(db, path);
    const user = (db, uid) => doc(db, `users/${uid}`);
    const member = (db, uid) => doc(db, `${path}/members/${uid}`);
    const details = { name: 'Home', hasPet: false, petName: '', memberUids: ['alice'] };

    await allowed(runTransaction(alice, async tx => {
      await tx.get(user(alice, 'alice'));
      tx.set(home(alice), details);
      tx.set(user(alice, 'alice'), { activeHouseholdId: code });
      tx.set(member(alice, 'alice'), { label: 'alice@example.com' });
    }));
    await allowed(getDoc(home(alice)));
    await denied(getDoc(home(guest)));
    await denied(getDoc(home(eve)));
    await denied(getDocs(collection(eve, 'households')));
    await denied(getDocs(query(collection(eve, 'households'), where('memberUids', 'array-contains', 'alice'))));
    await denied(getDocs(collection(alice, 'households')));
    await allowed(getDocs(query(collection(alice, 'households'), where('memberUids', 'array-contains', 'alice'))));
    await denied(setDoc(doc(eve, `households/${second}`), { ...details, memberUids: ['eve', 'alice'] }));
    await denied(setDoc(doc(eve, 'households/short-code'), { ...details, memberUids: ['eve'] }));
    await denied(updateDoc(home(eve), { name: 'Stolen' }));
    await denied(updateDoc(home(eve), { memberUids: ['eve'] }));
    await denied(updateDoc(home(eve), { memberUids: arrayUnion('eve', 'bob') }));
    await denied(updateDoc(home(eve), { memberUids: arrayUnion('eve'), hasPet: true }));
    await denied(updateDoc(home(alice), { memberUids: arrayUnion('bob') }));
    await denied(updateDoc(home(alice), { memberUids: arrayRemove('alice'), hasPet: true }));
    await denied(setDoc(user(eve, 'alice'), { activeHouseholdId: null }));
    await denied(getDoc(user(bob, 'alice')));
    await denied(setDoc(user(eve, 'eve'), { activeHouseholdId: code }));
    await denied(setDoc(user(alice, 'alice'), { activeHouseholdId: code, role: 'admin' }));
    await allowed(getDoc(member(alice, 'alice')));
    await denied(getDoc(member(eve, 'alice')));
    await denied(getDocs(collection(eve, `${path}/members`)));
    await denied(getDoc(member(guest, 'alice')));
    await denied(setDoc(member(eve, 'eve'), { label: 'eve@example.com' }));
    await denied(setDoc(member(guest, 'guest'), { label: 'guest@example.com' }));
    await denied(setDoc(member(alice, 'bob'), { label: 'bob@example.com' }));
    await denied(setDoc(member(alice, 'alice'), {}));
    await denied(setDoc(member(alice, 'alice'), { label: null }));
    await denied(setDoc(member(alice, 'alice'), { label: 123 }));
    await denied(setDoc(member(alice, 'alice'), { label: '' }));
    await denied(setDoc(member(alice, 'alice'), { label: 'other@example.com' }));
    await denied(setDoc(member(alice, 'alice'), { label: 'alice@example.com', uid: 'alice' }));
    await denied(deleteDoc(member(alice, 'alice')));
    await denied(setDoc(doc(alice, `${path}/members/alice/nested/bypass`), { label: 'Bypass' }));

    const badJoin = writeBatch(bob);
    badJoin.update(home(bob), { memberUids: arrayUnion('bob') });
    badJoin.set(member(bob, 'alice'), { label: 'bob@example.com' });
    await denied(badJoin.commit());
    await allowed(runTransaction(bob, async tx => {
      await tx.get(user(bob, 'bob'));
      tx.update(home(bob), { memberUids: arrayUnion('bob') });
      tx.set(user(bob, 'bob'), { activeHouseholdId: code });
      tx.set(member(bob, 'bob'), { label: 'bob@example.com' });
    }));
    await allowed(getDocs(collection(bob, `${path}/members`)));
    await allowed(getDoc(member(bob, 'alice')));
    await allowed(updateDoc(member(bob, 'bob'), { label: 'bob@example.com' }));
    await denied(updateDoc(member(bob, 'alice'), { label: 'bob@example.com' }));
    await denied(setDoc(member(bob, 'alice'), { label: 'alice@example.com' }));
    await denied(deleteDoc(member(bob, 'alice')));
    assert.equal((await getDoc(member(bob, 'alice'))).data().label, 'alice@example.com');
    checks++;
    await allowed(getDoc(home(bob)));
    assert.deepEqual((await getDoc(home(bob))).data().memberUids, ['alice', 'bob']);
    checks++;
    await denied(updateDoc(home(bob), { memberUids: arrayRemove('alice') }));
    await denied(updateDoc(doc(eve, 'households/ZZZZZZZZZZZZZZZZZZZZ'), { memberUids: arrayUnion('eve') }));
    await allowed(updateDoc(home(bob), { hasPet: true, petName: 'Milo' }));
    await denied(updateDoc(home(bob), { petName: 'a'.repeat(81) }));
    await denied(updateDoc(home(bob), { hasPet: 'yes' }));
    await denied(deleteDoc(home(alice)));

    const expense = (db, id = 'expense-1') => doc(db, `${path}/expenses/${id}`);
    const expenseData = () => ({ description: 'Dinner', amountCents: 1001,
      payerUid: 'bob', payerLabel: 'bob@example.com', participantUids: ['alice', 'bob'],
      creatorUid: 'alice', date: serverTimestamp(), due: Timestamp.fromMillis(Date.UTC(2026, 10, 15)) });
    await allowed(setDoc(expense(alice), expenseData()));
    await allowed(getDoc(expense(bob)));
    await allowed(getDocs(query(collection(alice, `${path}/expenses`), orderBy('date', 'desc'))));
    await denied(getDoc(expense(eve)));
    await denied(getDocs(collection(eve, `${path}/expenses`)));
    await denied(getDoc(expense(guest)));
    await denied(getDocs(collection(guest, `${path}/expenses`)));
    await denied(setDoc(expense(eve, 'outsider'), { ...expenseData(), creatorUid: 'eve' }));
    await denied(setDoc(expense(guest, 'guest'), expenseData()));
    const invalidExpenses = [
      { description: '' }, { description: ' \n\t' }, { description: 'x'.repeat(121) },
      { description: 123 }, { amountCents: 0 }, { amountCents: -1 }, { amountCents: 1.5 },
      { amountCents: '100' }, { amountCents: 1000000000 }, { payerUid: 'eve' },
      { payerUid: null }, { payerLabel: '' }, { payerLabel: 4 }, { payerLabel: 'Forged' },
      { payerLabel: 'x'.repeat(321) }, { creatorUid: 'bob' }, { creatorUid: null },
      { participantUids: [] }, { participantUids: ['alice'] },
      { participantUids: ['alice', 'bob', 'eve'] }, { participantUids: ['alice', 'bob', 'bob'] },
      { participantUids: 'alice,bob' }, { participantUids: ['alice', 123] },
      { date: Timestamp.fromMillis(1) }, { date: 'today' }, { date: null },
      { due: 'Nov 15' }, { due: null }, { due: 1 }, { extra: true }
    ];
    for (const [i, change] of invalidExpenses.entries()) {
      await denied(setDoc(expense(alice, `invalid-${i}`), { ...expenseData(), ...change }));
    }
    for (const key of Object.keys(expenseData())) {
      const data = expenseData();
      delete data[key];
      await denied(setDoc(expense(alice, `missing-${key}`), data));
    }
    await denied(updateDoc(expense(alice), { amountCents: 2000 }));
    await denied(setDoc(expense(alice), expenseData()));
    await denied(deleteDoc(expense(bob)));
    await denied(setDoc(doc(alice, `${path}/expenses/expense-1/nested/bypass`), { any: true }));
    await denied(getDoc(doc(alice, `${path}/expenses/expense-1/nested/bypass`)));
    await allowed(runTransaction(alice, async tx => {
      if (!(await tx.get(expense(alice, 'retry'))).exists()) tx.set(expense(alice, 'retry'), expenseData());
    }));
    await allowed(runTransaction(alice, async tx => {
      if (!(await tx.get(expense(alice, 'retry'))).exists()) tx.set(expense(alice, 'retry'), expenseData());
    }));
    assert.equal((await getDocs(collection(alice, `${path}/expenses`))).size, 2);
    checks++;
    await allowed(setDoc(expense(alice, 'alice-paid'), { ...expenseData(),
      payerUid: 'alice', payerLabel: 'alice@example.com' }));
    const historyQuery = await getDocs(query(collection(alice, `${path}/expenses`), orderBy('date', 'desc')));
    assert.equal(historyQuery.docs[0].id, 'alice-paid');
    assert.equal(historyQuery.docs[0].data().creatorUid, 'alice');
    assert.equal(historyQuery.docs[0].data().date instanceof Timestamp, true);
    checks++;

    const feature = db => doc(db, `${path}/chores/chore-1`);
    await allowed(setDoc(feature(alice), { title: 'Sweep', assigneeUid: 'bob' }));
    await allowed(getDoc(feature(bob)));
    await denied(getDoc(feature(eve)));
    await denied(setDoc(feature(eve), { title: 'Stolen' }));
    await denied(getDoc(feature(guest)));
    await denied(setDoc(doc(guest, 'households/00000000000000000000'), details));
    await denied(getDoc(user(guest, 'alice')));
    await denied(updateDoc(home(guest), { memberUids: arrayUnion('guest') }));

    // multiple households and private selection don't affect other members
    await allowed(setDoc(doc(alice, `households/${second}`), details));
    const singleExpense = doc(alice, `households/${second}/expenses/legacy-label`);
    await allowed(setDoc(singleExpense, { ...expenseData(), payerUid: 'alice', payerLabel: 'alice',
      participantUids: ['alice'] }));
    const legacyMember = doc(alice, `households/${second}/members/alice`);
    assert.equal((await getDoc(legacyMember)).exists(), false);
    checks++;
    await allowed(runTransaction(alice, async tx => {
      const record = await tx.get(legacyMember);
      if (!record.exists()) tx.set(legacyMember, { label: 'alice@example.com' });
    }));
    assert.deepEqual((await getDocs(collection(alice, `households/${second}/members`))).docs
      .map(record => [record.id, record.data().label]), [['alice', 'alice@example.com']]);
    checks++;
    await allowed(setDoc(user(alice, 'alice'), { activeHouseholdId: second }));
    assert.equal((await getDoc(user(bob, 'bob'))).data().activeHouseholdId, code);
    checks++;
    await allowed(updateDoc(home(alice), { memberUids: arrayRemove('alice') }));
    await denied(getDoc(home(alice)));
    await denied(getDoc(member(alice, 'alice')));
    await denied(getDocs(collection(alice, `${path}/members`)));
    await denied(setDoc(member(alice, 'alice'), { label: 'alice@example.com' }));
    await allowed(getDoc(user(alice, 'alice')));
    await allowed(getDoc(member(bob, 'alice')));
    assert.deepEqual((await getDoc(home(bob))).data().memberUids, ['bob']);
    checks++;
    await allowed(getDoc(home(bob)));
    await allowed(getDoc(feature(bob)));

    await denied(getDoc(expense(alice)));
    await denied(setDoc(expense(alice, 'left'), expenseData()));
    await allowed(getDoc(expense(bob, 'alice-paid')));
    const history = (await getDoc(expense(bob, 'alice-paid'))).data();
    assert.deepEqual(history.participantUids, ['alice', 'bob']);
    assert.equal(history.payerLabel, 'alice@example.com');
    checks++;
    await denied(setDoc(expense(bob, 'stale'), { ...expenseData(), creatorUid: 'bob' }));
    await denied(setDoc(expense(bob, 'departed-payer'), { ...expenseData(),
      creatorUid: 'bob', payerUid: 'alice', payerLabel: 'alice@example.com', participantUids: ['bob'] }));
    await allowed(setDoc(expense(bob, 'single'), { ...expenseData(), creatorUid: 'bob', participantUids: ['bob'] }));

    const joinAgain = writeBatch(alice);
    joinAgain.update(home(alice), { memberUids: arrayUnion('alice') });
    joinAgain.set(member(alice, 'alice'), { label: 'alice@example.com' });
    await allowed(joinAgain.commit());
    await allowed(updateDoc(home(eve), { memberUids: arrayUnion('eve') }));
    const threePersonExpense = { ...expenseData(), participantUids: ['eve', 'bob', 'alice'],
      payerUid: 'eve', payerLabel: 'eve' };
    await allowed(setDoc(expense(alice, 'three'), threePersonExpense));
    await denied(setDoc(expense(alice, 'three-missing'), expenseData()));
    assert.deepEqual((await getDoc(expense(alice, 'three'))).data().participantUids, ['eve', 'bob', 'alice']);
    assert.deepEqual((await getDoc(expense(alice, 'alice-paid'))).data().participantUids, ['alice', 'bob']);
    checks++;
    await allowed(updateDoc(home(eve), { memberUids: arrayRemove('eve') }));
    await allowed(updateDoc(home(alice), { memberUids: arrayRemove('alice') }));

    const leave = writeBatch(bob);
    leave.update(home(bob), { memberUids: arrayRemove('bob') });
    leave.set(user(bob, 'bob'), { activeHouseholdId: null });
    await allowed(leave.commit());
    await denied(getDoc(home(bob)));
    await denied(getDoc(feature(bob)));
    await denied(getDoc(expense(bob)));
    await denied(getDoc(member(bob, 'bob')));
    await denied(getDocs(collection(bob, `${path}/members`)));
    await denied(setDoc(member(bob, 'bob'), { label: 'bob@example.com' }));
    assert.equal((await getDoc(user(bob, 'bob'))).data().activeHouseholdId, null);
    checks++;
    await env.withSecurityRulesDisabled(async context => {
      const data = await getDoc(home(context.firestore()));
      assert.deepEqual(data.data().memberUids, []);
      assert.equal((await getDoc(feature(context.firestore()))).data().title, 'Sweep');
      assert.equal((await getDoc(member(context.firestore(), 'alice'))).data().label, 'alice@example.com');
      assert.equal((await getDoc(member(context.firestore(), 'bob'))).data().label, 'bob@example.com');
    });
    checks++;
    console.log(`Passed ${checks} Firestore emulator checks.`);
  } finally {
    await env.cleanup();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
