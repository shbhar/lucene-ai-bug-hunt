# addIndexes publishes segments outside the full-flush boundary, breaking sequence-number ordering

Bug LFV-014 · Area: index / addIndexes · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-014.html](../../bugs/LFV-014.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call addIndexes while other threads delete or update documents, and you rely on sequence numbers to order those operations. You expect operations with a smaller seqNo not to touch the added documents, and a commit to contain only operations up to its seqNo. Instead deletes or updates with a smaller seqNo hit the added documents, and a commit can include later operations.

## Expected vs actual

- Expected: The delete (seqNo 5) is ordered before `addIndexes` (seqNo 6), so live values are `[100]`; commit 7 does not contain the document added by `addIndexes` 8.
- Actual: 3 of 4 tests fail: live values `` with both `addIndexes` overloads, and commit 7 contains the document added by `addIndexes` 8; the sequential control passes.

## Files

- [src/fvverify/TestAddIndexesSeqNoContractVerify.java](src/fvverify/TestAddIndexesSeqNoContractVerify.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 9 test(s) run, 6 failed (testCommitContainsAddIndexesWithLargerSeqNo_hookBeforeApplyDeletes, testCommitContainsAddIndexesWithLargerSeqNo_hookDuringDWPTFlush, testDeleteByTermWithSmallerSeqNoDeletesDocAddedByAddIndexes, testDeleteWithSmallerSeqNoDeletesDocAddedByAddIndexesCodecReader, testNumericDVUpdateWithSmallerSeqNoRewritesDocAddedByAddIndexes, testUpdateDocumentWithSmallerSeqNoDeletesDocAddedByAddIndexes).
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
     execute --select-class fvverify.TestAddIndexesSeqNoContractVerify
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestAddIndexesSeqNoContract.java](TestAddIndexesSeqNoContract.java)
- [TestAddIndexesSeqNoContractVerify.java](TestAddIndexesSeqNoContractVerify.java)
- [fix-b-publish-at-full-flush.diff](fix-b-publish-at-full-flush.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
