# The default codec's off-heap vector values return an all-zero vector for vectorValue(-1) instead of failing, because the 'nothing cached' marker lastOrd = -1 is also accepted as an ordinal: FloatVectorValues/ByteVectorValues.vectorValue document IndexOutOfBoundsException outside [0, size() - 1], but a fresh or copied OffHeapFloatVectorValues/OffHeapByteVectorValues answers -1 (the index() of an unpositioned iterator) with the never-read buffer (every release since 10.0.0)

Finding LFV-1412 · Area: codecs / vectors: lucene95 off-heap flat vector values (OffHeapFloatVectorValues, OffHeapByteVectorValues, OffHeapFloat16VectorValues; the default flat vectors reader) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1412.html](../../bugs/LFV-1412.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a reader on an index whose vector field was written by the default codec and, because of a bug in your own code, ask the field's FloatVectorValues for vectorValue(-1), for example by calling values.vectorValue(iterator.index()) before moving the iterator to a document. The javadoc says an ordinal outside [0, size() - 1] makes the call throw IndexOutOfBoundsException, so you expect an error. Instead you get [0.0, 0.0, 0.0, 0.0] with no error and no read, because a new values object uses lastOrd = -1 to mean 'nothing read yet' and vectorValue(-1) matches that marker and returns the never-filled buffer; the byte vector values behave the same way.

## Expected vs actual

- Expected: `vectorValue(-1)` throws `IndexOutOfBoundsException`.
- Actual: No exception: it returns `[0.0, 0.0]` (`Expected exception IndexOutOfBoundsException but no exception was thrown`).

## Files

- [src/org/apache/lucene/codecs/lucene95/TestOffHeapVectorValuesNegativeOrdinal.java](src/org/apache/lucene/codecs/lucene95/TestOffHeapVectorValuesNegativeOrdinal.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene95.TestOffHeapVectorValuesNegativeOrdinal
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestOffHeapVectorValuesNegativeOrdinal.java](TestOffHeapVectorValuesNegativeOrdinal.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
