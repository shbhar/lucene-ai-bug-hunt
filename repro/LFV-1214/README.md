# Automaton.Builder.addEpsilon(s, s) never returns and exhausts the heap when state s already has a transition, because its loop re-reads nextTransition while it appends copies that leave s again

Finding LFV-1214 · Area: util / automaton (Automaton.Builder.addEpsilon) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1214.html](../../bugs/LFV-1214.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an automaton with Automaton.Builder, give a state s one accepting self-loop arc (s -a-&gt; s), and call addEpsilon(s, s). You expect it to return at once: its javadoc says it copies dest's arcs over to source, an epsilon from a state to itself adds nothing, and the sibling Automaton.addEpsilon returns on the same call. Instead the call never returns: its loop re-reads the growing transition count, every copy it appends again leaves s and is copied again, and it runs until OutOfMemoryError: Java heap space.

## Expected vs actual

- Expected: `addEpsilon(s, s)` returns and the automaton accepts `"aaa"`.
- Actual: It never returns: `java.lang.OutOfMemoryError: Java heap space` from `Builder.addTransition` &lt;- `Builder.addEpsilon` (`Automaton.java:812`), assertions on and off.

## Files

- [src/org/apache/lucene/util/automaton/TestBuilderAddEpsilonToItself.java](src/org/apache/lucene/util/automaton/TestBuilderAddEpsilonToItself.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.automaton.TestBuilderAddEpsilonToItself
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestBuilderAddEpsilonToItself.java](TestBuilderAddEpsilonToItself.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
