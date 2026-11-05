const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, deleteDoc, arrayUnion, arrayRemove,
  collection, getDocs, query, where, writeBatch, runTransaction } = require('firebase/firestore');
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

    const leave = writeBatch(bob);
    leave.update(home(bob), { memberUids: arrayRemove('bob') });
    leave.set(user(bob, 'bob'), { activeHouseholdId: null });
    await allowed(leave.commit());
    await denied(getDoc(home(bob)));
    await denied(getDoc(feature(bob)));
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
