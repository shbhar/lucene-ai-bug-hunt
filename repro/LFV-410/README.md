# JoinUtil numeric score joins (PointInSetIncludingScoreQuery, ScoreMode Max, Min, Avg or Total) ignore the boost, so BoostQuery, boosted BooleanQuery clauses and BooleanQuery's folding of duplicate clauses leave their scores unchanged; the part of GITHUB#12388 that was left out

Bug LFV-410 · Area: join / JoinUtil numeric score join (PointInSetIncludingScoreQuery createWeight boost) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a numeric query-time join with JoinUtil.createJoinQuery("fromId", false, "toId", Integer.class, fromQuery, searcher, ScoreMode.Max) (or Min, Avg, Total), wrap it in BoostQuery(join, 10), and expect each to-document's score to be 10 times its join score, as BoostQuery documents ('the produced scores will be boosted by boost'), as the string-field join over the same documents does, and as GITHUB#12388 ('JoinUtil queries were ignoring boosts', 9.8.0) made every other JoinUtil join do. Instead the PointInSetIncludingScoreQuery that JoinUtil returns never reads the boost passed to createWeight and its scorer returns the raw join score, so BoostQuery(join, 10) scores 0.13076457 instead of 1.3076458, and BooleanQuery[SHOULD join, SHOULD join], which Lucene rewrites to one clause with boost 2, scores 1x instead of 2x. Only scores and ranking are wrong: the matching documents are correct.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The PointInSetIncludingScoreQuery that JoinUtil returns never reads the boost passed to createWeight and its scorer returns the raw join score, so BoostQuery(join, 10) scores 0.13076457 instead of 1.3076458, and BooleanQuery[SHOULD join, SHOULD join], which Lucene rewrites to one clause with boost 2, scores 1x instead of 2x.

## Files

- [src/org/apache/lucene/search/join/TestNumericScoreJoinBoost.java](src/org/apache/lucene/search/join/TestNumericScoreJoinBoost.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestNumericScoreJoinBoost
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
