# benchmark defect

Bug LFV-940 · Area: benchmark / search-quality package (trec.TrecJudge, the qrels reader used by QualityBenchmark and QueryDriver) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You evaluate search quality with QueryDriver or QualityBenchmark and a TREC qrels file read by TrecJudge, expecting a document to count as relevant only when its grade is 1 or more, as trec_eval counts it. TrecJudge instead treats every grade token except the exact string "0" as relevant, so documents graded -2 (spam or useless, in TREC Web track qrels) or -1 count as relevant: a junk page graded -2 that ranks above the only relevant page gives 2 relevant documents, P@1 1.0 and AP 1.0, where trec_eval gives 1, 0 and 0.5. There is no error or warning.

## Expected vs actual

Expected `isRelevant("d", q) == false` (trec_eval counts a document relevant only when its grade is &gt;= 1); actual `true`, because every grade other than the literal string `0` counts as relevant, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/benchmark/quality/trec/TestTrecJudgeNegativeGrade.java](src/org/apache/lucene/benchmark/quality/trec/TestTrecJudgeNegativeGrade.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/benchmark/build/libs/lucene-benchmark-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.benchmark.quality.trec.TestTrecJudgeNegativeGrade
```

The module(s) benchmark also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
