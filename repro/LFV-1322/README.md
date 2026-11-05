# Operations.repeat(a, min, max) does not check for a negative min and silently returns a wrong language: on main and since 10.2.0 repeat(x, -1, 1) accepts "xx" (two repetitions, above max) and rejects ""; every release since 4.0.0 is wrong for min &lt; 0 &lt; max, in a different form before 10.2.0

Finding LFV-1322 · Area: core / util/automaton: Operations.repeat(a, min, max) (bounded repetition) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1322.html](../../bugs/LFV-1322.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a bounded-repetition automaton with Operations.repeat(Automata.makeChar('x'), -1, 1), for example with a lower bound computed in your own code that went below 0. You expect what the javadoc promises, an automaton that accepts between min and max (including both) repetitions, here "" and "x", or else an IllegalArgumentException for the negative min. Instead, on main and 10.2.0 to 10.6.0, the result accepts "x" and "xx" (two repetitions although max is 1) and rejects "", because the only bound check is min &gt; max and a negative min turns the mandatory prefix into an empty concatenation (since 10.2.0 a 0-state automaton) to which max - min optional copies are appended; 4.10.0 to 10.1.x return the empty language and 4.0.0 to 4.9.x accept "", "x" and "xx" instead.

## Expected vs actual

- Expected: `repeat` throws `IllegalArgumentException`, or its automaton rejects "xx" and accepts ""; the test passes.
- Actual: The automaton accepts "xx": `AssertionError: 2 repetitions accepted, but max is 1` at line 32, with assertions on and off.

## Files

- [src/org/apache/lucene/util/automaton/TestRepeatNegativeMin.java](src/org/apache/lucene/util/automaton/TestRepeatNegativeMin.java) (JUnit test)

## Run it

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.automaton.TestRepeatNegativeMin
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRepeatNegativeMin.java](TestRepeatNegativeMin.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
