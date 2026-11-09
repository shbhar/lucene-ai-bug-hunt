# An IOException while reading doc values reaches IndexSearcher#search and the doc-values accessors as a plain RuntimeException instead of the declared IOException, because DirectReader's LongValues.get wraps it in new RuntimeException(e) (since 4.9.0)

Finding LFV-1456 · Area: core / codecs: doc-values decoding (util/packed DirectReader, used by codecs/lucene90 Lucene90DocValuesProducer for numeric, sorted, sorted-set and sorted-numeric doc values) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a reader on an index with a NumericDocValuesField, and then the storage starts failing reads of the doc-values data file (.dvd) with IOException, as a failing disk, NIOFSDirectory after a thread interrupt or a remote Directory can. You expect a search sorted by that field to throw that IOException: IndexSearcher#search documents '@throws IOException if there is a low-level I/O error', and NumericDocValues#longValue, SortedDocValues#ordValue and SortedNumericDocValues#nextValue declare it. Instead it throws java.lang.RuntimeException wrapping the IOException, because DirectReader's per-bit-width LongValues.get methods, which cannot throw a checked exception, catch it and throw new RuntimeException(e) (DirectReader.java:204 and 13 more sites; the same in Lucene90DocValuesProducer's VaryingBPV values), and nothing unwraps it, so catch (IOException) and even catch (UncheckedIOException) miss it.

## Expected vs actual

- Expected: You expect a search sorted by that field to throw that IOException: IndexSearcher#search documents '@throws IOException if there is a low-level I/O error', and NumericDocValues#longValue, SortedDocValues#ordValue and SortedNumericDocValues#nextValue declare it.
- Actual: It throws java.lang.RuntimeException wrapping the IOException, because DirectReader's per-bit-width LongValues.get methods, which cannot throw a checked exception, catch it and throw new RuntimeException(e) (DirectReader.java:204 and 13 more sites; the same in Lucene90DocValuesProducer's VaryingBPV values), and nothing unwraps it, so catch (IOException) and even catch (UncheckedIOException) miss it.

## Files

- [src/org/apache/lucene/search/TestDocValuesReadFailureType.java](src/org/apache/lucene/search/TestDocValuesReadFailureType.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
     execute --select-class org.apache.lucene.search.TestDocValuesReadFailureType
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
