# FiniteStringsIterator (and LimitedFiniteStringsIterator) returns strings the automaton does not accept after an arc ending at Integer.MAX_VALUE: PathNode.nextLabel steps an int label with label++, which wraps to Integer.MIN_VALUE, so its end test 'label &gt; t.max' never fires and the iterator goes on to emit labels Integer.MIN_VALUE .. -2; since 4.8.1

Finding LFV-1418 · Area: util / automaton (FiniteStringsIterator.PathNode.nextLabel; LimitedFiniteStringsIterator) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1418.html](../../bugs/LFV-1418.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an automaton with one arc labelled Integer.MAX_VALUE from the start state to an accepting state, so it accepts exactly one string, [Integer.MAX_VALUE], and enumerate it with new FiniteStringsIterator(a). You expect next() to return [7fffffff] and then null, as the class javadoc ('Iterates all accepted strings.') and next() ('null, if no more finite strings are available') promise. Instead the second next() returns [80000000] (Integer.MIN_VALUE), a string the automaton rejects, and the iterator keeps going: PathNode.nextLabel returns label++ on an int, which wraps from Integer.MAX_VALUE to Integer.MIN_VALUE, so its end test 'label &gt; t.max' never fires until the label reaches -1, the iterator's end sentinel.

## Expected vs actual

- Expected: `next()` returns `[7fffffff]`, then `null`.
- Actual: The second `next()` returns `[80000000]`: `AssertionError: expected null, but was:<[80000000]>`, assertions on and off.

## Files

- [src/org/apache/lucene/util/automaton/TestFiniteStringsIteratorMaxLabel.java](src/org/apache/lucene/util/automaton/TestFiniteStringsIteratorMaxLabel.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
     execute --select-class org.apache.lucene.util.automaton.TestFiniteStringsIteratorMaxLabel
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFiniteStringsIteratorMaxLabel.java](TestFiniteStringsIteratorMaxLabel.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
