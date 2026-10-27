# FSTCompletionLookup sorts weights as unsigned bytes, so negative weights get the highest buckets and are suggested before positive ones

Bug LFV-479 · Area: suggest / FST completion suggester (FSTCompletionLookup.build weight bucketing) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an FSTCompletionLookup from two suggestions, apple with weight 10 and apricot with weight -10, and ask lookup("ap", true, 2) for the best completions first. You expect apple first, because the class javadoc says buckets come from 'sorting the input by weight', InputIterator says 'higher numbers mean better suggestions', and encodeWeight accepts every int. Instead you get [apricot/5, apple/0], with no error: build sorts the weights by their unsigned big-endian bytes, where every negative int (first byte 0x80 or above) comes after every non-negative int, so all negative weights land in the highest buckets and are suggested first.

## Expected vs actual

- Expected: You expect apple first, because the class javadoc says buckets come from 'sorting the input by weight', InputIterator says 'higher numbers mean better suggestions', and encodeWeight accepts every int.
- Actual: You get [apricot/5, apple/0], with no error: build sorts the weights by their unsigned big-endian bytes, where every negative int (first byte 0x80 or above) comes after every non-negative int, so all negative weights land in the highest buckets and are suggested first.

## Files

- [src/org/apache/lucene/search/suggest/fst/TestFSTCompletionLookupNegativeWeights.java](src/org/apache/lucene/search/suggest/fst/TestFSTCompletionLookupNegativeWeights.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.search.suggest.fst.TestFSTCompletionLookupNegativeWeights
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
