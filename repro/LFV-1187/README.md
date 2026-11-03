# A SegmentOrder built by SegmentOrder.fromSort(numeric Sort) sorts the leaves of every reorder() call with one shared comparator whose per-segment memo is an unsynchronized HashMap, so two threads calling reorder() on the same SegmentOrder at the same time can throw NullPointerException (Map.get returns null and is unboxed) (since 10.4.0)

Finding LFV-1187 · Area: core / index: SegmentOrder (query-time segment re-ordering helper) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1187.html](../../bugs/LFV-1187.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build one SegmentOrder with SegmentOrder.fromSort(new Sort(new SortField("f", SortField.Type.LONG, true))) and share it between searches, as you would a Sort or a Query, so two threads call order.reorder(reader) on the same freshly opened reader at the same time. You expect each call to return a re-ordered view: the strategy is built from an immutable Sort, its API exposes no state, and its javadoc says nothing about threads. Instead every reorder() of that strategy sorts the leaves with one comparator whose per-segment memo is a plain HashMap filled with containsKey/put/get and no lock, so a concurrent put or resize can make Map.get return null and reorder() throws NullPointerException 'Cannot invoke "java.lang.Long.longValue()" because the return value of "java.util.Map.get(Object)" is null' at SegmentOrder.java:141.

## Expected vs actual

- Expected: Both `reorder()` calls return a view of the 1,000 segments, f = 999 first.
- Actual: `NullPointerException` ("the return value of java.util.Map.get(Object) is null") at `SegmentOrder.java:141`, in 20 of 20 iterations (19 of 20 with assertions off).

## Files

- [src/org/apache/lucene/search/TestSegmentOrderConcurrentReorder.java](src/org/apache/lucene/search/TestSegmentOrderConcurrentReorder.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSegmentOrderConcurrentReorder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSegmentOrderConcurrentReorder.java](TestSegmentOrderConcurrentReorder.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
