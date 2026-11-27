# A scored search or explain() of a span query built around a FieldMaskingSpanQuery clause can throw NullPointerException when no document of the index has an indexed term in the masking field while a masked term exists (the search threw in the 61 releases that were run from 8.0.0 to 10.6.0; known upstream: fixed by #17018)

Finding LFV-1575 · Area: queries / span queries (SpanWeight.buildSimWeight: the field statistics of the query's own field, met through FieldMaskingSpanQuery clauses) · Priority: low · Impact: crash · Novelty: known upstream

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1575.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1575.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

Fixed upstream: [#17018](https://github.com/apache/lucene/pull/17018) (commit [1e7e6bcce3ee](https://github.com/apache/lucene/commit/1e7e6bcce3ee120e7538ee35636c36cab0cfeaf1)), merged 2026-11-20; also on branch_11x ([55ef56a69344](https://github.com/apache/lucene/commit/55ef56a69344)) and branch_10_6 ([451a69c1d4a6](https://github.com/apache/lucene/commit/451a69c1d4a6)). The defect is in the 61 releases from 8.0.0 to 10.6.0 that were run; main already had the fix when the finding was listed. The cherry-pick 55ef56a69344 was committed on 2026-11-25; a build of the commit before it failed the test that day.

## What it shows

You index one document with the term james in the field first and search with a SpanOrQuery around that term wrapped in a FieldMaskingSpanQuery whose masking field, other, is a field in which no document has an indexed term; IndexSearcher.count returns 1 for the query, so you expect IndexSearcher.search(query, 10) to return that document. Instead the search throws NullPointerException from BM25Similarity (IndexSearcher.explain threw the same way in the runs): the span weight asks for the statistics of the query's own field, which for this query is the masking field, gets the null that IndexSearcher documents for a field without indexed terms, and passes it to the Similarity. It throws on the 10.6.0 release, with and without assertions; main returns the document since upstream's fix #17018, which was also cherry-picked to branch_10_6 (451a69c1d4a6, 2026-11-20) and to branch_11x (55ef56a69344, 2026-11-25).

## Expected vs actual

- Expected: `IndexSearcher.search(query, 10)` returns the one document (`count(query)` is 1, in a control run beside the test).
- Actual: On 10.6.0 the search throws `java.lang.NullPointerException: Cannot invoke "org.apache.lucene.search.CollectionStatistics.docCount()" because "collectionStats" is null`, in `BM25Similarity.idfExplain` under `SpanWeight.buildSimWeight`, with assertions on and off.

## Files

- [src/TestMaskedSpanScoredSearchOnFieldWithoutTerms.java](src/TestMaskedSpanScoredSearchOnFieldWithoutTerms.java) (JUnit test)

## Run it

- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Tested on Lucene branch_11x 3221dd4cf3a1 (a commit of 2026-11-20): reproduced, with assertions on and off (11.0.0-SNAPSHOT), when run on 2026-11-21: this commit of branch_11x does not have the fix #17018.
- Tested on Lucene branch_11x 691f4adb27f4 (a commit of 2026-11-25): reproduced, with assertions on and off (11.0.0-SNAPSHOT), when run on 2026-11-25: this commit of branch_11x is the commit before the cherry-pick of the fix #17018.
- Tested on Lucene branch_11x 55ef56a69344 (a commit of 2026-11-25): the test passes, with assertions on and off (11.0.0-SNAPSHOT), when run on 2026-11-25: this commit of branch_11x is the cherry-pick of the fix #17018, so this run is a control.
- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): the test passes, with assertions on and off (12.0.0-SNAPSHOT): main has the fix #17018, so this run is the control.
- Tested on Lucene main 8506598d7a98 (a commit of 2026-11-25): the test passes, with assertions on and off (12.0.0-SNAPSHOT), when run on 2026-11-25: main has the fix #17018, so this run is a control.
- This finding is known upstream and fixed there: another person's pull request #17018 (https://github.com/apache/lucene/pull/17018, merged 2026-11-20 as 1e7e6bcce3ee) describes the defect and fixes it. The fix is also on branch_10_6 (451a69c1d4a6, 2026-11-20) and, since the cherry-pick 55ef56a69344 of 2026-11-25, on branch_11x; no release has it when read on 2026-11-25. Not new in 11.0: the test fails with the 10.6.0 release jars as it failed on builds of branch_11x before that cherry-pick. The test fails with the jars of each of the 61 releases from 8.0.0 to 10.6.0 that were run (the releases before 9.0.0 with a copy of the test for the package the span queries had then) and on builds of branch_11x before the cherry-pick (3221dd4cf3a1, run on 2026-11-21; 691f4adb27f4, the commit before the cherry-pick, run on 2026-11-25); it passes on main from the fix on and on the build of branch_11x at the cherry-pick, so runs on those builds are the controls. The commands below run the test first on the 10.6.0 release jars, where it fails, and then on a build of main, where it passes. For the first run put lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar, lucene-test-framework-10.6.0.jar and lucene-queries-10.6.0.jar (Maven Central, org.apache.lucene) into your copy of this folder, next to the four test jars that the commands name; for another release set V (10.5.2, 9.12.3 and 9.0.0 were run the same way and fail the same way). A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there: with a checkout of branch_11x at 55ef56a69344 the second part passes too. To see the failure on a build of the branch, build a checkout of branch_11x at 691f4adb27f4, the commit before the cherry-pick, and run the second part with that checkout and V=11.0.0-SNAPSHOT: the test fails there with the same exception, whose message names FieldStats and "fieldStats" there. For a run with assertions put -ea -esa in place of -da -dsa -Dtests.asserts=false: the outcomes are the same. What the test does: it indexes one document with the term james in the field first and searches a SpanOrQuery around that term wrapped in a FieldMaskingSpanQuery whose masking field, other, is a field in which no document has an indexed term; it expects the one document as the hit. Its input has no randomness. For the Gradle alternative at the end of this page the module is lucene/queries: run on 2026-11-25 in a checkout of branch_11x at 691f4adb27f4 it reports 1 test completed, 1 failed, with -Ptests.asserts=true and with -Ptests.asserts=false, and in checkouts of branch_11x at 55ef56a69344 and of main at 8506598d7a98 the test passes.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: the test passed, as recorded before (a control run).
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestMaskedSpanScoredSearchOnFieldWithoutTerms.java](TestMaskedSpanScoredSearchOnFieldWithoutTerms.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
