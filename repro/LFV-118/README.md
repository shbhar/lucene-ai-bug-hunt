# The frozen-FieldType validation cache (#15886) keys a field on its first value's type, so for a field built from several field types a later document skips the per-field schema check (or is checked against stale state after a rejected addBatch), depending on value order, flushes and earlier operations

Bug LFV-118 · Area: index / IndexingChain per-field schema validation (frozen-FieldType cache) · Priority: medium · Impact: wrong error · Novelty: new

Bug page: [../../bugs/LFV-118.html](../../bugs/LFV-118.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents whose field id is a StringField plus a SortedDocValuesField, adding the StringField first, and then a document that has only the StringField. You expect IllegalArgumentException, as Lucene's per-field consistency rule (LUCENE-9334, MIGRATE.md) requires, as 9.0-10.4 do, and as main still does when the doc-values value comes first, the type is an unfrozen copy, or a flush comes in between. Instead the document is accepted, because the frozen-FieldType cache added in 10.5.0 (#15886) caches the first value's type after the union of all the document's values was validated and then skips the check for every later document whose first value has that type; on the same fast path the schema state is not rebuilt, so after a rejected addBatch a valid StringField + StoredField document fails with 'index options: expected DOCS, but it has NONE'.

## Expected vs actual

- Expected: Both documents without doc values are rejected ("doc values type: expected 'SORTED', but it has 'NONE'"), and the valid document after the rejected batch is accepted.
- Actual: 3 of 4 tests fail: both documents without doc values are accepted, and the valid document is rejected with "index options: expected 'DOCS', but it has 'NONE'".

## Files

- [src/org/apache/lucene/index/TestFrozenTypeCacheMultiTypeField.java](src/org/apache/lucene/index/TestFrozenTypeCacheMultiTypeField.java) (JUnit test)
- [src/org/apache/lucene/document/TestPerFieldConsistencyReversedOrder.java](src/org/apache/lucene/document/TestPerFieldConsistencyReversedOrder.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFrozenTypeCacheMultiTypeField

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestPerFieldConsistencyReversedOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestFrozenTypeCacheSchemaCheck.java](TestFrozenTypeCacheSchemaCheck.java)
- [regression-tests.diff](regression-tests.diff)
- [TestFrozenTypeCacheMultiTypeField.java](TestFrozenTypeCacheMultiTypeField.java)
- [OutcomeProbe.java](OutcomeProbe.java)

Formal models and scripts this bug's part of the hunt used: [../../models/indexingchain-schema/](../../models/indexingchain-schema/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
