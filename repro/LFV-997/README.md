# SortRescorer and Expression#getRescorer size their sort collector by topN instead of by the number of first-pass hits, so a topN far above the hit count (such as Integer.MAX_VALUE for 're-sort them all') throws (OutOfMemoryError on main, IllegalArgumentException in current releases) or allocates memory in proportion to topN, while QueryRescorer and DoubleValuesSourceRescorer return the hits

Bug LFV-997 · Area: search / rescoring (SortRescorer; expressions ExpressionRescorer inherits rescore) · Priority: low · Impact: wrong error · Novelty: new

Bug page: [../../bugs/LFV-997.html](../../bugs/LFV-997.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a first-pass search that returns 5 hits and re-sort them with new SortRescorer(sort).rescore(searcher, firstPass, Integer.MAX_VALUE), meaning 're-sort all of them'; you expect the 5 hits back in sort order, as topN = 5 or 10 give and as QueryRescorer and DoubleValuesSourceRescorer give for any topN. Instead the call throws OutOfMemoryError 'Requested array size exceeds VM limit' on main (IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631' in 10.x and 9.x), because SortRescorer sizes its TopFieldCollector, which pre-allocates its arrays, by topN instead of by the number of hits it was given. Smaller large values succeed but cost memory in proportion to topN (about 120 MB for 4 hits at topN = 10^7), and Expression#getRescorer inherits the same code.

The test shows that SortRescorer.rescore with topN = Integer.MAX_VALUE ('re-sort all hits') fails on 2 first-pass hits, because it sizes its TopFieldCollector by topN instead of by the hit count.

## Expected vs actual

Expected: the 2 hits come back re-sorted (as with topN = 2). Actual (-ea and -da): OutOfMemoryError 'Requested array size exceeds VM limit' at SortRescorer.rescore:49 (IllegalArgumentException in 10.x/9.x releases).

## Files

- [src/org/apache/lucene/search/TestSortRescorerMaxTopN.java](src/org/apache/lucene/search/TestSortRescorerMaxTopN.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.search.TestSortRescorerMaxTopN
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/rescorers-nearest-and-rescoretopn-presized-by-unbounded-topn/](../../models/rescorers-nearest-and-rescoretopn-presized-by-unbounded-topn/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
