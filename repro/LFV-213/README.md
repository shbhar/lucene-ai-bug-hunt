# TemporalMergePolicy.forceMergeDeletes never expunges deletes from a segment that is the only eligible one in its time window (or the odd one left after full batches)

Bug LFV-213 · Area: index / merge policy (TemporalMergePolicy forced-deletes selection, IndexWriter.forceMergeDeletes) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use TemporalMergePolicy and call IndexWriter.forceMergeDeletes while some time window holds exactly one segment whose share of deleted documents is above forceMergeDeletesPctAllowed (default 10%), for example in a single-segment index, or after forceMerge(1), which leaves one segment per window. You expect that segment to be rewritten without its deleted documents, as the policy's setForceMergeDeletesPctAllowed javadoc and MergePolicy.findForcedDeletesMerges ('expunge all deletes') describe and as TieredMergePolicy and LogMergePolicy do. Instead the policy skips every window with fewer than two eligible segments (and drops the one left over after full batches of maxThreshold), so the call schedules no merge and returns at once: a 10-document segment with 5 deletes still has maxDoc 10, and calling it again changes nothing.

## Expected vs actual

- Expected: You expect that segment to be rewritten without its deleted documents, as the policy's setForceMergeDeletesPctAllowed javadoc and MergePolicy.findForcedDeletesMerges ('expunge all deletes') describe and as TieredMergePolicy and LogMergePolicy do.
- Actual: The policy skips every window with fewer than two eligible segments (and drops the one left over after full batches of maxThreshold), so the call schedules no merge and returns at once: a 10-document segment with 5 deletes still has maxDoc 10, and calling it again changes nothing.

## Files

- [src/org/apache/lucene/index/TestTemporalMergePolicyForceMergeDeletes.java](src/org/apache/lucene/index/TestTemporalMergePolicyForceMergeDeletes.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestTemporalMergePolicyForceMergeDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
