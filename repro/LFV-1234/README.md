# OnHeapHnswGraph.getNodesOnLevel(1) throws NullPointerException on the empty graph that HnswGraphBuilder.build(0) returns, although that graph reports numLevels() == 2, because the upper-level node cache counts as up to date whenever lastFreezeSize == size(), and both start at 0, so it is never built (since 9.9.0)

Finding LFV-1234 · Area: core / util/hnsw: OnHeapHnswGraph (upper-level node cache of getNodesOnLevel and getSortedNodes) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1234.html](../../bugs/LFV-1234.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an HNSW graph with HnswGraphBuilder.create(...).build(0) over an empty vector set and list the nodes of every level the returned OnHeapHnswGraph reports (numLevels() is 2), the same loop Lucene's own vectors writer runs over a graph. You expect level 1 to list no nodes, as level 0 of the same graph does and as HnswGraph.EMPTY does for every level. Instead getNodesOnLevel(1) (and getSortedNodes(1)) throws NullPointerException 'Cannot load from object array because "this.levelToNodes" is null', because the upper-level node cache is rebuilt only when size() differs from the size at its last build, both are 0 on a new graph, so the cache is never created.

## Expected vs actual

- Expected: Every level below `numLevels()` (levels 0 and 1) lists 0 nodes.
- Actual: Level 1 throws `NullPointerException: Cannot load from object array because "this.levelToNodes" is null` at `OnHeapHnswGraph.getNodesOnLevel(OnHeapHnswGraph.java:276)`.

## Files

- [src/org/apache/lucene/util/hnsw/TestEmptyOnHeapGraphLevels.java](src/org/apache/lucene/util/hnsw/TestEmptyOnHeapGraphLevels.java) (JUnit test)

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
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.hnsw.TestEmptyOnHeapGraphLevels
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestEmptyOnHeapGraphLevels.java](TestEmptyOnHeapGraphLevels.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
