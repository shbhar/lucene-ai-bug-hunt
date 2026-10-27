# ParallelLeafReader stored fields ignore StoredFieldVisitor.Status.STOP: the later parallel readers still offer and deliver fields after the visitor answered STOP

Bug LFV-743 · Area: core / index (ParallelLeafReader and ParallelCompositeReader stored fields, StoredFieldVisitor protocol) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You split a document over two lockstep indexes (title in index A, price in index B), open them as a ParallelLeafReader, and load the document with a StoredFieldVisitor that answers STOP at title and YES otherwise, expecting the visit to end at title, as STOP promises ('stop processing any other fields for this document') and as a single index holding the same document does. Instead the second parallel reader still asks needsField(price) and delivers price=42 after the STOP, because ParallelLeafReader.storedFields.document hands the visitor to each parallel reader in turn and never checks its answer. ParallelCompositeReader builds ParallelLeafReaders, so it behaves the same.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The second parallel reader still asks needsField(price) and delivers price=42 after the STOP, because ParallelLeafReader.storedFields.document hands the visitor to each parallel reader in turn and never checks its answer.

## Files

- [src/org/apache/lucene/index/TestParallelLeafReaderStoredFieldsStop.java](src/org/apache/lucene/index/TestParallelLeafReaderStoredFieldsStop.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.index.TestParallelLeafReaderStoredFieldsStop
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
