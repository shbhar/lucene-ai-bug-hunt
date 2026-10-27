# Stored fields writer: the int slice loop in flush wraps once a chunk is larger than 2,147,450,880 bytes, so a ~2 GiB document within the documented per-document limit closes IndexWriter when smaller documents are still buffered in its chunk

Bug LFV-186 · Area: codecs / stored fields (Lucene90 compressing writer) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a small document and then, before the chunk fills, one document with about 2 GiB of stored content that is within the documented per-document limit, for example exactly 2^31 - 81,920 = 2,147,401,728 serialized bytes after a 49,153-byte document. You expect both to be indexed, as they are when the first document is one byte smaller. Instead flush steps through the chunk with an int offset that wraps past Integer.MAX_VALUE, ByteBuffersDataInput.slice rejects the negative offset with IllegalArgumentException, and IndexWriter closes itself and drops every uncommitted document.

## Expected vs actual

- Expected: You expect both to be indexed, as they are when the first document is one byte smaller.
- Actual: flush steps through the chunk with an int offset that wraps past Integer.MAX_VALUE, ByteBuffersDataInput.slice rejects the negative offset with IllegalArgumentException, and IndexWriter closes itself and drops every uncommitted document.

## Files

- [src/org/apache/lucene/codecs/lucene90/TestStoredFieldsSliceLoopOverflow.java](src/org/apache/lucene/codecs/lucene90/TestStoredFieldsSliceLoopOverflow.java) (JUnit test)

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
java -ea -esa -Xmx10g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.TestStoredFieldsSliceLoopOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/storedfields-compressing/](../../models/storedfields-compressing/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
