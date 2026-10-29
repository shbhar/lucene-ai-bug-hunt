# ReindexingEnrichedDirectoryTaxonomyWriter.reindexWithNewOrdinalData rebuilds only the last committed taxonomy, so categories added since that commit are dropped and their ordinals are later reused for other categories

Bug LFV-481 · Area: facet / taxonomy (ReindexingEnrichedDirectoryTaxonomyWriter.reindexWithNewOrdinalData) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-481.html](../../bugs/LFV-481.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document with the category Author/Bob through a ReindexingEnrichedDirectoryTaxonomyWriter after its last commit, then call reindexWithNewOrdinalData to refresh the per-category data, whose javadoc promises 'The ordinals themselves will be preserved, so the caller does not need to update references to them in the main index'. Instead the method rebuilds the taxonomy from a reader of the last commit, so Author/Bob disappears (the writer's getSize drops from 4 to 3) and its ordinal 3 goes to the next new category, Author/Carol. After both indexes are committed, the main index's Bob document is counted as Carol (Alice=1 Carol=1 instead of Alice=1 Bob=1) with no error; on a taxonomy that was never committed the call throws IndexNotFoundException.

ReindexingEnrichedDirectoryTaxonomyWriter.reindexWithNewOrdinalData drops a category added after the last taxonomy commit, although its javadoc promises 'The ordinals themselves will be preserved'.

## Expected vs actual

Expected Author/Bob to keep ordinal 2 in the taxonomy the call commits; main gives getOrdinal(Author/Bob) = -1.

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestReindexDropsUncommittedCategories.java](src/org/apache/lucene/facet/taxonomy/directory/TestReindexDropsUncommittedCategories.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestReindexDropsUncommittedCategories
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/taxonomy-index-pair-tla/](../../models/taxonomy-index-pair-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
