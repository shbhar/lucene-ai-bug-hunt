# DirectoryTaxonomyWriter.replaceTaxonomy is not failure-atomic: if addIndexes throws, the taxonomy is already emptied but the writer keeps its old ordinal counter and cache, so later addCategory calls and the next commit or close store an empty or broken taxonomy

Bug LFV-978 · Area: facet / taxonomy writer (DirectoryTaxonomyWriter.replaceTaxonomy failure path: deleteAll before addIndexes, writer state reset only on success) · Priority: low · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-978.html](../../bugs/LFV-978.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call replaceTaxonomy on a DirectoryTaxonomyWriter to swap in a rebuilt taxonomy, the call throws (in the repro an IOException while a file of a valid, closed source is read, or IndexNotFoundException because the source holds no index), and the application then closes the writer as usual or keeps using it. You expect the failed call to leave the taxonomy that the writer and its last commit describe, so that addCategory returns 'its existing ordinal' for an existing category (TaxonomyWriter.java:53) and the root keeps ordinal 0. Instead replaceTaxonomy has already emptied the internal index with deleteAll while the writer keeps its old ordinal counter, cache and parent arrays: close commits a taxonomy of size 0 (expected 4), a kept-open writer returns ordinal 2 for dim/a although the commit no longer has it and stores a new label with ordinal 4 at docID 0, and over a main index that still refers to the old ordinals the facet counts a=2 b=1 x=1 y=1 come out as x=3 y=2.

The test shows that a `DirectoryTaxonomyWriter.replaceTaxonomy` call that throws an IOException while reading a valid, closed source taxonomy has already emptied the taxonomy (deleteAll ran first), while the writer keeps answering from its old cache, and close() commits that emptied index.

## Expected vs actual

Expected: `addCategory(dim/a)` after the failure returns dim/a's existing ordinal (2) and the commit still has dim/a there (a writer that closes itself on the failure is also accepted). Actual: addCategory returns 2, but a reader on the commit gives -1 for dim/a.

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestReplaceTaxonomyFailure.java](src/org/apache/lucene/facet/taxonomy/directory/TestReplaceTaxonomyFailure.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestReplaceTaxonomyFailure
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
