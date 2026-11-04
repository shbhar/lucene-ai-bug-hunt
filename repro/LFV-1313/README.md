# Automaton.copy while the receiver's current state is still open silently overwrites a copied state's transitions once that state is finished, because finishing it shrinks its block in place and moves the transition write position back into the copied block

Finding LFV-1313 · Area: util / automaton (Automaton.copy, finishState) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1313.html](../../bugs/LFV-1313.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an Automaton by hand: state 0 gets arcs 'a' and 'b' to state 1; while state 0 is still the state being built you append a small automaton x (0 -x-&gt; 1, accepting) with copy, which becomes states 2 and 3, and then add state 1's arc 'c' to state 2, which by the class javadoc finishes state 0. You expect 0 -[a-b]-&gt; 1 -c-&gt; 2 -x-&gt; 3, which accepts "acx", exactly what you get if you call finishState() before copy. Instead the automaton silently rejects "acx": finishing state 0 merges its two arcs into one in place and moves the transition write position back into the copied block, so state 1's new arc overwrites copied state 2's arc (state 2 now reads 2 -c-&gt; 2 and state 3 is unreachable).

## Expected vs actual

- Expected: The automaton is `0 -[a-b]-> 1 -c-> 2 -x-> 3`, `run("acx")` returns `true`, and the test passes.
- Actual: `run("acx")` returns `false`: `AssertionError` at line 33 (the `assertTrue`), with assertions on and off, no exception from Lucene.

## Files

- [src/org/apache/lucene/util/automaton/TestAutomatonCopyIntoOpenState.java](src/org/apache/lucene/util/automaton/TestAutomatonCopyIntoOpenState.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.automaton.TestAutomatonCopyIntoOpenState
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestAutomatonCopyIntoOpenState.java](TestAutomatonCopyIntoOpenState.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
