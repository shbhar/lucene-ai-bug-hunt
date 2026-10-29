# BayesianScoreQuery turns the minimum competitive probability into an inner-score threshold that lies inside the range of inner scores with exactly that probability, so default top-k search can skip a hit that is still competitive

Bug LFV-450 · Area: search / top-k dynamic pruning (BayesianScoreQuery: BayesianScoreScorer#setMinCompetitiveScore) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run an ordinary top-k search such as searcher.search(query, 10) with a BayesianScoreQuery, and more than 1,000 documents match. You expect the same top hits as an exhaustive search, because the probability is a monotone function of the inner score and pruning may skip only documents that score below the current k-th hit. Instead, when a later document's probability is exactly one float step above the k-th hit's (most often when probabilities are close to 1), BayesianScoreQuery turns that probability back into an inner-score threshold with a closed-form inverse that lands in the middle of the range of inner scores that all round to that probability, so the inner query skips the document and the search silently returns the weaker hit.

The test shows that `BayesianScoreQuery` turns the minimum competitive probability into an inner-score threshold that skips a still-competitive hit, so the default `search(q, 1)` returns the wrong top hit after 1,000 matches.

## Expected vs actual

Expected doc 1001 (inner 5.0, p=0.9933072); actual doc 1000 (inner 4.999999, p=0.9933071): `score=0.9933071 expected:<1001> but was:<1000>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestBayesianMinCompetitiveTies.java](src/org/apache/lucene/search/TestBayesianMinCompetitiveTies.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestBayesianMinCompetitiveTies
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
