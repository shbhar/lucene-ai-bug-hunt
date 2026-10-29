# Operations.isTotal returns true for a deterministic automaton that can reach a dead state, so an AutomatonQuery can match every term

Bug LFV-049 · Area: util / automaton · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-049.html](../../bugs/LFV-049.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call Operations.isTotal, or build an AutomatonQuery, on a deterministic automaton that rejects some strings and has a reachable dead state, such as a complete DFA with an explicit sink state built by hand. You expect false, as Lucene 9.12.0 returns. Instead, since 10.0.0, isTotal checks only live states, so a transition into the dead state counts as coverage and it returns true; CompiledAutomaton then compiles the query to AUTOMATON_TYPE.ALL, and in the repro an AutomatonQuery matched 3 documents instead of 2.

The test shows that `Operations.isTotal` returns true for a deterministic automaton with a reachable dead state, although the automaton rejects "0".

## Expected vs actual

Expected: `isTotal(a) == false` (javadoc: "Returns true if the given automaton accepts all strings"); actual: `true` (`expected:<false> but was:<true>`).

## Files

- [src/org/apache/lucene/util/automaton/TestIsTotalDeadState.java](src/org/apache/lucene/util/automaton/TestIsTotalDeadState.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.automaton.TestIsTotalDeadState
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-automaton-verification/](../../models/study-automaton-verification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
