# JaroWinklerDistance.setThreshold javadoc says 'Set to a negative value to get the Jaro distance', but a negative threshold adds the Winkler bonus to every pair (the same as threshold 0); only a threshold of 1 or more gives plain Jaro; documentation defect, the javadoc fix is the default proposal

Bug LFV-537 · Area: suggest / spell (JaroWinklerDistance threshold) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You want the plain Jaro similarity from JaroWinklerDistance, so, as the setThreshold javadoc says ('Set to a negative value to get the Jaro distance'), you call setThreshold(-1) and expect getDistance("martha", "marhta") to return the Jaro value 0.9444. Instead it returns 0.9611, the Jaro-Winkler value: getDistance skips the Winkler bonus only when the Jaro value is below the threshold, which never happens for a negative threshold, so every pair gets the bonus, exactly as with threshold 0. Only a threshold of 1 or more gives plain Jaro; the sentence has been false since the class was added in 2008, so the javadoc is the side to fix by default.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It returns 0.9611, the Jaro-Winkler value: getDistance skips the Winkler bonus only when the Jaro value is below the threshold, which never happens for a negative threshold, so every pair gets the bonus, exactly as with threshold 0.

## Files

- [src/org/apache/lucene/search/spell/TestJaroWinklerNegativeThreshold.java](src/org/apache/lucene/search/spell/TestJaroWinklerNegativeThreshold.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.spell.TestJaroWinklerNegativeThreshold
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
