# SubmissionReport writes scores of 1000 or more with a grouping comma ("1,200.5000") into the TREC run file that QueryDriver documents as the submission file for trec_eval, so trec_eval reads such a score as 1.0 and ranks those hits wrongly

Bug LFV-937 · Area: benchmark / quality (utils.SubmissionReport, the TREC run-file writer used by QualityBenchmark and QueryDriver) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You evaluate search quality with lucene/benchmark's SubmissionReport, for example through QueryDriver, whose usage text calls the output the 'submission file for trec_eval', and expect the score column of each TREC run line (qid Q0 docno rank score tag) to be a plain number. SubmissionReport formats scores with NumberFormat.getInstance(Locale.ROOT) and never turns grouping off, so a hit scoring 1200.5 is written as '1,200.5000'. trec_eval reads that column with C atof, which stops at the comma and returns 1.0, and then re-sorts the run by the parsed score, so a run [1200.5, 900.25] is evaluated as [900.25, 1.0], with the top hit moved down, and the reported metrics are silently wrong.

The test shows that `SubmissionReport.report` writes a hit with score 1200.5 into the TREC run line (`qid Q0 docno rank score tag`, the file `QueryDriver` calls the submission file for trec_eval) with a grouping comma.

## Expected vs actual

Expected the score column `1200.5000`; actual `1,200.5000` (trec_eval reads it with C `atof()` as 1.0), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/benchmark/quality/utils/TestSubmissionReportScoreFormat.java](src/org/apache/lucene/benchmark/quality/utils/TestSubmissionReportScoreFormat.java) (JUnit test)

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
     execute --select-class org.apache.lucene.benchmark.quality.utils.TestSubmissionReportScoreFormat
```

The module(s) benchmark also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
