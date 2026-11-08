# Automaton.copy(a) called on a itself throws IndexOutOfBoundsException once a has an accept state, and leaves a half-copied, because copy reads a's state count and accept bits after it has already grown a

Finding LFV-1220 · Area: util / automaton (Automaton.copy) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1220.html](../../bugs/LFV-1220.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a small Automaton a (state 0 with an arc 'a' to state 1, state 1 accepting) and call a.copy(a). You expect what the javadoc says, 'Copies over all states/transitions from other' with the new states appended: states 2 and 3 with 2 -a-&gt; 3 and 3 accepting, which is exactly what copying from a separate identical automaton gives. Instead copy throws IndexOutOfBoundsException from setAccept, because it reads a's state count and accept bits after it has already grown a, and a is left with 4 states but only 1 transition.

## Expected vs actual

- Expected: `a.copy(a)` appends states 2 and 3 with `2 -a-> 3` and state 3 accepting: 2 transitions, `isAccept(3)` true.
- Actual: `IndexOutOfBoundsException: Index 5 out of bounds for length 4` at `Automaton.setAccept` (`:108`) &lt;- `Automaton.copy` (`:221`), assertions on and off.

## Files

- [src/org/apache/lucene/util/automaton/TestAutomatonCopySelf.java](src/org/apache/lucene/util/automaton/TestAutomatonCopySelf.java) (JUnit test)

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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.automaton.TestAutomatonCopySelf
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestAutomatonCopySelf.java](TestAutomatonCopySelf.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
