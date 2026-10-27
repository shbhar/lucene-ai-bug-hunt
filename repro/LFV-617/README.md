# UpgradeIndexMergePolicy rewrites the old segments its delegate declines with its own plain OneMerge, which a wrapping delegate never sees, so IndexUpgrader (or UpgradeIndexMergePolicy) with a SoftDeletesRetentionMergePolicy inside silently purges the soft-deleted documents the retention policy must keep

Bug LFV-617 · Area: core index / UpgradeIndexMergePolicy and IndexUpgrader with OneMergeWrappingMergePolicy delegates (SoftDeletesRetentionMergePolicy) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-617.html](../../bugs/LFV-617.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure soft deletes with SoftDeletesRetentionMergePolicy("soft", MatchAllDocsQuery::new, new TieredMergePolicy), which must keep every soft-deleted document, and upgrade a one-segment index written by Lucene 10.5.2 (10 visible plus 3 soft-deleted documents) with new IndexUpgrader(dir, thatConfig, false).upgrade. You expect the segment to be rewritten in the current format with all 13 documents, exactly as happens when the same index has two old segments. Instead TieredMergePolicy treats the lone segment as already merged, UpgradeIndexMergePolicy rewrites it with its own 'merge remaining ones into new segment' OneMerge that the retention policy never wraps, and the 3 retained documents are purged with no error (maxDoc 13 -&gt; 10; partial retention queries lose their documents the same way).

## Expected vs actual

- Expected: The upgraded segment holds all 13 documents: 10 visible and the 3 retained soft-deleted ones (`maxDoc` 13).
- Actual: It holds 10 (`maxDoc` 10; the 3 retained documents are gone), with no error; the two-segment and retention-outermost controls keep 13.

## Files

- [src/org/apache/lucene/index/TestUpgradeKeepsRetainedSoftDeletes.java](src/org/apache/lucene/index/TestUpgradeKeepsRetainedSoftDeletes.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- Not runnable from this site as is: this repro also needs extra JVM setup (such as an instrumentation hook) that is not exported here; see the comments at the top of the source.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestUpgradeKeepsRetainedSoftDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestUpgradeIndexMergePolicyRetainsSoftDeletes.java](TestUpgradeIndexMergePolicyRetainsSoftDeletes.java)
- [TestUpgradeKeepsRetainedSoftDeletes.java](TestUpgradeKeepsRetainedSoftDeletes.java)
- [TestUpgradeLeftoverMergeRetention.java](TestUpgradeLeftoverMergeRetention.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
