# A doc-values update racing IndexWriter.deleteAll can be queued after deleteAll has cleared its field from the writer's field schema; applying it then makes every commit, NRT reopen and close fail (a tragic close with assertions on), or, if a document added in between gives the field another doc-values type, commits a segment whose field cannot be read

Bug LFV-635 · Area: index / IndexWriter doc-values updates vs deleteAll (writer-wide field schema, FieldInfos.FieldNumbers) · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

One thread calls deleteAll on an IndexWriter while another thread updates a doc-values field with updateDocValues, updateNumericDocValue or softUpdateDocument. You expect the update either to be dropped with everything else (if it came first) or to apply to the documents indexed after deleteAll, as on a fresh writer. Instead, when the update checked its field before deleteAll cleared the writer's field schema and is queued just after, the writer can no longer commit, refresh or close (NullPointerException with assertions off, a tragic AssertionError with them on), and when a document added in between gives the field another doc-values type, the commit succeeds but writes a segment whose field cannot be read.

## Expected vs actual

- Expected: You expect the update either to be dropped with everything else (if it came first) or to apply to the documents indexed after deleteAll, as on a fresh writer.
- Actual: When the update checked its field before deleteAll cleared the writer's field schema and is queued just after, the writer can no longer commit, refresh or close (NullPointerException with assertions off, a tragic AssertionError with them on), and when a document added in between gives the field another doc-values type, the commit succeeds but writes a segment whose field cannot be read.

## Files

- [src/org/apache/lucene/index/TestDeleteAllDuringDocValuesUpdate.java](src/org/apache/lucene/index/TestDeleteAllDuringDocValuesUpdate.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestDeleteAllDuringDocValuesUpdate
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
