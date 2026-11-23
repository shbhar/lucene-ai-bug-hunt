# A scored search or explain() of a span query built around a FieldMaskingSpanQuery clause can throw NullPointerException when no document of the index has an indexed term in the masking field while a masked term exists (the search threw in the 61 releases that were run from 8.0.0 to 10.6.0 and on branch_11x 3221dd4cf3a1; known upstream: fixed by #17018 on main and branch_10_6, not on branch_11x when read on 2026-11-21)

Finding LFV-1575 · Area: queries / span queries (SpanWeight.buildSimWeight: the field statistics of the query's own field, met through FieldMaskingSpanQuery clauses) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

Fixed upstream: [#17018](https://github.com/apache/lucene/pull/17018) (commit [1e7e6bcce3ee](https://github.com/apache/lucene/commit/1e7e6bcce3ee120e7538ee35636c36cab0cfeaf1)), merged 2026-11-20; also on the 10.6 branch ([451a69c1d4a6](https://github.com/apache/lucene/commit/451a69c1d4a6)). The defect is in the releases 8.0.0 to 10.6.0 and, as read on 2026-11-21, on the 11.0 branch, which did not have the fix that day; main already had the fix when the finding was listed.

## What it shows

You index one document with the term james in the field first and search with a SpanOrQuery around that term wrapped in a FieldMaskingSpanQuery whose masking field, other, is a field in which no document has an indexed term; IndexSearcher.count returns 1 for the query, so you expect IndexSearcher.search(query, 10) to return that document. Instead the search throws NullPointerException from BM25Similarity (IndexSearcher.explain threw the same way in the runs): the span weight asks for the statistics of the query's own field, which for this query is the masking field, gets the null that IndexSearcher documents for a field without indexed terms, and passes it to the Similarity. It throws on the 10.6.0 release and on branch_11x 3221dd4cf3a1 (unreleased), with and without assertions; main returns the document since upstream's fix #17018, which is also on branch_10_6 and was not on branch_11x when read on 2026-11-21.

## Expected vs actual

- Expected: You expect IndexSearcher.search(query, 10) to return that document.
- Actual: The search throws NullPointerException from BM25Similarity (IndexSearcher.explain threw the same way in the runs): the span weight asks for the statistics of the query's own field, which for this query is the masking field, gets the null that IndexSearcher documents for a field without indexed terms, and passes it to the Similarity.

## Files

- [src/TestMaskedSpanScoredSearchOnFieldWithoutTerms.java](src/TestMaskedSpanScoredSearchOnFieldWithoutTerms.java) (JUnit test)

## Run it

- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Tested on Lucene branch_11x 3221dd4cf3a1 (a commit of 2026-11-20): reproduced, with assertions on and off (11.0.0-SNAPSHOT): this commit, the 11.0 release branch as read on 2026-11-21, does not have the fix #17018.
- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): the test passes, with assertions on and off (12.0.0-SNAPSHOT): main has the fix #17018, so this run is the control.
- This finding is known upstream and fixed on main: another person's pull request #17018 (https://github.com/apache/lucene/pull/17018, merged 2026-11-20 as 1e7e6bcce3ee) describes the defect and fixes it, and the fix is also on branch_10_6 (451a69c1d4a6). No release has the fix, and the 11.0 release branch (branch_11x) did not have it at 3221dd4cf3a1, its commit when read on 2026-11-21. The test fails with the jars of each of the 61 releases from 8.0.0 to 10.6.0 that were run (the releases before 9.0.0 with a copy of the test for the package the span queries had then) and on a build of branch_11x 3221dd4cf3a1; it passes on main from the fix on, so a run on main is the control. The commands below run the test first on the 10.6.0 release jars, where it fails, and then on a build of main, where it passes. For the first run put lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar, lucene-test-framework-10.6.0.jar and lucene-queries-10.6.0.jar (Maven Central, org.apache.lucene) into your copy of this folder, next to the four test jars that the commands name; for another release set V (10.5.2, 9.12.3 and 9.0.0 were run the same way and fail the same way). To see the failure on the 11.0 release branch as it was on 2026-11-21, build a checkout of branch_11x at 3221dd4cf3a1 and run the second part with that checkout and V=11.0.0-SNAPSHOT: the test fails there with the same exception, whose message names FieldStats and "fieldStats" there. For a run with assertions put -ea -esa in place of -da -dsa -Dtests.asserts=false: the outcomes are the same. What the test does: it indexes one document with the term james in the field first and searches a SpanOrQuery around that term wrapped in a FieldMaskingSpanQuery whose masking field, other, is a field in which no document has an indexed term; it expects the one document as the hit. Its input has no randomness. For the Gradle alternative at the end of this page the module is lucene/queries: run in a checkout of branch_11x at 3221dd4cf3a1 it reports 1 test completed, 1 failed, with -Ptests.asserts=true and with -Ptests.asserts=false, and in a checkout of main (run at 7b8418faa2ff) the test passes.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: the test passed, as recorded before (a control run).
- How to read the outcome: on the 10.6.0 release jars a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.NullPointerException: Cannot invoke "org.apache.lucene.search.CollectionStatistics.docCount()" because "collectionStats" is null, thrown in BM25Similarity.idfExplain under SpanWeight.buildSimWeight; on a build of main the same test passes (1 test successful): main has the fix, and that run is the control.

```bash
# needs JDK 25 on the PATH; run in a copy of this folder that also holds the jars named below (Maven Central)
V=10.6.0   # the release under test
T=randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies
T=$T:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher
CP=lucene-core-$V.jar:lucene-codecs-$V.jar:lucene-test-framework-$V.jar:lucene-queries-$V.jar:$T   # org.apache.lucene, release $V

javac -d classes -cp "$CP" $(find src -name '*.java')

# the finding: on the release jars the test fails (1 test failed, NullPointerException)
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestMaskedSpanScoredSearchOnFieldWithoutTerms

# the control: a Lucene checkout of main, which has the fix, built with: ./gradlew --no-daemon assemble
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar:$T

javac -d classes-main -cp "$CP" $(find src -name '*.java')

# there the same test passes (1 test successful)
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED \
     -cp "classes-main:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestMaskedSpanScoredSearchOnFieldWithoutTerms
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
