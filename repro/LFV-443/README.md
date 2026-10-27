# LogOddsFusionQuery with logit bounds but no weights normalizes every clause's logit with the first clause's bounds, so scores disagree with explain and can exceed getMaxScore

Bug LFV-443 · Area: search / score fusion (LogOddsFusionQuery: LogOddsFusionScorer#score with per-signal logit bounds and null weights) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You combine two probability clauses with LogOddsFusionQuery(clauses, 0.5f, null, logitMin, logitMax), giving each clause its own logit bounds and no weights (null means uniform weighting). You expect each clause's logit to be normalized with its own bounds, as the constructor javadoc ('per-signal' bounds), explain and getMaxScore all do. Instead score normalizes every clause with the first clause's bounds: in the repro a document that matches only the second clause (p = 0.6, bounds [-1, 1]) scores 0.50071675 while explain reports the documented 0.6217322, and with the bounds the other way round a document that matches both clauses scores 0.76923805, above the scorer's own getMaxScore of 0.6703954.

## Expected vs actual

- Expected: You expect each clause's logit to be normalized with its own bounds, as the constructor javadoc ('per-signal' bounds), explain and getMaxScore all do.
- Actual: score normalizes every clause with the first clause's bounds: in the repro a document that matches only the second clause (p = 0.6, bounds [-1, 1]) scores 0.50071675 while explain reports the documented 0.6217322, and with the bounds the other way round a document that matches both clauses scores 0.76923805, above the scorer's own getMaxScore of 0.6703954.

## Files

- [src/org/apache/lucene/search/TestLogOddsFusionUnweightedLogitBounds.java](src/org/apache/lucene/search/TestLogOddsFusionUnweightedLogitBounds.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestLogOddsFusionUnweightedLogitBounds
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
