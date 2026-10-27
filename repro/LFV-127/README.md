# IndexWriter#addBatch marks the frozen FieldType of an earlier rejected document as validated, so later documents with that type skip the per-field schema check (a wrong-dimension vector is stored truncated)

Bug LFV-127 · Area: index / IndexingChain per-field schema validation (frozen-FieldType cache) x columnar addBatch · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document whose 8-dimensional KnnFloatVectorField (built from one shared frozen FieldType) does not match the field's 4 dimensions, and Lucene correctly rejects it; you then index an IndexWriter#addBatch with a 4-dimensional vector column for the same field and add the same kind of 8-dimensional document again. You expect the same IllegalArgumentException, because Lucene requires a field to keep the same data structures in every document and checks each document against them. Instead the batch marks the rejected document's FieldType as already validated, so until the next flush every document with that FieldType instance skips the check: the 8-dimensional vector is accepted and stored as its first 4 values (CheckIndex clean), and a SORTED value for a NUMERIC doc-values field fails with ClassCastException instead of the schema error.

## Expected vs actual

- Expected: You expect the same IllegalArgumentException, because Lucene requires a field to keep the same data structures in every document and checks each document against them.
- Actual: The batch marks the rejected document's FieldType as already validated, so until the next flush every document with that FieldType instance skips the check: the 8-dimensional vector is accepted and stored as its first 4 values (CheckIndex clean), and a SORTED value for a NUMERIC doc-values field fails with ClassCastException instead of the schema error.

## Files

- [src/org/apache/lucene/index/TestAddBatchCachesRejectedFieldType.java](src/org/apache/lucene/index/TestAddBatchCachesRejectedFieldType.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestAddBatchCachesRejectedFieldType
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indexingchain-schema/](../../models/indexingchain-schema/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
