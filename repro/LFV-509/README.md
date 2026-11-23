# SoftDeletesRetentionMergePolicy's OneMerge wrapper forwards only wrapForMerge, so a reordering policy nested inside it (BPReorderingMergePolicy, or any OneMerge#reorder override) silently never reorders natural or forced merges, and BPReorderingMergePolicy's bp.reordered diagnostic is never written (since 9.9.0)

Finding LFV-509 · Area: index / merge policies (SoftDeletesRetentionMergePolicy OneMerge wrapping); misc / BP merge-time reordering (BPReorderingMergePolicy) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Since #16571 (main 354165bd7d9, 2026-11-12; also on branch_11x) only the missing bp.reordered diagnostic is left of this defect; releases 9.9.0 to 10.6.0 have all of it. On those releases you configure IndexWriter with new SoftDeletesRetentionMergePolicy(field, query, new BPReorderingMergePolicy(base, reorderer)) to keep soft-deleted history and to reorder doc IDs on merge, then call forceMerge or let natural merges run. You expect the merged segments to be reordered, as BPReorderingMergePolicy documents for all forced merges and as the reverse nesting does on the same documents. Instead the retention policy replaces each merge with a new OneMerge that forwards only wrapForMerge, so IndexWriter's call to reorder() reaches the default that returns null: the merged segment silently keeps its original doc order and carries no bp.reordered diagnostic (on main since #16571, and on branch_11x, the merge is reordered again and only the diagnostic is still missing).

## Expected vs actual

- Expected: You expect the merged segments to be reordered, as BPReorderingMergePolicy documents for all forced merges and as the reverse nesting does on the same documents.
- Actual: The retention policy replaces each merge with a new OneMerge that forwards only wrapForMerge, so IndexWriter's call to reorder() reaches the default that returns null: the merged segment silently keeps its original doc order and carries no bp.reordered diagnostic (on main since #16571, and on branch_11x, the merge is reordered again and only the diagnostic is still missing).

## Files

- [src/org/apache/lucene/misc/index/TestSoftDeletesRetentionDropsReorder.java](src/org/apache/lucene/misc/index/TestSoftDeletesRetentionDropsReorder.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): still fails 2 of 3, with and without assertions, in the narrower form the next line describes (run with the test adapted as that line says).
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): still fails 2 of 3, with and without assertions, but narrower than on the releases: since 354165bd7d9 (#16571) the forced merge is reordered again, and only the bp.reordered diagnostic and the setMergeInfo, mergeInit and mergeFinished calls on the wrapped merge are still missing (run with the test adapted to #16962, which made the no-argument MatchAllDocsQuery constructor private on main (24d9e7d1a0d) and on the 11.0 branch (branch_11x, a6a26f9b251): three lines pass a lambda returning MatchAllDocsQuery.INSTANCE instead of MatchAllDocsQuery::new).
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- This finding was re-run on Lucene main b5a4546a5964 on 2026-11-22: still reproduces (the repro adapted to an API change on this commit).
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
