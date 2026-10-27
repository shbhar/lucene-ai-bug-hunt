# IndexWriter accepts a stored string of 2^30 - 1 or more UTF-8 bytes of non-Latin-1 text (from about half of its own MAX_STORED_STRING_LENGTH up), but DataInput.readString can never decode it, so StoredFields#document, visitor-path merges and index-sorted flushes throw OutOfMemoryError

Bug LFV-172 · Area: core store + index / stored string fields (DataInput.readString decode, IndexWriter.MAX_STORED_STRING_LENGTH check) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You store a text field of 357,913,941 CJK characters (1,073,741,823 UTF-8 bytes, about half of what IndexWriter's own length check, MAX_STORED_STRING_LENGTH, allows) with the default codec, and IndexWriter accepts and commits it. You expect StoredFields#document to return it, as it does for a string one character shorter. Instead every read throws java.lang.OutOfMemoryError 'UTF16 String size is 1073741823, should be less than 1073741823', whatever the heap, because DataInput.readString decodes with new String(bytes, 0, length, UTF_8), which on JDK 9 and later sizes its UTF-16 buffer by the byte count and refuses 2^30 - 1 or more units; merges that copy the document through a visitor, and flushes of an index-sorted writer, throw the same Error and close IndexWriter.

## Expected vs actual

- Expected: You expect StoredFields#document to return it, as it does for a string one character shorter.
- Actual: Every read throws java.lang.OutOfMemoryError 'UTF16 String size is 1073741823, should be less than 1073741823', whatever the heap, because DataInput.readString decodes with new String(bytes, 0, length, UTF_8), which on JDK 9 and later sizes its UTF-16 buffer by the byte count and refuses 2^30 - 1 or more units; merges that copy the document through a visitor, and flushes of an index-sorted writer, throw the same Error and close IndexWriter.

## Files

- [src/org/apache/lucene/codecs/lucene90/TestStoredStringOver1GiBUtf8.java](src/org/apache/lucene/codecs/lucene90/TestStoredStringOver1GiBUtf8.java) (JUnit test)

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
java -ea -esa -Xmx8g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.TestStoredStringOver1GiBUtf8
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/storedfields-compressing/](../../models/storedfields-compressing/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
