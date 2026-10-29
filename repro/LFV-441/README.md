# Automata.makeDecimalInterval accepts a negative min without error and returns an automaton with the wrong language (for [-1, 5] it rejects "0" to "4" and accepts "12" and "-1"), or for some inputs throws StringIndexOutOfBoundsException

Bug LFV-441 · Area: util / automaton · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a numeric-interval automaton with Automata.makeDecimalInterval(min, max, digits) and pass a negative min, for example (-1, 5, 0). You expect either the documented IllegalArgumentException or an automaton for the non-negative integers in the interval (here 0 to 5, as makeDecimalInterval(0, 5, 0) gives). Instead the call returns without error an automaton that rejects "0" to "4" and accepts "12", "49", "-1" and "-5", and some all-negative ranges such as (-15, -1, 0) throw an undocumented StringIndexOutOfBoundsException.

The test shows that `Automata.makeDecimalInterval(-1, 5, 0)` accepts a negative min without error and returns an automaton with the wrong language.

## Expected vs actual

Expected `IllegalArgumentException`, or "0" (in [-1, 5]) accepted and "12" (&gt; max) rejected; actual: "0" rejected and "12" accepted (`expected:<[0]> but was:<[12]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/util/automaton/TestMakeDecimalIntervalNegativeMin.java](src/org/apache/lucene/util/automaton/TestMakeDecimalIntervalNegativeMin.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.automaton.TestMakeDecimalIntervalNegativeMin
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
