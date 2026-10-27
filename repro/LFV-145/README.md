# Merging segments of an index sorted by a FLOAT or DOUBLE field misplaces a NaN whose sign bit is set, so the merged segment violates its declared index sort (CheckIndex at level 2 or higher reports it) and sorted searches that terminate early on the index sort return wrong top hits

Bug LFV-145 · Area: index / index sorting at merge time (IndexSorter.FloatSorter/DoubleSorter merge keys, MultiSorter) -> search / TopFieldCollector index-sort early termination · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-145.html](../../bugs/LFV-145.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure IndexWriterConfig#setIndexSort with a FLOAT (or DOUBLE) SortField over a FloatDocValuesField (DoubleDocValuesField), index a document whose value is a NaN with the sign bit set, for example the run-time negation of a NaN, and expect every flushed and merged segment to follow the sort, in which Float.compare puts every NaN after +Infinity. Flush gets this right, but a merge orders documents by NumericUtils.sortableFloatBits of the raw stored bits, which ranks that NaN below -Infinity, so the merged segment follows neither order: segments {3, 1, NaN} and {2, 0.5} sorted descending merge to [2.0, 0.5, NaN, 3.0, 1.0]. CheckIndex at level 2 or higher reports the segment as unsorted, and a top-1 search sorted by the index sort returns 2.0 instead of NaN, and 2.0 instead of 3.0 for a query that does not match the NaN document.

## Expected vs actual

- Expected: The merged segment is [NaN, 3.0, 2.0, 1.0, 0.5], the index-sort check passes, and the top hit without the NaN document is 3.0.
- Actual: The merged segment is [2.0, 0.5, NaN, 3.0, 1.0] (FLOAT and DOUBLE), the check reports `docID=1 sorts after docID=2`, and the top hit is 2.0.

## Files

- [src/org/apache/lucene/index/TestIndexSortMergeSignedNaN.java](src/org/apache/lucene/index/TestIndexSortMergeSignedNaN.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.index.TestIndexSortMergeSignedNaN
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestIndexSortSignedNaNMerge.java](TestIndexSortSignedNaNMerge.java)
- [fix-with-test.diff](fix-with-test.diff)
- [TestIndexSortMergeSignedNaN.java](TestIndexSortMergeSignedNaN.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
