# WANDScorer mishandles a minimum competitive score of +Infinity: an assertion fails with -ea, and with -da it prunes documents that score +Infinity

Bug LFV-113 · Area: search / top-k dynamic pruning (WANDScorer) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a top-k search whose top-level disjunction runs on WANDScorer, for example three FeatureField linear SHOULD clauses with minimumNumberShouldMatch 2, over documents whose finite clause scores add up past Float.MAX_VALUE, so that they score +Infinity. You expect the same documents as an exhaustive (COMPLETE) search: hits tied at +Infinity stay competitive and are ordered by doc id (or by the next sort field). Instead, once the collector raises the minimum competitive score to +Infinity, WANDScorer.scaleMinScore fails its 'Float.isFinite(minScore)' assertion with -ea (even in a single-threaded search), and with -da it saturates the bound to Long.MAX_VALUE and prunes every remaining document, so a concurrent search with the default slicing returns docs 23, 24, 25 instead of 0, 1, 2, all scoring +Infinity.

The test shows that a single-threaded WAND disjunction (minimumNumberShouldMatch 2) sorted by (score, rank), where every hit scores +Infinity: WANDScorer mishandles setMinCompetitiveScore(+Infinity).

## Expected vs actual

Expected top-3 docs 9, 8, 7 (rank breaks the tie); actual with -da: docs 3, 2, 1; with -ea: AssertionError at WANDScorer.scaleMinScore:112.

## Files

- [src/org/apache/lucene/search/TestWANDInfiniteMinCompetitiveScore.java](src/org/apache/lucene/search/TestWANDInfiniteMinCompetitiveScore.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestWANDInfiniteMinCompetitiveScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/wand-blockmax-bounds/](../../models/wand-blockmax-bounds/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
