# SortRescorer and ExpressionRescorer give returned hits another hit's first-pass score when topN is smaller than the number of first-pass hits (AssertionError with -ea)

Bug LFV-424 · Area: search / rescoring (SortRescorer; expressions ExpressionRescorer inherits rescore) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-424.html](../../bugs/LFV-424.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a search, take its top hits and call new SortRescorer(sort).rescore(searcher, hits, topN), or Expression#getRescorer(bindings).rescore, with topN smaller than the number of hits, for example to re-sort the best 1,000 hits by popularity and keep the top 100 as the search package docs suggest. You expect the top N hits in sort order, each with its own first-pass score, which is what every release up to 7.7.3 returned and what you still get when topN is at least the number of hits. Instead the documents and their order are right, but the k-th smallest doc id among the returned hits gets the score of the k-th smallest doc id among all first-pass hits, so nearly every returned hit carries another hit's score (with 2 hits and topN=1, doc 1 comes back with doc 0's score), and with assertions enabled rescore throws AssertionError at SortRescorer.java:86.

`SortRescorer.rescore(searcher, hits, topN)` with topN (1) smaller than the number of hits (2) gives the returned hit another hit's first-pass score.

## Expected vs actual

Expected: doc 1 keeps its own score 1.0. Actual: doc 0's score 2.0 with `-da`; with `-ea` an AssertionError at SortRescorer.java:86.

## Files

- [src/org/apache/lucene/search/TestSortRescorerTopNScore.java](src/org/apache/lucene/search/TestSortRescorerTopNScore.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestSortRescorerTopNScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
