# MultiDocValues norms view (also the merged reader passed to MergePolicy.OneMerge#reorder): advance(target) returns a document before target when target's segment has no norms for the field, so queries over that view can return duplicate or non-matching hits

Bug LFV-142 · Area: index / MultiDocValues merged norms view (also the merged reader passed to MergePolicy.OneMerge#reorder) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the norms of a multi-segment index as one merged iterator (MultiDocValues.getNormValues(reader, "body"), or LeafReader#getNormValues on the merged reader that IndexWriter hands to a custom OneMerge#reorder), where doc 0's segment has the text field "body", docs 1-2 sit in a segment without it and doc 3's segment has it again, and call advance(1) on a fresh iterator. You expect 3, the first document at or after the target that has a norm, as the DocIdSetIterator#advance contract says and as the merged numeric doc-values view of the same index returns. Instead advance(1) returns 0, a document before the target, because advance falls back to nextDoc before it records which segment comes next, so nextDoc restarts from the first segment; queries over that view inherit the backward step (FieldExistsQuery with intra-segment partitions returns [0, 0, 0, 3] instead of [0, 3], and a conjunction can return a document that fails one of its FILTER clauses).

The test shows that `MultiDocValues.getNormValues(reader, field).advance(target)` returns a document before the target when the target's segment has no norms for the field (3 segments: doc 0 has the field, doc 1 does not, doc 2 has it).

## Expected vs actual

Expected `advance(1)` to return 2, the first document &gt;= 1 with a norm; actual: 0 (`expected:<2> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestMultiDocValuesNormsAdvance.java](src/org/apache/lucene/index/TestMultiDocValuesNormsAdvance.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestMultiDocValuesNormsAdvance
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
