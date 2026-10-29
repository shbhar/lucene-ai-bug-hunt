# AutomatonTermsEnum throws ArrayIndexOutOfBoundsException for an NFA with a reachable dead state

Bug LFV-055 · Area: util / automaton · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compile an NFA that has a reachable dead state into a CompiledAutomaton and intersect it through a non-block-tree consumer such as DOC_VALUES_REWRITE, SortedSetDocValues.intersect or MemoryIndex. You expect the matching terms, which the block-tree path returns. Instead AutomatonTermsEnum throws ArrayIndexOutOfBoundsException.

The test shows that an AutomatonQuery with DOC_VALUES_REWRITE over a 4-state NFA with a reachable dead state (language {b, bc}) crashes in AutomatonTermsEnum on a one-document index.

## Expected vs actual

Expected: count 1 (the doc with "b"; CompiledAutomaton forbids dead states only for DFAs); actual (-ea and -da): ArrayIndexOutOfBoundsException "Index 5 out of bounds for length 5" at NFARunAutomaton.getNextTransition.

## Files

- [src/org/apache/lucene/search/TestAutomatonQueryNfaDeadState.java](src/org/apache/lucene/search/TestAutomatonQueryNfaDeadState.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestAutomatonQueryNfaDeadState
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-automaton-verification/](../../models/study-automaton-verification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
