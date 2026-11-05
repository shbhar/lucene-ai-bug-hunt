# Util.TopNSearcher computes TopResults.isComplete as rejectCount + topN &lt;= maxQueueDepth in int, so with topN = Integer.MAX_VALUE, a bounded queue and one rejected result the sum wraps and isComplete is true although the queue dropped an accepted completion (every release since 4.8.0)

Finding LFV-1326 · Area: core / util/fst: Util.TopNSearcher.search (the TopResults.isComplete flag) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1326.html](../../bugs/LFV-1326.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass Util.TopNSearcher over the FST {a -&gt; 1, b -&gt; 2} with an acceptResult that rejects 'a', ask for every result (topN = Integer.MAX_VALUE) with a queue of depth 1, and call search(). You expect an empty result with isComplete false: the depth-1 queue dropped 'b', an accepted completion, and the flag's javadoc promises true only if the queue was large enough to find the complete list (topN = Integer.MAX_VALUE - 1 does give false). Instead isComplete is true, because search() computes rejectCount + topN &lt;= maxQueueDepth in int and 1 + Integer.MAX_VALUE wraps to Integer.MIN_VALUE.

## Expected vs actual

- Expected: An empty list with `isComplete == false`: the depth-1 queue dropped `b`, an accepted result.
- Actual: An empty list with `isComplete == true` (`1 + Integer.MAX_VALUE` wraps to `Integer.MIN_VALUE`, which is `<= 1`), assertions on or off.

## Files

- [src/TestTopNSearcherIsCompleteOverflow.java](src/TestTopNSearcherIsCompleteOverflow.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestTopNSearcherIsCompleteOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTopNSearcherIsCompleteOverflow.java](TestTopNSearcherIsCompleteOverflow.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
