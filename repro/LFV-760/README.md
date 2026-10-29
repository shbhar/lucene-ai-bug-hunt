# With an index sort, a document whose sort field is indexed without doc values (for example only as a StringField) is accepted by addDocument, and the next flush throws IllegalStateException and closes the IndexWriter, losing every uncommitted document (since 8.6.0)

Bug LFV-760 · Area: index / index sorting (IndexingChain index-sort field validation and flush-time sort) · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-760.html](../../bugs/LFV-760.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure an index sort on a field, for example new Sort(new SortField("sort", SortField.Type.STRING)), and add a document that carries that field only as a StringField, without the SortedDocValuesField that sorting reads, before any committed document has the field. You expect addDocument to reject the document up front, as it has rejected a doc-values field of the wrong type since LUCENE-8441 (7.5.0), or the field to count as missing. Instead addDocument accepts it, and the next flush or commit throws IllegalStateException 'unexpected docvalues type NONE for field 'sort' (expected=SORTED). Re-index with correct docvalues type.' and closes the IndexWriter, so every document added since the last commit is lost.

The test shows that with an index sort on `sort`, a document that has `sort` only as a `StringField` (no doc values) is accepted by `addDocument`, and the next commit throws and closes the `IndexWriter` (losing every uncommitted document).

## Expected vs actual

Expected `addDocument` to reject the document or the commit to succeed; actual on main: `commit` throws `IllegalStateException: unexpected docvalues type NONE for field 'sort' (expected=SORTED)` and `writer open=false`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/index/TestIndexSortFieldWithoutDocValues.java](src/org/apache/lucene/index/TestIndexSortFieldWithoutDocValues.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestIndexSortFieldWithoutDocValues
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
