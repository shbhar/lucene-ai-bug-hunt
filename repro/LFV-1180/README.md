# Operations.determinize throws (AssertionError with -ea, ArrayIndexOutOfBoundsException with -da) for a valid automaton with an arc ending at Integer.MAX_VALUE, because its sweep sorts points with 'point - other.point' and the arc's end point 1 + max wraps to Integer.MIN_VALUE

Finding LFV-1180 · Area: util / automaton (Operations.determinize sweep: PointTransitions / PointTransitionSet; also NFARunAutomaton's lazy determinize) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1180.html](../../bugs/LFV-1180.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build the union of {0}[11, Integer.MAX_VALUE] and {0}[0, 10] (a 0 followed by any one label) with Automata.makeChar, makeCharRange, Operations.concatenate and union, and call Operations.determinize; you expect a deterministic automaton for the same language. Instead it throws AssertionError in StateSet.decr with -ea, and ArrayIndexOutOfBoundsException 'Index -5 out of bounds for length 9' with -da. The sweep files the end of the [11, MAX] arc under 1 + MAX, which wraps to Integer.MIN_VALUE, and sorts its points with the subtraction point - other.point, which is no consistent order for MIN and 0, so it closes the [0, 10] arc before opening it and decrements a state it never added.

## Expected vs actual

- Expected: `determinize` returns a DFA that accepts the two-label word [0, 5].
- Actual: `AssertionError` in `StateSet.decr` (assertions on), or `ArrayIndexOutOfBoundsException: Index -5 out of bounds for length 9` (off).

## Files

- [src/org/apache/lucene/util/automaton/TestDeterminizeRangeEndingAtMaxValue.java](src/org/apache/lucene/util/automaton/TestDeterminizeRangeEndingAtMaxValue.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.automaton.TestDeterminizeRangeEndingAtMaxValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDeterminizeRangeEndingAtMaxValue.java](TestDeterminizeRangeEndingAtMaxValue.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
