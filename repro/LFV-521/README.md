# DirectoryTaxonomyWriter gives an existing category a second ordinal after an addCategory call fails with an IOException while the writer opens its near-real-time reader, because the cache stays marked complete or the reader stays stale

Bug LFV-521 · Area: facet / taxonomy writer (DirectoryTaxonomyWriter.addCategory exception safety: cacheIsComplete and reader-manager freshness after a failed near-real-time reader open) · Priority: low · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-521.html](../../bugs/LFV-521.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index facet categories with a default DirectoryTaxonomyWriter; one addCategory call throws an IOException because the writer could not open its near-real-time reader (for example on the call whose new category makes the 4000-entry cache evict), and you keep using the writer, as IndexWriter allows after a non-fatal exception. You expect a later addCategory of a category that is already in the taxonomy to return its existing ordinal, as TaxonomyWriter.addCategory promises. Instead the writer still trusts its cache as complete (or its old reader), indexes the category again and returns a new ordinal (l0: 4001 instead of 1; a retried 'new': 14 instead of 13), and commit persists one label under two ordinals, so its facet counts are split between them.

The test shows that after one `addCategory` fails because the eviction refresh of the default DirectoryTaxonomyWriter's NRT reader throws an IOException, the writer still trusts its cache as complete and indexes an existing, evicted category again.

## Expected vs actual

Expected `addCategory(l1)` to return l1's existing ordinal 1 (TaxonomyWriter.addCategory contract); actual: a new ordinal, `expected:<1> but was:<4001>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyWriterFailedRefreshDuplicatesOrdinal.java](src/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyWriterFailedRefreshDuplicatesOrdinal.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestTaxonomyWriterFailedRefreshDuplicatesOrdinal
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/taxonomy-writer-concurrent-add/](../../models/taxonomy-writer-concurrent-add/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
