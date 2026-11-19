# Since #16857 (main and branch_11x, both unreleased) a top-n search in index order (Sort.INDEXORDER, or any sort whose first field is _doc) without a searchAfter cursor no longer skips the later segments once the top hits and the hits threshold are reached: the query is evaluated in each later segment until a hit is collected there (for a doc-values range query: to the end of the 4,096-document window that holds the first hit), the whole segment when it has none; 10.6.0 evaluates nothing there (performance only, results equal; known upstream, #16953)

Finding LFV-1519 · Area: search / field sorting in index order (_doc): index-sort early termination (IndexSortedLeafFieldComparator) and DocComparator's competitive iterator · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call searcher.search(query, 10, Sort.INDEXORDER) on an index with two segments; the query, a doc-values range query, matches the 1,001 documents of the first segment and none of the 1,000 documents of the second. You expect the search to stop after the first segment, as Lucene 10.6.0 does: it has the first 10 documents in index order and more hits than the default 1,000-hit threshold, and no later document can come before them, so 10.6.0 reads none of the second segment's values. Instead, on main and branch_11x the search reads all 1,000 values of the second segment, because since #16857 a later segment is left only when a hit is collected in it; the top 10 are the same, only the work differs.

## Expected vs actual

- Expected: You expect the search to stop after the first segment, as Lucene 10.6.0 does: it has the first 10 documents in index order and more hits than the default 1,000-hit threshold, and no later document can come before them, so 10.6.0 reads none of the second segment's values.
- Actual: On main and branch_11x the search reads all 1,000 values of the second segment, because since #16857 a later segment is left only when a hit is collected in it; the top 10 are the same, only the work differs.

## Files

- [src/org/apache/lucene/search/TestIndexOrderSearchLaterSegmentWork.java](src/org/apache/lucene/search/TestIndexOrderSearchLaterSegmentWork.java) (JUnit test)

## Run it

- Tested on Lucene main 1cfa86310f76 (2026-11-17): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 89e68e0f3a50 (2026-11-17): reproduced, with assertions on and off.
- Tested on Lucene main 9965d34e3c77 (2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 9d2b5a15427a (2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene main 781025d6fe32 (2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene branch_11x daa54f2e0cd3 (2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (2026-10-26): the test passes, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- In no release: the test fails on Lucene main and on the 11.0 release branch (branch_11x daa54f2e0cd3), with and without assertions, and passes with the 10.6.0 release jars, so 11.0.0 would be the first release with it. It is about work, not results: the search returns the same documents on every build. Known upstream: #16953 (closed) reported the collected-hit side of it through one of Lucene's own tests. What the test does: it writes two segments, 1,001 documents that match the query and then 1,000 that do not (NoMergePolicy keeps them apart), wraps the second segment in a reader that counts every doc value read, runs search(query, 10, Sort.INDEXORDER) with a doc-values range query and expects that no value of the second segment is read, as on 10.6.0. It fails with 'values of x read in the second segment expected:&lt;0&gt; but was:&lt;1000&gt;'. The test's input has no randomness; the test framework draws the default codec from the seed, and with three other seeds the test failed the same way on branch_11x and passed with the 10.6.0 release jars. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put lucene-core, lucene-codecs and lucene-test-framework 10.6.0 from Maven Central (org.apache.lucene) in place of the three $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/core.
- This finding was re-run on Lucene main ec3b3cca8f10 on 2026-11-18: still reproduces.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestIndexOrderSearchLaterSegmentWork
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
