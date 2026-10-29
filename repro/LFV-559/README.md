# MoreLikeThis with setBoost(true) divides every term's score by the lowest selected score, so a selected term that scores 0 (a TFIDFSimilarity whose idf is 0, such as the textbook log(N/df) for a term in every document) makes like throw 'boost must be a positive float, got NaN' (8.0 and later) or build NaN and Infinity boosts (4.10 to 7.x)

Bug LFV-559 · Area: queries / MoreLikeThis boosted query construction (MoreLikeThis.createQuery boost normalisation) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable boosting on MoreLikeThis (setBoost(true)) with a custom TFIDFSimilarity whose idf is 0 for a term that is in every document, as the textbook idf log(N/df) is, and call like(...) on text that contains that term and a rarer one. You expect a query with finite, non-negative boosts: the same call works with setBoost(false), and BoostQuery accepts a boost of 0. Instead createQuery divides each term's score by the first score popped from its ascending queue, which is the lowest selected score (0 here), so the zero-score term's boost is 0/0 = NaN and like throws IllegalArgumentException 'boost must be a positive float, got NaN' on 8.0 and later, or builds the query with NaN and Infinity boosts on 4.10 to 7.x.

The test shows that `MoreLikeThis` with `setBoost(true)` and a TFIDFSimilarity whose idf is 0 for a term in every document divides by that zero lowest score, so `like()` throws.

## Expected vs actual

Expected a query with finite boosts (as with setBoost(false); BoostQuery accepts 0); actual IllegalArgumentException "boost must be a positive float, got NaN" from MoreLikeThis.createQuery, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queries/mlt/TestMoreLikeThisBoostZeroScore.java](src/org/apache/lucene/queries/mlt/TestMoreLikeThisBoostZeroScore.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisBoostZeroScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/queries-mlt-commonterms-reference/](../../models/queries-mlt-commonterms-reference/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
