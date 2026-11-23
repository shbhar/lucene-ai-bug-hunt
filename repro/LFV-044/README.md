# Doc-values range queries with a skip index can return wrong hits when a segment's values span more than Long.MAX_VALUE

Finding LFV-044 · Area: codecs / doc values · Priority: high · Impact: silent wrong results · Novelty: known upstream

Finding page: [../../bugs/LFV-044.html](../../bugs/LFV-044.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fixed, by kkewwei (contributor; PR #16896, approved by costin on 2026-11-11, merged and cherry-picked to branch_11x by Alan Woodward (romseygeek) on 2026-11-23) on 2026-11-23 (https://github.com/apache/lucene/pull/16896). kkewwei fixed it in PR #16896 (opened 2026-10-23): the query bounds are moved into the stored domain only when that cannot overflow, and otherwise the range is compared with decoded values; the two saturating helpers are gone. costin approved the PR on 2026-11-11; Alan Woodward merged it into main on 2026-11-23 as 3cee9ed259e (no milestone) and cherry-picked it to branch_11x the same day (8e4ee3d4138). Not on branch_10_6 (head 451a69c1d4a, 2026-11-20); issue #17042 was open when read on 2026-11-23. From our runs of 2026-11-23, each with and without assertions: the repro and the 11 study tests pass on main 3cee9ed259e9 and on branch_11x 8e4ee3d41386; on the commits before them and on the 10.6.0 release the repro and 10 of the 11 fail. This site files nothing upstream.

Fixed upstream: [#16896](https://github.com/apache/lucene/pull/16896) (commit [3cee9ed259e9](https://github.com/apache/lucene/commit/3cee9ed259e9d932b0fa64cb0ec12f4c1e76ef6b)), merged 2026-11-23; also on the 11.0 release branch ([8e4ee3d41386](https://github.com/apache/lucene/commit/8e4ee3d41386)).

## What it shows

You index numeric doc values with a skip index (NumericDocValuesField.indexedField) where one segment's values span more than Long.MAX_VALUE, and run a doc-values range query. You expect the same hits as 10.5.2. Instead the bulk range check compares the stored values, some of which have wrapped to negative longs, with the shifted query bounds: 100k random latitudes queried for [45, 90] gave 0 hits instead of 25,157, 64-bit hashes gave 2 instead of 49,931, and some cases add wrong hits.

## Expected vs actual

- Expected: `count(q)` = 512 and `search(q, 10)` returns 10 hits, as on 10.5.2 and on the same data without a skip index.
- Actual: `count(q)` = 0 and `search(q, 10)` returns 0 hits: `AssertionError: count(q); search(q, 10) returned 0 hits expected:<512> but was:<0>`.

## Files

- [src/TestVerifyEncodedRange.java](src/TestVerifyEncodedRange.java) (JUnit test)
- [src/TestVerifyDefaultSearch.java](src/TestVerifyDefaultSearch.java) (JUnit test)

## Run it

- Tested on Lucene main b83101cdd2 (a commit of 2026-10-23): reproduced.
- On a build with upstream's fix these tests pass: #16896 (https://github.com/apache/lucene/pull/16896) was merged into main on 2026-11-23 as 3cee9ed259e9 and is on branch_11x as 8e4ee3d41386, and on builds of these two commits the 11 tests of the two classes pass, with and without assertions; on the commits before them (main 951e199fbae7, branch_11x 262e019500ec) 10 of the 11 fail (runs of 2026-11-23, each with the one source change named below). To see the finding, run the commands on a build that has it. As printed (V=11.0.0-SNAPSHOT) they fit the tested commit above; run as printed on a build of main 645d4fe14a20 on 2026-11-23, 10 of the 11 tests fail. Without a build: put lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar and lucene-test-framework-10.6.0.jar (Maven Central, org.apache.lucene) in place of the three $LUCENE jars and set V=10.6.0; the 10.6.0 release fails the same 10 tests, with and without assertions. The eleventh test, testControlNoOverflow, is a control and passed on every build that was run. The source change: on a build of main from 24d9e7d1a0d on or of branch_11x from a6a26f9b251 on (the four commits named first are such builds), replace new MatchAllDocsQuery() with MatchAllDocsQuery.INSTANCE in src/TestVerifyDefaultSearch.java before you compile, because #16962 made the no-argument constructor private there. Builds of main 3cee9ed259e9 and 951e199fbae7 have 12.0.0-SNAPSHOT jars (set V=12.0.0-SNAPSHOT); builds of branch_11x 8e4ee3d41386 and 262e019500ec have 11.0.0-SNAPSHOT jars. The expected and actual lines above are those of the finding page's one-test copy at the top of this folder, TestSkipIndexedDocValuesRangeWideSpan.java; to run it as well, add that file to the javac line and --select-class org.apache.lucene.search.TestSkipIndexedDocValuesRangeWideSpan to the java line (12 tests then: 11 fail on the 10.6.0 release jars, all 12 pass on a build of main 3cee9ed259e9).
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: it no longer reproduces there; in the commands below, V=12.0.0-SNAPSHOT on a build of main 11c8b6ea99ae.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
     execute --select-class TestVerifyEncodedRange \
       --select-class TestVerifyDefaultSearch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSkipIndexedDocValuesRangeWideSpan.java](TestSkipIndexedDocValuesRangeWideSpan.java)

Formal models and scripts this finding's part of the hunt used: [../../models/study-simd-equivalence/](../../models/study-simd-equivalence/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
