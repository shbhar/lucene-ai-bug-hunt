# A try* un-delete or pending soft-delete change is ignored or lost by later term-based operations

Bug LFV-010 · Area: index / soft deletes · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-010.html](../../bugs/LFV-010.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

With a soft-deletes field, you revive a document with tryUpdateDocValue (or change its soft field by term) and then issue term-based deletes or updates. You expect the later operations to see the revived or newly soft-deleted state. Instead term-based ops resolve against live docs that are only materialized when doc-values updates are written, so the revived document is skipped (duplicate live keys) or lost when its segment looks 100% deleted.

## Expected vs actual

- Expected: 1 live x after the replace (K1c); x live after y is deleted (K2); 0 live x after the second soft delete (K1).
- Actual: 2 live x (`expected:<1> but was:<2>`); x gone (`expected:<1> but was:<0>`); 1 live x (`expected:<0> but was:<1>`). No exception; CheckIndex passes on the K1c and K2 commits.

## Files

- [src/TestTryUndeleteVsBufferedOps.java](src/TestTryUndeleteVsBufferedOps.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 15 test(s) run, 10 failed (testK1_retentionMergePolicy, testK1_termSoftDeleteAfterTryRevive, testK1b_termNumericUpdateAfterTryRevive, testK1c_duplicatePersistsInCommit, testK1c_softUpdateDocumentAfterTryRevive, testK2_bufferedHardDeleteOfOtherDocUndoesRevive).
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestTryUndeleteVsBufferedOps
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestTryUndeleteVsBufferedOps.java](TestTryUndeleteVsBufferedOps.java)
- [TestTrySoftUpdateEdgeCases.java](TestTrySoftUpdateEdgeCases.java)
- [fix-b-apply-try-soft-update-now.diff](fix-b-apply-try-soft-update-now.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
