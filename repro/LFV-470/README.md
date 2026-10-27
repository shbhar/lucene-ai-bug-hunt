# PayloadScoreQuery built with includeSpanScore=false ignores every positive query boost (BoostQuery, boosted BooleanQuery clauses, boosts propagated by parent queries): the score stays the bare payload function value, so the clause gets the wrong weight and ranking

Bug LFV-470 · Area: queries / payloads (PayloadScoreQuery scoring with includeSpanScore=false) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Build a PayloadScoreQuery with includeSpanScore=false and wrap it in BoostQuery(q, 3), or put it in a BooleanQuery as a clause with boost 10. You expect its scores to be multiplied by the boost, as BoostQuery promises for every query ('the produced scores will be boosted by boost'). Instead the score stays the bare payload value (4.0 instead of 12.0; 1.0 instead of 10.0), so the boosted payload hit ranks below a weaker clause; only a boost of 0 still works, because BoostQuery rewrites it into a ConstantScoreQuery.

## Expected vs actual

- Expected: You expect its scores to be multiplied by the boost, as BoostQuery promises for every query ('the produced scores will be boosted by boost').
- Actual: The score stays the bare payload value (4.0 instead of 12.0; 1.0 instead of 10.0), so the boosted payload hit ranks below a weaker clause; only a boost of 0 still works, because BoostQuery rewrites it into a ConstantScoreQuery.

## Files

- [src/org/apache/lucene/queries/payloads/TestPayloadScoreQueryBoost.java](src/org/apache/lucene/queries/payloads/TestPayloadScoreQueryBoost.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.payloads.TestPayloadScoreQueryBoost
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
