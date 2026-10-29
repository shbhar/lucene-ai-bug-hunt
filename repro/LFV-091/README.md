# SingletonSortedSetDocValues.intoBitSet does not refresh its cached ordinal, so nextOrd returns an earlier document's ordinal and DocValuesRangeIterator.forOrdinalRange can return extra hits on single-valued segments

Bug LFV-091 · Area: index / doc values (single-valued SortedSetDocValues view, DocValuesRangeIterator) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take the SortedSetDocValues of a segment whose documents have at most one value each (DocValues.getSortedSet and LeafReader#getSortedSetDocValues then return a single-valued wrapper), call intoBitSet(upTo,...), and read nextOrd for the document the iterator now sits on; or you pass that view to DocValuesRangeIterator.forOrdinalRange on a skip-indexed field and load the matches in bulk. You expect nextOrd to describe the current document, as after the nextDoc loop that the intoBitSet javadoc says it behaves exactly like, and the range iterator to mark only documents whose value is in range. Instead the wrapper's intoBitSet moves the inner iterator without refreshing its cached ordinal, so with two documents a and b nextOrd on doc 1 returns a's ordinal, and forOrdinalRange for [b, b] also marks doc 8192, whose value is a.

The test shows that on the single-valued `SortedSetDocValues` view, `intoBitSet` moves the iterator to the next document but `nextOrd()` still returns the previous document's ordinal.

## Expected vs actual

Expected: after `nextDoc()` (doc 0, "a") and `intoBitSet(1,...)`, the iterator is on doc 1 and `nextOrd()` is 1 ("b"). Actual: 0 (doc 0's "a").

## Files

- [src/org/apache/lucene/index/TestSingletonSortedSetIntoBitSetStaleOrd.java](src/org/apache/lucene/index/TestSingletonSortedSetIntoBitSetStaleOrd.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestSingletonSortedSetIntoBitSetStaleOrd
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
