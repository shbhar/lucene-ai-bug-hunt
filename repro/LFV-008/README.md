# Removing a document's soft-delete value brings a hard-deleted document back

Bug LFV-008 · Area: index / soft deletes · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-008.html](../../bugs/LFV-008.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

With a soft-deletes field configured, you hard-delete a document and then clear its soft-delete value. You expect the document to stay deleted. Instead PendingSoftDeletes.applySoftDeletes sets its live bit again, so it becomes visible; softDelCount can reach -1, which wedges the writer and gives a corrupt commit or merge.

The test shows that with a soft-deletes field (b soft-deleted), hard-deleting doc a and then removing a's soft value with `tryUpdateDocValue(reader, 0, null)` makes a live again.

## Expected vs actual

Expected: `count(id:a)` is 0 on the next NRT reader (a hard-deleted doc stays deleted, `IndexWriterConfig#setSoftDeletesField`). Actual (`-ea` and `-da`): `expected:<0> but was:<1>`.

## Files

- [src/org/apache/lucene/index/TestSoftValueRemovalRevivesHardDeletedDoc.java](src/org/apache/lucene/index/TestSoftValueRemovalRevivesHardDeletedDoc.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestSoftValueRemovalRevivesHardDeletedDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
