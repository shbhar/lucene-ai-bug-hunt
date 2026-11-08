# Operations.complement and minus give a wrong, non-deterministic result for an automaton with an arc ending at Integer.MAX_VALUE, because totalize computes t.max + 1 in int and the wrap adds a dead-state arc that overlaps the real arc

Finding LFV-1167 · Area: util / automaton (Operations.totalize, used by Operations.complement and Operations.minus) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1167.html](../../bugs/LFV-1167.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build Automata.makeCharRange('a', Integer.MAX_VALUE), which accepts every one-code-point word from "a" upward, and take its complement with Operations.complement; you expect a deterministic automaton (as the javadoc promises) that rejects "a". Instead the complement accepts "a" and is not deterministic, with no error: totalize, which complement uses to send every missing label to a dead state, computes t.max + 1 in int, the sum wraps to Integer.MIN_VALUE, so it also adds a dead-state arc over the whole code-point range on top of the real arc, and that dead state becomes accepting in the complement. Operations.minus, which subtracts through complement, likewise keeps words it should remove.

## Expected vs actual

- Expected: The complement rejects `"a"`: its intersection with `makeString("a")` is empty.
- Actual: It accepts `"a"`: `java.lang.AssertionError` at line 29, assertions on and off.

## Files

- [src/org/apache/lucene/util/automaton/TestComplementOfRangeEndingAtMaxValue.java](src/org/apache/lucene/util/automaton/TestComplementOfRangeEndingAtMaxValue.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.automaton.TestComplementOfRangeEndingAtMaxValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestComplementOfRangeEndingAtMaxValue.java](TestComplementOfRangeEndingAtMaxValue.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
