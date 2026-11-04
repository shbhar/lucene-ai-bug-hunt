# IndriDisjunctionScorer.getMaxScore always returns 0 although IndriAndScorer scores the weighted mean of its IndriScorer clauses, so when a clause scores above 0, top-k search prunes the IndriAndQuery and returns the wrong top hit

Finding LFV-1144 · Area: search / IndriAndQuery (IndriDisjunctionScorer.getMaxScore, the max-score bound that top-k pruning reads) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1144.html](../../bugs/LFV-1144.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put an IndriAndQuery whose two clauses are your own IndriScorer-based queries (each scores 100 on the last of 10,000 documents and 0 elsewhere, and reports a max score of 100) into a BooleanQuery as a SHOULD clause next to MatchAllDocsQuery, and ask IndexSearcher.search(q, 1) for the best hit; you expect the last document, which an exhaustive search ranks first with score 101.0, because Scorer.getMaxScore must bound every score. Instead top-k search returns doc 0 (score 1.0), with no error, under -ea and -da. IndriDisjunctionScorer.getMaxScore, which IndriAndScorer inherits, always returns 0 while IndriAndScorer scores the boost-weighted mean of its IndriScorer clauses (100 here), so once the minimum competitive score is above the summed max-score bounds of the two BooleanQuery clauses (0 + 1.0), MaxScoreBulkScorer skips the rest of the segment unscored, the last document included. An earlier 20,000-document repro (one clause scoring 100 on the last document, the other 0, next to the term query f:t) fails the same way: doc 0 (score 1.1e-5) instead of the last document (score 50.00001).

## Expected vs actual

- Expected: `search(q, 1)` returns doc 9999 (score 101.0), the exhaustive best hit.
- Actual: It returns doc 0 (score 1.0): `AssertionError: expected:<9999> but was:<0>`, assertions on and off.

## Files

- [src/org/apache/lucene/search/TestIndriAndQueryMaxScore.java](src/org/apache/lucene/search/TestIndriAndQueryMaxScore.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestIndriAndQueryMaxScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestIndriAndQueryMaxScore.java](TestIndriAndQueryMaxScore.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
