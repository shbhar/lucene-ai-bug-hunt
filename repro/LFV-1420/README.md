# Test framework: AutomatonTestUtil.getFiniteStringsRecursive steps its int label loop counter past Integer.MAX_VALUE, where it wraps to Integer.MIN_VALUE, so for an arc ending at Integer.MAX_VALUE it returns strings the automaton does not accept (with a limit) or never returns (without one); since 4.8.1

Finding LFV-1420 · Area: test-framework / util.automaton (AutomatonTestUtil.getFiniteStringsRecursive, the slow reference enumerator of finite strings) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1420.html](../../bugs/LFV-1420.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an automaton with one arc labelled Integer.MAX_VALUE from the start state to an accepting state, so it accepts exactly one string, [Integer.MAX_VALUE], and call AutomatonTestUtil.getFiniteStringsRecursive(a, 1). You expect the set {[7fffffff]}, as its javadoc promises ('Returns the set of accepted strings, assuming that at most limit strings are accepted'). Instead you get {[7fffffff], [80000000]}: the label loop 'for (int n = t.min; n &lt;= t.max; n++)' wraps from Integer.MAX_VALUE to Integer.MIN_VALUE, the test n &lt;= t.max stays true, and only the limit check stops it; with limit -1 it never stops.

## Expected vs actual

- Expected: `{[7fffffff]}`, the one accepted string.
- Actual: `{[7fffffff], [80000000]}`: `AssertionError: expected:<[[7fffffff]]> but was:<[[7fffffff], [80000000]]>`, assertions on and off.

## Files

- [src/org/apache/lucene/tests/util/automaton/TestGetFiniteStringsRecursiveMaxLabel.java](src/org/apache/lucene/tests/util/automaton/TestGetFiniteStringsRecursiveMaxLabel.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.tests.util.automaton.TestGetFiniteStringsRecursiveMaxLabel
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestGetFiniteStringsRecursiveMaxLabel.java](TestGetFiniteStringsRecursiveMaxLabel.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
