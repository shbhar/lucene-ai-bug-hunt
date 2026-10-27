# Concurrent doc-values updates can make a merge throw NullPointerException and close the writer

Bug LFV-013 · Area: index / doc-values updates · Priority: medium · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-013.html](../../bugs/LFV-013.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update a doc-values field that is new to a segment while that segment is being merged. You expect the merge to finish. Instead writeFieldUpdates records the not-yet-applicable field as NUMERIC with dvGen=-1, the merge throws NullPointerException in DocIDMerger, and the IndexWriter closes tragically.

## Expected vs actual

- Expected: The merge of `_0` and `_1` succeeds and the writer stays open: 4 documents, `a.num=7`, `a.tag=a-tag`; all 6 tests pass.
- Actual: `maybeMerge` throws `NullPointerException` at `DocIDMerger.java:123` and the writer closes; 3 of 6 tests fail, with assertions on and off.

## Files

- [src/fvverify/TestVerifyMergeNPEUnfinishedDVPacket.java](src/fvverify/TestVerifyMergeNPEUnfinishedDVPacket.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 8 test(s) run, 3 failed (testBinaryUpdateOfFieldNewToSegment, testNrtReaderInWindowSeesTypedFieldWithoutValues, testSoftDeleteFieldNewToSegment).
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
     execute --select-class fvverify.TestVerifyMergeNPEUnfinishedDVPacket
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestMergeDuringUnfinishedDocValuesUpdate.java](TestMergeDuringUnfinishedDocValuesUpdate.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
