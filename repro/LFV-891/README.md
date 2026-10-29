# CompiledAutomaton.floor throws NullPointerException for NONE, ALL and SINGLE automata (what the documented one-argument constructor builds from an empty, any-string or single-string automaton) and, since 10.0.0, for NFA-backed NORMAL automata

Bug LFV-891 · Area: util / automaton · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a CompiledAutomaton with the documented one-argument constructor, for example from new RegExp("m").toAutomaton (type SINGLE), Automata.makeEmpty (NONE), Automata.makeAnyString (ALL) or, on 10.x, the non-deterministic RegExp("(a|ab)c").toAutomaton (type NORMAL, run by NFARunAutomaton), and call floor(input, output). You expect what the javadoc promises, the largest accepted term &lt;= input or null: floor("x") = "m", null, "x", and floor("b") = "ac" (the value the determinized form of the same language returns). Instead floor throws NullPointerException ('Cannot invoke ByteRunAutomaton.step(int, int) because this.runAutomaton is null'), because it always walks runAutomaton and the constructor leaves runAutomaton null for these four shapes.

The test shows that `new CompiledAutomaton(Automata.makeString("m")).floor("x")` throws a NullPointerException, because the one-argument constructor builds type SINGLE with `runAutomaton == null` and `floor` always walks `runAutomaton`.

## Expected vs actual

Expected: the largest accepted term &lt;= "x", i.e. "m". Actual on main with -ea and with -da: NullPointerException ("this.runAutomaton" is null) at CompiledAutomaton.java:411.

## Files

- [src/org/apache/lucene/util/automaton/TestCompiledAutomatonFloorSingle.java](src/org/apache/lucene/util/automaton/TestCompiledAutomatonFloorSingle.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.automaton.TestCompiledAutomatonFloorSingle
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
