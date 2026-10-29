# FacetSets.adoc says MatchingFacetSetsCounts counts the documents each FacetSetMatcher matches, but it counts matching facet sets, so one document can add more than 1 to a matcher (documentation defect; the per-set code is intended and pinned by upstream tests)

Bug LFV-351 · Area: facet / facet sets (MatchingFacetSetsCounts child counts vs. FacetSets.adoc and the demo javadoc) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents that each hold several facet sets (the adoc's own example: one (genre, year) set per movie of an actor) and count them with MatchingFacetSetsCounts, which the user guide FacetSets.adoc says 'counts how many documents are matched by each of a list of FacetSetMatcher'. You expect each matcher's value to be a number of documents, never larger than FacetResult.value. Instead the code adds 1 for every matching facet set, so one document with the sets (Thriller, 2020) and (Thriller, 2021) gives 2 for a 'Thriller 2020-2022' range matcher while FacetResult.value is 1; upstream tests pin this per-set count, so the conservative fix is to correct the documentation, although maintainers could instead choose per-document counts.

The test shows (LFV-351, documentation defect) that FacetSets.adoc says `MatchingFacetSetsCounts` counts the documents each matcher matches, but one document with facet sets (1,2020) and (1,2021) counts 2 for a "Thriller 2020-2022" range matcher.

## Expected vs actual

Expected (per FacetSets.adoc:119-120): 1; actual (-ea and -da): 2, because the code adds 1 per matching facet set (MatchingFacetSetsCounts.java:128); the per-set code is intended, so the fix is the documentation.

## Files

- [src/org/apache/lucene/facet/facetset/TestMatchingFacetSetsCountsPerDocument.java](src/org/apache/lucene/facet/facetset/TestMatchingFacetSetsCountsPerDocument.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.facetset.TestMatchingFacetSetsCountsPerDocument
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
