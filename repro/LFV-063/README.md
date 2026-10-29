# AxiomaticF2LOG and AxiomaticF2EXP scores drop by 1 ulp as term frequency crosses a power of two

Bug LFV-063 · Area: search / similarities · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score with AxiomaticF2LOG or AxiomaticF2EXP at default parameters and no boost. You expect the score never to decrease as term frequency rises, which impacts-based pruning relies on. Instead it drops by 1 ulp when the frequency crosses a power of two, so a pruned search can return a different top hit than exhaustive search.

The test shows that with default `AxiomaticF2EXP()` and `AxiomaticF2LOG()`, boost 1 and the statistics of a real index, `SimScorer#score` drops by 1 float ulp when freq goes from 2^29-32 to 2^29 (the next float).

## Expected vs actual

Expected `score(2^29) >= score(2^29 - 32)` (the `SimScorer#score` contract that impacts-based pruning relies on); actual F2EXP 1.0134975 -&gt; 1.0134974 and F2LOG 0.10277987 -&gt; 0.102779865, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/similarities/TestAxiomaticF2ScoreMonotonicInFreq.java](src/org/apache/lucene/search/similarities/TestAxiomaticF2ScoreMonotonicInFreq.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.similarities.TestAxiomaticF2ScoreMonotonicInFreq
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-similarity-impacts-smt/](../../models/study-similarity-impacts-smt/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
