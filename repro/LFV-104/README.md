# ScorerUtil.minRequiredScore returns NaN when the minimum competitive score is +Infinity, so block-max conjunctions and MaxScore disjunctions drop documents that score +Infinity

Bug LFV-104 · Area: search / top-k dynamic pruning (block-max conjunction and MaxScore bulk scorers) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run an ordinary top-k search, for example IndexSearcher#search(query, 1), with a two-clause conjunction or disjunction whose scores can overflow to +Infinity (two FeatureField linear queries over very large feature values, or two clauses with very large finite boosts). You expect the best documents first, +Infinity before Float.MAX_VALUE and ties by ascending doc id, as Lucene 10.2.2 returns them. Instead, once the minimum competitive score reaches +Infinity, ScorerUtil.minRequiredScore returns NaN and filterCompetitiveHits discards every remaining candidate, so the search silently returns doc 0 scoring Float.MAX_VALUE instead of doc 3000 scoring +Infinity (one thread, one segment), or docs 100, 101, 102 instead of the equally scored docs 0, 1, 2 (a concurrent search in which the slice with the higher doc ids finishes first).

## Expected vs actual

- Expected: You expect the best documents first, +Infinity before Float.MAX_VALUE and ties by ascending doc id, as Lucene 10.2.2 returns them.
- Actual: Once the minimum competitive score reaches +Infinity, ScorerUtil.minRequiredScore returns NaN and filterCompetitiveHits discards every remaining candidate, so the search silently returns doc 0 scoring Float.MAX_VALUE instead of doc 3000 scoring +Infinity (one thread, one segment), or docs 100, 101, 102 instead of the equally scored docs 0, 1, 2 (a concurrent search in which the slice with the higher doc ids finishes first).

## Files

- [src/org/apache/lucene/search/TestMinRequiredScoreInfinite.java](src/org/apache/lucene/search/TestMinRequiredScoreInfinite.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestMinRequiredScoreInfinite
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/wand-blockmax-bounds/](../../models/wand-blockmax-bounds/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
