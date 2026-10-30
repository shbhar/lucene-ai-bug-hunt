# With doc-values overlays (on by default on main), a second advanceExact on the same document does not restart a multi-valued SORTED_NUMERIC column's values, so SortedNumericSortField sorts return the wrong top hits (or throw)

Bug LFV-1051 · Area: index / doc-values overlays (OverlaySortedNumericDocValues over DocValuesOverlayMerger), seen through search / field sorting (SortedNumericSelector, LongComparator, TopFieldCollector) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index three documents with a multi-valued SortedNumericDocValuesField m = {50,60}, {10,90}, {30,40}, set doc 2 to the single value 70 with IndexWriter.updateSortedNumericDocValue (default IndexWriterConfig on main), and sort all documents by new SortedNumericSortField("m", LONG), which keys each document by its minimum value. You expect doc 1 first (minimum 10), which is what you get with overlays disabled. You get doc 2: the overlay view ignores a second advanceExact on the document it already sits on, so when the collector reads doc 1 again its value cursor continues at 90 instead of restarting at 10, and with a MAX sort the cursor can run past the end of the column and throw.

## Expected vs actual

- Expected: You expect doc 1 first (minimum 10), which is what you get with overlays disabled.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/index/TestOverlaySortedNumericSort.java](src/org/apache/lucene/index/TestOverlaySortedNumericSort.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestOverlaySortedNumericSort
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
