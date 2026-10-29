# When a dense LongColumn's values cursor throws during addBatch, the NUMERIC/SORTED_NUMERIC doc values it already delivered stay buffered with no document, so later documents of that field in the same segment are stored with another document's value

Bug LFV-097 · Area: index / columnar addBatch doc values (dense NUMERIC and SORTED_NUMERIC writers) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-097.html](../../bugs/LFV-097.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a dense LongColumn as NUMERIC or SORTED_NUMERIC doc values with IndexWriter#addBatch, its values cursor throws part-way (for example after 255 earlier documents, a 2-document batch whose cursor fails on its 2nd value), and you keep adding documents to the same writer. You expect what Lucene's own recovery test asserts: the exception propagates, the batch's documents are deleted, and a document added afterwards with value 42 reads back 42. Instead the values the cursor delivered before the throw stay in the doc-values buffer with no document attached, so the good document reads back -1 (a value of the rejected batch) and every later document of that field in the segment is shifted by the same number of values, which survives commit, reopen and forceMerge(1) while CheckIndex finds nothing wrong.

The test shows that when a dense NUMERIC `LongColumn`'s values cursor throws during `addBatch` after 255 earlier documents of the field, the value it already delivered stays buffered with no document, so the next document of the field is stored with that value.

## Expected vs actual

Expected the document added after the failed batch to read back its own value 42; actual: `expected:<42> but was:<-1>` (-1 is the failed batch's value), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestDenseLongColumnFailure.java](src/org/apache/lucene/index/TestDenseLongColumnFailure.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestDenseLongColumnFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/columnar-addbatch/](../../models/columnar-addbatch/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
