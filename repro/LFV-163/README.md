# Lucene90StoredFieldsFormat (the default codec's stored fields) documents a per-document limit of 2^31 - 2^14 bytes, but the format it delegates to documents and enforces 2^31 - chunkSize (chunks of 81,920 / 491,520 bytes), so a document just below the stated limit can fail with ArithmeticException and close IndexWriter (documentation defect: the javadoc value has been stale since the chunks grew past 16 KiB)

Bug LFV-163 · Area: codecs / stored fields (Lucene90StoredFieldsFormat javadoc vs Lucene90CompressingStoredFieldsFormat) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the default codec's stored-fields documentation (Lucene90StoredFieldsFormat, 'Known limitations'), which says only individual documents larger than 2^31 - 2^14 = 2,147,467,264 bytes are unsupported, and you index a document of 2,147,403,644 serialized bytes, below that limit, right after a small document. addDocument fails with ArithmeticException ('integer overflow') and IndexWriter closes itself, while the same big document on its own is indexed. The writer does what the class it delegates to, Lucene90CompressingStoredFieldsFormat, documents (at most 2^31 - chunkSize bytes, with chunks of 81,920 or 491,520 bytes today); the outer javadoc's number was right only when chunks were 16 KiB and was never updated.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/codecs/lucene90/TestStoredFieldsChunkEndOffsetOverflow.java](src/org/apache/lucene/codecs/lucene90/TestStoredFieldsChunkEndOffsetOverflow.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene90.TestStoredFieldsChunkEndOffsetOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/storedfields-compressing/](../../models/storedfields-compressing/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
