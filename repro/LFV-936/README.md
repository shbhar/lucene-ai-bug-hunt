# QualityQuery.compareTo is not a total order when numeric and non-numeric topic ids are mixed (ints compared as numbers, everything else as strings), so TrecTopicsReader and Trec1MQReader.readQueries, and with them QueryDriver, can throw IllegalArgumentException 'Comparison method violates its general contract!'

Bug LFV-936 · Area: benchmark / search-quality package (QualityQuery, TREC topic readers) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read a TREC topics file with TrecTopicsReader or Trec1MQReader (as QueryDriver does) whose ids are mostly numbers but include a few sub-topic ids such as 1a or 10.1, or a few digit-only ids larger than Integer.MAX_VALUE, and expect the topics back sorted by id, as the readers' '// sort result array (by ID)' comment says. Instead QualityQuery.compareTo compares two int ids as numbers and every other pair as strings, which forms cycles (2 &lt; 100 as numbers, 100 &lt; 1a and 1a &lt; 2 as strings), so Arrays.sort can throw IllegalArgumentException 'Comparison method violates its general contract!' (the repro: ids 1..111 plus 1a, 20a, 64a and 99a, each listed after its parent); when the sort does not detect the cycle, the topics come back in an order that depends on the file order. Separately, the int branch returns n - nOther, which overflows for extreme ids such as 2147483647 vs -1 (this needs a negative id).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: QualityQuery.compareTo compares two int ids as numbers and every other pair as strings, which forms cycles (2 &lt; 100 as numbers, 100 &lt; 1a and 1a &lt; 2 as strings), so Arrays.sort can throw IllegalArgumentException 'Comparison method violates its general contract!' (the repro: ids 1..111 plus 1a, 20a, 64a and 99a, each listed after its parent); when the sort does not detect the cycle, the topics come back in an order that depends on the file order.

## Files

- [src/org/apache/lucene/benchmark/quality/TestQualityQueryOrder.java](src/org/apache/lucene/benchmark/quality/TestQualityQueryOrder.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.benchmark.quality.TestQualityQueryOrder
```

The module(s) benchmark also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
