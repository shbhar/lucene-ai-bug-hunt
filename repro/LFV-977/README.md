# ReindexingEnrichedDirectoryTaxonomyWriter.reindexWithNewOrdinalData is not failure-atomic: when the ordinal-data appender throws part-way, the taxonomy has already been deleted and only partly re-added, and a following close commits it, so main-index ordinals lose their labels or are handed to other categories

Bug LFV-977 · Area: facet / taxonomy (ReindexingEnrichedDirectoryTaxonomyWriter.reindexWithNewOrdinalData) · Priority: low · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-977.html](../../bugs/LFV-977.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call reindexWithNewOrdinalData on a ReindexingEnrichedDirectoryTaxonomyWriter with a new ordinal-data appender, the appender throws for one category (in the repro an IllegalStateException on d/b; an appender that reads the last label component throws on the root category, which reaches the appender only during a reindex), and the application then closes the writer as usual. You expect the method's promise 'The ordinals themselves will be preserved, so the caller does not need to update references to them in the main index' to hold, or at least the last committed taxonomy to survive the failed call. Instead the method has already deleted the whole taxonomy and re-added only the categories before the failing one, and close commits that: the taxonomy shrinks from 5 to 3 categories, d/b and d/c resolve to -1 while main-index documents still carry their ordinals 3 and 4, and if the writer is kept in use new categories take those ordinals, so facet counts a=2 b=1 w=1 z=1 come out as w=3 z=2 with no error (other layouts make FastTaxonomyFacetCounts throw ArrayIndexOutOfBoundsException).

The test shows that ReindexingEnrichedDirectoryTaxonomyWriter.reindexWithNewOrdinalData deletes the taxonomy before re-adding it, so when the ordinal-data appender throws part-way, a following close() commits a half-rebuilt taxonomy and a main-index ordinal loses its label.

## Expected vs actual

Expected: after the failed reindex and close(), d/a still has ordinal 2 (method javadoc: "The ordinals themselves will be preserved"); actual: getOrdinal(d/a) = -1 (`expected:<2> but was:<-1>`).

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestReindexOrdinalDataFailureAtomicity.java](src/org/apache/lucene/facet/taxonomy/directory/TestReindexOrdinalDataFailureAtomicity.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestReindexOrdinalDataFailureAtomicity
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
