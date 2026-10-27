# Since 10.4.0 Lucene opens indexes created by Lucene 8 once Lucene 9 has rewritten every segment, but without Lucene 9's field-consistency leniency for them (LUCENE-10518), so FieldExistsQuery returns documents that lack the doc value (or throws) and IndexWriter refuses the index

Bug LFV-490 · Area: search / FieldExistsQuery and index / FieldInfos field-schema consistency on Lucene-8-created indexes (SegmentInfos per-segment open gate, #14607) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You have an index first created by Lucene 8 that Lucene 9 later rewrote completely (every live segment is 9.x), in which a field is indexed in every document but has a doc value in only some (Lucene 9 allowed that only for such 8.x-created indexes), and you open it with Lucene 10.4.0 or later, which accepts it since #14607. You expect FieldExistsQuery on that field to return only the documents with the doc value, as Lucene 9.12.3 does and Lucene 9's own TestFieldExistsQuery#testOldIndices asserts, and IndexWriter to open whatever DirectoryReader.open accepts. Instead, once the documents share a segment FieldExistsQuery silently rewrites to MatchAllDocsQuery and also returns the document without the doc value (hits [d1, d2] instead of [d1], count 2 instead of 1), while they sit in separate segments it throws IllegalStateException, and IndexWriter and FieldInfos.getMergedFieldInfos throw IllegalArgumentException 'cannot change field "my_field" from doc values type=BINARY to inconsistent doc values type=NONE'.

## Expected vs actual

- Expected: You expect FieldExistsQuery on that field to return only the documents with the doc value, as Lucene 9.12.3 does and Lucene 9's own TestFieldExistsQuery#testOldIndices asserts, and IndexWriter to open whatever DirectoryReader.open accepts.
- Actual: Once the documents share a segment FieldExistsQuery silently rewrites to MatchAllDocsQuery and also returns the document without the doc value (hits [d1, d2] instead of [d1], count 2 instead of 1), while they sit in separate segments it throws IllegalStateException, and IndexWriter and FieldInfos.getMergedFieldInfos throw IllegalArgumentException 'cannot change field "my_field" from doc values type=BINARY to inconsistent doc values type=NONE'.

## Files

- [src/org/apache/lucene/backward_index/TestPre9CreatedIndexSchemaConsistency.java](src/org/apache/lucene/backward_index/TestPre9CreatedIndexSchemaConsistency.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.backward_index.TestPre9CreatedIndexSchemaConsistency
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
