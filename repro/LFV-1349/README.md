# SortedSetSelector's MIN, MAX, MIDDLE_MIN and MIDDLE_MAX views read the current document's ordinals again after an intoBitSet call that does not move (upTo &lt;= docID()), so on SimpleTextCodec, which since 10.6.0 exposes every single-valued SORTED_SET field through the MIN view, IndexSearcher.count of a FILTER conjunction throws ArrayIndexOutOfBoundsException (new in 10.6.0)

Finding LFV-1349 · Area: search / SortedSetSelector views (intoBitSet), reached through codecs / SimpleText doc values · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1349.html](../../bugs/LFV-1349.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 4,500 documents with SimpleTextCodec, a single-valued SORTED_SET field f on docs 0 and 4400 and a numeric doc-values field g on docs 1 and 4400, and call IndexSearcher.count on FILTER FieldExistsQuery(f) + FILTER FieldExistsQuery(g). You expect 1, as on the default codec and on 10.5.2. Instead count throws ArrayIndexOutOfBoundsException 'Index 1 out of bounds for length 1': the conjunction scorer makes an intoBitSet call that cannot move the iterator (upTo &lt;= docID()), which the intoBitSet javadoc defines as a no-op, but the SortedSetSelector MIN view that SimpleText puts in front of the field since 10.6.0 calls nextOrd() again for the current document and runs past its single ordinal.

## Expected vs actual

- Expected: `count` returns 1 (only doc 4400 has both fields) and the test passes.
- Actual: `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` at `SimpleTextDocValuesReader$9.nextOrd(:851)`, from `SortedSetSelector$MinValue.intoBitSet(:137)`.

## Files

- [src/org/apache/lucene/search/TestSimpleTextSortedSetConjunctionCount.java](src/org/apache/lucene/search/TestSimpleTextSortedSetConjunctionCount.java) (JUnit test)

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
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSimpleTextSortedSetConjunctionCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSimpleTextSortedSetConjunctionCount.java](TestSimpleTextSortedSetConjunctionCount.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
