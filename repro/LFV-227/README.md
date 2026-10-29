# JoinUtil's single-valued term join forgets a from-document that scored exactly 0, so ScoreMode.Min and ScoreMode.Avg give too-high, order-dependent scores

Bug LFV-227 · Area: join / JoinUtil query-time term join (TermsWithScoreCollector.SV and SV.Avg, ScoreMode Min/Avg) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-227.html](../../bugs/LFV-227.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build JoinUtil.createJoinQuery("join", false, "ref", fromQuery, searcher, ScoreMode.Min) where two from-documents share the join value x and fromQuery scores them 0 and 2 (one matches only a FILTER clause), and expect the to-document with ref x to score min(0, 2) = 0, or (0 + 2) / 2 = 1 with ScoreMode.Avg, because the ScoreMode javadoc says 'the min of all child scores' and 'the average of all child scores' and the multi-valued join returns exactly that on the same documents. Instead both modes return 2: the single-valued collector uses an accumulated score of exactly 0 to mean 'this join value has not been seen yet', so after a from-document that scored 0, the next from-document with the same value overwrites the stored minimum (Min) or restarts the sum and count (Avg). Which documents match does not change; only scores and ranking are wrong, and they depend on collection order (scores [0, 2] go wrong, [2, 0] come out right).

The test shows that `JoinUtil.createJoinQuery(..., multipleValuesPerDocument=false,..., ScoreMode.Min)` forgets a from-document that scored exactly 0: the single-valued collector treats a stored score of 0 as "join value not seen yet", so the next from-document with the same join value overwrites the minimum.

## Expected vs actual

Expected for two from-documents with join value x scoring 0 then 2 (the 0 is a FILTER-only match): the to-document scores min(0, 2) = 0.0; actual: `expected:<0.0> but was:<2.0>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/join/TestJoinMinZeroScore.java](src/org/apache/lucene/search/join/TestJoinMinZeroScore.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestJoinMinZeroScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
