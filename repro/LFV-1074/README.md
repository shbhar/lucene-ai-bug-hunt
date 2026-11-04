# A deleteAll() that overlaps addIndexes clears the incoming fields from the writer's field schema, but addIndexes still publishes its segment, so a later document can give such a field another doc-values type and the committed index can no longer be opened by an IndexWriter

Finding LFV-1074 · Area: index / IndexWriter addIndexes vs deleteAll (writer-wide field schema, FieldInfos.FieldNumbers) · Priority: medium · Impact: index unusable · Novelty: new

Finding page: [../../bugs/LFV-1074.html](../../bugs/LFV-1074.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

One thread imports an index with IndexWriter.addIndexes while another thread calls deleteAll() on the same writer. You expect the outcome of one of the two orders: either the imported documents are deleted, or they stay and their fields keep their doc-values types, and in both cases the committed index opens again in a new IndexWriter. Instead deleteAll can clear the imported fields from the writer's schema while addIndexes still publishes the imported segment, so a later document can give an imported field another doc-values type, the commit succeeds, and the next new IndexWriter on that index throws IllegalArgumentException ('cannot change field "f" from doc values type=NUMERIC to inconsistent doc values type=SORTED').

## Expected vs actual

- Expected: A new `IndexWriter` opens the committed index, which holds 1 document in either legal order.
- Actual: `new IndexWriter(dir, new IndexWriterConfig())` throws `IllegalArgumentException: cannot change field "f" from doc values type=NUMERIC to inconsistent doc values type=SORTED`.

## Files

- [src/org/apache/lucene/index/TestDeleteAllDuringAddIndexes.java](src/org/apache/lucene/index/TestDeleteAllDuringAddIndexes.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDeleteAllDuringAddIndexes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDeleteAllDuringAddIndexes.java](TestDeleteAllDuringAddIndexes.java)

Formal models and scripts this finding's part of the hunt used: [../../models/deleteall-during-addindexes-keeps-imported-field-schema/](../../models/deleteall-during-addindexes-keeps-imported-field-schema/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
