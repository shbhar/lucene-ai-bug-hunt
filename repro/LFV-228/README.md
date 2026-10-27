# SoftDeletesRetentionMergePolicy.numDeletesToMerge counts a soft-deleted-then-hard-deleted document as retained although merges drop it, so merge selection under-counts reclaimable deletes and forceMergeDeletes can leave the hard-deleted document in place

Bug LFV-228 · Area: index / merge policy (SoftDeletesRetentionMergePolicy.numDeletesToMerge, soft deletes) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable soft deletes with SoftDeletesRetentionMergePolicy and a retention query that keeps soft-deleted documents, soft-delete a document (it is kept as history), commit, and later hard-delete the same document with deleteDocuments. You expect what MergePolicy.numDeletesToMerge promises, 'the number of deletes that a merge would claim', and since LUCENE-8381 IndexWriter does drop such a hard-deleted document in a merge even though the retention query matches it. Instead the policy subtracts every deleted document that has the soft-delete field and matches the retention query, hard-deleted or not, so in the repro it reports 0 where forceMerge(1) drops 1, and forceMergeDeletes leaves a segment with 1 of its 4 documents hard-deleted (25%, above TieredMergePolicy's 10% threshold) untouched.

## Expected vs actual

- Expected: You expect what MergePolicy.numDeletesToMerge promises, 'the number of deletes that a merge would claim', and since LUCENE-8381 IndexWriter does drop such a hard-deleted document in a merge even though the retention query matches it.
- Actual: The policy subtracts every deleted document that has the soft-delete field and matches the retention query, hard-deleted or not, so in the repro it reports 0 where forceMerge(1) drops 1, and forceMergeDeletes leaves a segment with 1 of its 4 documents hard-deleted (25%, above TieredMergePolicy's 10% threshold) untouched.

## Files

- [src/org/apache/lucene/index/TestSoftDeletesRetentionNumDeletesToMerge.java](src/org/apache/lucene/index/TestSoftDeletesRetentionNumDeletesToMerge.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestSoftDeletesRetentionNumDeletesToMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
