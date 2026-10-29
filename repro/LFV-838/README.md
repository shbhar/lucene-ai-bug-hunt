# A binary doc-values update whose term matches many documents copies its value once per matched document into one int-sized buffer when it is applied, so at 2^31 bytes in one segment (about 2^30 for the first update of a field in a packet) commit throws ArrayIndexOutOfBoundsException (AssertionError with -ea) and IndexWriter closes itself, losing the update and every uncommitted document (every release since 4.8.0)

Bug LFV-838 · Area: index / IndexWriter doc-values updates (BinaryDocValuesFieldUpdates value buffer, FrozenBufferedUpdates apply, applyUpdatesPacket tragic close) · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-838.html](../../bugs/LFV-838.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You commit 2,100 documents that share the term group:g, add one more document, call updateBinaryDocValue(group:g, field, a 1 MiB value) once and commit; the javadoc sets no size limit and says the value replaces the value of all affected documents, and the resulting 2.1 GB of binary doc values is an index Lucene can hold (a build with the fix sketch commits it). Instead, applying the update appends a full copy of the value for every matched document to one int-addressed BytesRefBuilder, which overflows at 2^31 bytes: commit throws ArrayIndexOutOfBoundsException (with -ea, AssertionError 'size must be positive (got -2147483648): likely integer overflow?'), IndexWriter treats it as a tragic event and closes itself, and both the update and the extra uncommitted document are gone. On 7.7.0 and later a lone update fails at half that size (1,100 documents x 1 MiB) because of the linked bug that applies the first update of a field twice, and ordinary value sizes suffice too (530,000 documents x 4 KiB).

## Expected vs actual

- Expected: `commit` succeeds, the writer stays open, all 2,100 documents read the 1 MiB value and `late` is committed.
- Actual: `commit` throws `AssertionError: size must be positive (got -2147483648): likely integer overflow?` (`ArrayIndexOutOfBoundsException` with assertions off); the writer is closed and `late` is lost.

## Files

- [src/org/apache/lucene/index/TestBinaryDocValuesUpdateBufferOverflow.java](src/org/apache/lucene/index/TestBinaryDocValuesUpdateBufferOverflow.java) (JUnit test)
- [src/BinaryDocValuesUpdateBufferOverflowMain.java](src/BinaryDocValuesUpdateBufferOverflowMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx8g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestBinaryDocValuesUpdateBufferOverflow

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" BinaryDocValuesUpdateBufferOverflowMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestBinaryDocValuesUpdateBufferOverflow.java](TestBinaryDocValuesUpdateBufferOverflow.java)
- [TestBinaryDvUpdateManyDocs.java](TestBinaryDvUpdateManyDocs.java)
- [TestModerateValueBinaryUpdate.java](TestModerateValueBinaryUpdate.java)
- [TestBinaryDocValuesFieldUpdatesSharedValue.java](TestBinaryDocValuesFieldUpdatesSharedValue.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
