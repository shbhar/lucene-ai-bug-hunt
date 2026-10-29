# A failed addIndexes(Directory...) leaves the incoming fields registered in the writer's field schema, so getFieldNames lists them and later valid documents that use those names differently are rejected until the writer is reopened

Bug LFV-124 · Area: index / IndexWriter addIndexes(Directory...) and the writer-wide field registry (FieldInfos.FieldNumbers) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter#addIndexes(Directory...) with an index whose first field a_new is new to the destination and whose field z_conflict clashes with the destination's schema; the call correctly throws IllegalArgumentException and adds no segment, as its javadoc promises ('either no indexes will have been added or they all will have been'). You expect the writer to be as it was before the call, so that a later document can use a_new with any schema, as it can after the writer is reopened and after the same failure in addIndexes(CodecReader...). Instead the writer keeps a_new registered with the incoming schema, because addIndexes(Directory...) registers each incoming FieldInfo in the writer-wide FieldNumbers map while it iterates and its failure path only deletes the copied files: getFieldNames lists a_new although no segment has it, and a document with NumericDocValuesField('a_new') is rejected ('cannot change field "a_new" from index options=DOCS to inconsistent index options=NONE') until the writer is reopened.

The test shows that a failed `IndexWriter#addIndexes(Directory...)` (schema conflict on "z_conflict", nothing added) leaves the incoming field "a_new", checked before the conflict, registered in the writer's schema (default config).

## Expected vs actual

Expected the first document of the destination that uses "a_new" (`NumericDocValuesField`) to be accepted, as after a reopen; actual: `IllegalArgumentException: cannot change field "a_new" from index options=DOCS to inconsistent index options=NONE`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestFailedAddIndexesRegistersField.java](src/org/apache/lucene/index/TestFailedAddIndexesRegistersField.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestFailedAddIndexesRegistersField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indexingchain-schema/](../../models/indexingchain-schema/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
