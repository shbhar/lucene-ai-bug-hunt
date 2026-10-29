# TaxonomyFacetLabels javadoc offers it as a replacement for stored fields, but for dimensions configured multi-valued plus hierarchical (or multi-valued plus requireDimCount) it also returns the ancestor paths and the bare dimension that FacetsConfig indexes only for counting (documentation/contract defect; every release since 8.7.0)

Bug LFV-529 · Area: facet / taxonomy (TaxonomyFacetLabels, per-document facet label retrieval) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add FacetField("Author", "Bob") and FacetField("Publish Date", "2010", "10", "15") to a document and read its labels back with TaxonomyFacetLabels, whose javadoc offers it as a way to skip stored fields for these values, so you expect [Author/Bob, Publish Date/2010/10/15]; that is what you get when both dimensions are single-valued. With Publish Date configured multi-valued and hierarchical you get [Author/Bob, Publish Date/2010, Publish Date/2010/10, Publish Date/2010/10/15], and with Author configured multi-valued with requireDimCount you get [Author, Author/Bob, Publish Date/2010/10/15]. FacetsConfig deliberately indexes those ancestor (and dimension) ordinals so that multi-valued counts are right, and the reader turns every indexed ordinal back into a label; the javadoc never says so.

The test shows that for a dimension configured multi-valued and hierarchical, `TaxonomyFacetLabels` (documented as a replacement for stored fields) returns the ancestor path that FacetsConfig indexes only for counting, next to the one label the document was given.

## Expected vs actual

Expected `[Date/2010/10]`; actual `[Date/2010, Date/2010/10]`, with `-ea` and `-da` (documentation/contract defect).

## Files

- [src/org/apache/lucene/facet/taxonomy/TestTaxonomyFacetLabelsMultiValuedHierarchical.java](src/org/apache/lucene/facet/taxonomy/TestTaxonomyFacetLabelsMultiValuedHierarchical.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.TestTaxonomyFacetLabelsMultiValuedHierarchical
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
