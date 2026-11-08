# The iterator returned by GraphTokenStreamFiniteStrings.getFiniteStrings throws NullPointerException (AssertionError with -ea) instead of NoSuchElementException when next() is called after the last path (since 6.5.0)

Finding LFV-1254 · Area: core / util.graph (GraphTokenStreamFiniteStrings.getFiniteStrings: the java.util.Iterator<TokenStream> over a token graph's paths) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1254.html](../../bugs/LFV-1254.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a GraphTokenStreamFiniteStrings over a one-token stream, take getFiniteStrings(), read its only path with next(), see hasNext() return false, and call next() once more. You expect NoSuchElementException, as java.util.Iterator.next() specifies. Instead next() ignores the false result of its own hasNext() call and wraps a null path, so it throws NullPointerException ('Cannot read field "offset" because "ids" is null') with assertions disabled and AssertionError from 'assert ids != null' with -ea; on a graph with no tokens at all, next() throws NullPointerException at once.

## Expected vs actual

- Expected: The second `next()` throws `NoSuchElementException`.
- Actual: It throws `NullPointerException: Cannot read field "offset" because "ids" is null` (assertions off) or `AssertionError` (assertions on).

## Files

- [src/org/apache/lucene/util/graph/TestGraphFiniteStringsNextPastEnd.java](src/org/apache/lucene/util/graph/TestGraphFiniteStringsNextPastEnd.java) (JUnit test)

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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.graph.TestGraphFiniteStringsNextPastEnd
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestGraphFiniteStringsNextPastEnd.java](TestGraphFiniteStringsNextPastEnd.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
