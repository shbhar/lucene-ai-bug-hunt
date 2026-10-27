# SoftDeletesRetentionMergePolicy's OneMerge wrapper forwards only wrapForMerge, so a reordering policy nested inside it (BPReorderingMergePolicy, or any OneMerge#reorder override) silently never reorders natural or forced merges, and BPReorderingMergePolicy's bp.reordered diagnostic is never written (since 9.9.0)

Bug LFV-509 · Area: index / merge policies (SoftDeletesRetentionMergePolicy OneMerge wrapping); misc / BP merge-time reordering (BPReorderingMergePolicy) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure IndexWriter with new SoftDeletesRetentionMergePolicy(field, query, new BPReorderingMergePolicy(base, reorderer)) to keep soft-deleted history and to reorder doc IDs on merge, then call forceMerge or let natural merges run. You expect the merged segments to be reordered, as BPReorderingMergePolicy documents for all forced merges and as the reverse nesting does on the same documents. Instead the retention policy replaces each merge with a new OneMerge that forwards only wrapForMerge, so IndexWriter's call to reorder reaches the default that returns null: the merged segment silently keeps its original doc order and carries no bp.reordered diagnostic.

## Expected vs actual

- Expected: You expect the merged segments to be reordered, as BPReorderingMergePolicy documents for all forced merges and as the reverse nesting does on the same documents.
- Actual: The retention policy replaces each merge with a new OneMerge that forwards only wrapForMerge, so IndexWriter's call to reorder reaches the default that returns null: the merged segment silently keeps its original doc order and carries no bp.reordered diagnostic.

## Files

- [src/org/apache/lucene/misc/index/TestSoftDeletesRetentionDropsReorder.java](src/org/apache/lucene/misc/index/TestSoftDeletesRetentionDropsReorder.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestSoftDeletesRetentionDropsReorder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
