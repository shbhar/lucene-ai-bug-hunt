# DirectoryTaxonomyWriter gives every later category a wrong ordinal after IndexWriter rejects one enriched ordinal document: the rejected document keeps its docID but the writer's ordinal counter does not advance

Bug LFV-520 · Area: facet / taxonomy writer (DirectoryTaxonomyWriter.addCategoryDocument with the ordinal-document enrichment hook: ReindexingEnrichedDirectoryTaxonomyWriter / enrichOrdinalDocument) · Priority: low · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-520.html](../../bugs/LFV-520.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

An application adds its own fields to taxonomy ordinal documents with ReindexingEnrichedDirectoryTaxonomyWriter (or an enrichOrdinalDocument override), IndexWriter rejects one category's field (for example a term over 32766 bytes or a vector of a different dimension), addCategory throws IllegalArgumentException, and the application skips that category and keeps going. You expect only that category to be missing and every later addCategory to return the category's ordinal, the one a taxonomy reader reports for it, with an existing category keeping its ordinal (TaxonomyWriter.addCategory). Instead IndexWriter keeps the rejected document as a deleted document that still uses up a docID while the writer's ordinal counter does not move, so every later category gets an ordinal one below its docID: readers resolve it to the previous category (even the rejected label), facet counts land under the wrong labels or throw ArrayIndexOutOfBoundsException or CorruptIndexException, and a reopened writer returns 3 instead of 2 for a category that already exists.

The test shows that after IndexWriter rejects one enriched ordinal document (a 40,000-byte term), DirectoryTaxonomyWriter returns an ordinal for the next category that the taxonomy reader maps to a different category.

## Expected vs actual

Expected: addCategory(b) returns b's ordinal in the taxonomy (2), or the writer refuses further adds. Actual (-ea and -da): it returns 1, the docID of the rejected, deleted document.

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyWriterRejectedOrdinalDocument.java](src/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyWriterRejectedOrdinalDocument.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestTaxonomyWriterRejectedOrdinalDocument
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/taxonomy-writer-concurrent-add/](../../models/taxonomy-writer-concurrent-add/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
