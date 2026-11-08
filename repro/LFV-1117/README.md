# GraphTokenStreamFiniteStrings.getFiniteStrings returns TokenStreams that all read the iterator's one reused IntsRef, so a stream read after the next hasNext() or next() call replays another path's tokens, or a mix that is no path of the graph (regression of LUCENE-7638, since 6.5.0)

Finding LFV-1117 · Area: core / util.graph (GraphTokenStreamFiniteStrings.getFiniteStrings: per-path TokenStreams over FiniteStringsIterator's reused IntsRef) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1117.html](../../bugs/LFV-1117.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a GraphTokenStreamFiniteStrings over the tokens new, ny (a synonym spanning both positions) and york, whose paths are [new, york] and [ny], collect both TokenStreams from getFiniteStrings() into a list, and then read them. You expect each stream to replay its own path, as the original 6.4 API (which copied each path) did. You get [ny, york] and [ny], with no error: every stream reads the iterator's one reused IntsRef, which hasNext() overwrites with the next path, so the first stream mixes the second path's first token with its own leftover second token.

## Expected vs actual

- Expected: The set of replayed paths is `{[new, york], [ny]}`, the graph's two paths.
- Actual: It is `{[ny, york], [ny]}`: `AssertionError: expected:<[[new, york], [ny]]> but was:<[[ny], [ny, york]]>`, with no other error.

## Files

- [src/org/apache/lucene/util/graph/TestGraphTokenStreamFiniteStringsHeldStreams.java](src/org/apache/lucene/util/graph/TestGraphTokenStreamFiniteStringsHeldStreams.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.graph.TestGraphTokenStreamFiniteStringsHeldStreams
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestGraphTokenStreamFiniteStringsHeldStreams.java](TestGraphTokenStreamFiniteStringsHeldStreams.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
