# StoredFieldDataInput(ByteArrayDataInput) takes the input's end position as its length, so a ByteArrayDataInput that does not start at position 0 (a slice at a non-zero offset, or a partly read input) silently stores extra trailing bytes, or makes addDocument fail and close IndexWriter

Bug LFV-175 · Area: index / stored fields (StoredFieldDataInput: DataInput-backed StoredField values, #14213) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-175.html](../../bugs/LFV-175.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add new StoredField(name, new StoredFieldDataInput(new ByteArrayDataInput(bytes, offset, len))) and expect the stored value to be exactly those len bytes, because the record's length is documented as 'the length of the data input'. The convenience constructor copies ByteArrayDataInput.length, which is the end position offset + len, so for an 8-byte slice at offset 4 of a 16-byte array the document stores 12 bytes, the slice plus the next 4, with no error. If fewer bytes than the offset follow the slice, the stored fields writer reads past the array, addDocument throws ArrayIndexOutOfBoundsException and IndexWriter closes itself.

## Expected vs actual

- Expected: `getLength` is 8 and field `f` holds the 8-byte slice `05 06 07 08 09 0a 0b 0c`; the same slice of a 12-byte array is indexed and the writer stays open.
- Actual: `getLength` is 12 and `f` holds 12 bytes, ending in `0d 0e 0f 10`; for the 12-byte array `addDocument` throws `ArrayIndexOutOfBoundsException` and the `IndexWriter` closes.

## Files

- [src/org/apache/lucene/index/TestStoredFieldDataInputSliceLength.java](src/org/apache/lucene/index/TestStoredFieldDataInputSliceLength.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestStoredFieldDataInputSliceLength
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestStoredFieldDataInputPosition.java](TestStoredFieldDataInputPosition.java)
- [TestStoredFieldDataInputSliceLength.java](TestStoredFieldDataInputSliceLength.java)
- [TestStoredFieldDataInputOffsetSlice.java](TestStoredFieldDataInputOffsetSlice.java)
- [TestStoredFieldDataInputLostDocsProbe.java](TestStoredFieldDataInputLostDocsProbe.java)
- [TestStoredFieldDataInputExposureProbe.java](TestStoredFieldDataInputExposureProbe.java)
- [StoredFieldDataInputPersistenceProbe.java](StoredFieldDataInputPersistenceProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
