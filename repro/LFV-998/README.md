# BlendedInfixSuggester.lookup computes num * numFactor in int, so a large num wraps: num = Integer.MAX_VALUE throws IllegalArgumentException 'numHits must be &gt; 0', some values throw NoSuchElementException, and specific values silently return fewer suggestions than exist

Bug LFV-998 · Area: suggest / analyzing: BlendedInfixSuggester lookup over-fetch (num * numFactor) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a BlendedInfixSuggester with its defaults (numFactor 10) from 8 entries that match 'luc' and call lookup("luc", false, Integer.MAX_VALUE) to get all suggestions; num is documented as the 'maximum number of results to return', so you expect the same 8 suggestions that num = 1000 returns. Instead the over-fetch count num * numFactor is computed in int (BlendedInfixSuggester.java:199) and wraps: a negative product throws IllegalArgumentException 'numHits must be &gt; 0; please use TotalHitCountCollector...' (num = Integer.MAX_VALUE or 214748365), a product below numFactor leaves room for 0 results and throws NoSuchElementException (num = 429496730), and a small positive product silently cuts the list (num = 429496731 returns only [lucas]; with numFactor 1000 already num = 4294969 does). So the number of suggestions can go down as num goes up.

The test shows that `BlendedInfixSuggester.lookup("luc", false, Integer.MAX_VALUE)` fails because `num * numFactor` (default 10) is computed in int and wraps to -10 (BlendedInfixSuggester.java:199).

## Expected vs actual

Expected: the one stored suggestion (`num` is a maximum); actual (-ea and -da): IllegalArgumentException "numHits must be &gt; 0; please use TotalHitCountCollector..." from TopFieldCollectorManager, rethrown as an AssertionError.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestBlendedInfixSuggesterNumOverflow.java](src/org/apache/lucene/search/suggest/analyzing/TestBlendedInfixSuggesterNumOverflow.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestBlendedInfixSuggesterNumOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/suggester-lookup-num-overflow-and-presize/](../../models/suggester-lookup-num-overflow-and-presize/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
