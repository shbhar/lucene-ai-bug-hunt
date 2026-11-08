# SegmentInfos.readCommit compares delCount + softDelCount with maxDoc in int arithmetic, so for a segment of 2^30 or more documents an over-sized pair of deletion counts wraps negative and the commit is read without the CorruptIndexException ('invalid deletion count') that the same check gives for smaller segments (since 7.4.0)

Finding LFV-1246 · Area: core / index: SegmentInfos.readCommit (deletion-count consistency check of segments_N) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1246.html](../../bugs/LFV-1246.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read a commit whose segment claims more deleted documents (hard deletes plus soft deletes) than it has; for a small segment SegmentInfos.readLatestCommit rejects it with CorruptIndexException 'invalid deletion count: 10 vs maxDoc=6'. For a segment of 2^30 documents with delCount = softDelCount = 2^30, each count is within maxDoc on its own, but their int sum wraps to Integer.MIN_VALUE, the check 'softDelCount + delCount &gt; info.maxDoc()' passes, and the commit is returned with deletion counts that add up to twice the segment's size. The same method already keeps its total document count in a long; a (long) cast on the sum fixes it.

## Expected vs actual

- Expected: `readLatestCommit` throws `CorruptIndexException`: "invalid deletion count: 2147483648 vs maxDoc=1073741824" with the fix.
- Actual: No exception: "Expected exception CorruptIndexException but no exception was thrown", with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestDeletionCountSumOverflow.java](src/org/apache/lucene/index/TestDeletionCountSumOverflow.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDeletionCountSumOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDeletionCountSumOverflow.java](TestDeletionCountSumOverflow.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
