# AutomatonToTokenStream.toTokenStream throws ArrayIndexOutOfBoundsException ('Index 0 out of bounds for length 0') on a 0-state automaton, Lucene's standard empty-language value (Automata.makeEmpty, or an empty Operations result such as intersection("a", "b"))

Bug LFV-865 · Area: core / analysis: AutomatonToTokenStream (automaton to token-graph conversion) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You convert an automaton whose language is empty, for example Automata.makeEmpty or Operations.intersection(Automata.makeString("a"), Automata.makeString("b")), with AutomatonToTokenStream.toTokenStream, whose only stated precondition is a finite DAG. You expect a TokenStream with no tokens (what the same method already returns for Automata.makeEmptyString and for the same empty language written with one dead state) or at least a clear IllegalArgumentException. Instead it throws ArrayIndexOutOfBoundsException 'Index 0 out of bounds for length 0', because these automata have 0 states and the method reads indegree[0], the in-degree of the start state, without checking that a state exists.

## Expected vs actual

- Expected: You expect a TokenStream with no tokens (what the same method already returns for Automata.makeEmptyString and for the same empty language written with one dead state) or at least a clear IllegalArgumentException.
- Actual: It throws ArrayIndexOutOfBoundsException 'Index 0 out of bounds for length 0', because these automata have 0 states and the method reads indegree[0], the in-degree of the start state, without checking that a state exists.

## Files

- [src/org/apache/lucene/analysis/TestAutomatonToTokenStreamEmptyAutomaton.java](src/org/apache/lucene/analysis/TestAutomatonToTokenStreamEmptyAutomaton.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions on, seed 3430DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=3430DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestAutomatonToTokenStreamEmptyAutomaton
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
