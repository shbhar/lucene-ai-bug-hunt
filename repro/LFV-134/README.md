# A rejected IndexWriter#updateDocValues call (an index-sort field, SORTED or SORTED_SET doc values, or a bad later field in the same call) first registers a never-seen field as doc-values-only, so later legal documents that index that field are rejected until the writer is reopened

Bug LFV-134 · Area: index / IndexWriter doc-values updates and the writer-wide field registry (FieldInfos.FieldNumbers) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter#updateDocValues (or softUpdateDocument(s) / tryUpdateDocValue) with a field that no document has used yet, and the call is correctly rejected with IllegalArgumentException, for example because the field is part of the index sort or because SORTED and SORTED_SET doc values cannot be updated. You expect the rejected call to leave the writer unchanged, so that the first real document with that field defines its schema, as on a fresh writer and as the document path does when it rejects a document for its index-sort doc-values type. Instead buildDocValuesUpdate has already registered the field as doc-values-only in the writer-wide FieldNumbers map before those checks run, and nothing undoes it: for the rest of the writer session a legal document such as LongField('price') or StringField('tag') plus SortedDocValuesField('tag') is refused ('cannot change field "price" from points dimensionCount=0... to inconsistent dimensionCount=1'), and getFieldNames lists a field that no segment or accepted operation has.

The test shows that a rejected `IndexWriter#updateDocValues` call (SORTED doc values cannot be updated) on a never-seen field "tag" still registers "tag" as a doc-values-only field in the writer's schema (default config).

## Expected vs actual

Expected the first real document with `StringField("tag")` + `SortedDocValuesField("tag")` to be accepted, as on a fresh writer; actual: `IllegalArgumentException: cannot change field "tag" from index options=NONE to inconsistent index options=DOCS`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestRejectedDocValuesUpdateRegistersField.java](src/org/apache/lucene/index/TestRejectedDocValuesUpdateRegistersField.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestRejectedDocValuesUpdateRegistersField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/sortednumeric-dv-update/](../../models/sortednumeric-dv-update/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
