# DirectoryTaxonomyWriter indexes a facet label that contains an unpaired surrogate a second time, under a new ordinal, once its writer cache has been filled from the taxonomy index, because the cache compares the label's Java string while the index stores its UTF-8 term with the surrogate replaced by U+FFFD; the taxonomy then holds two categories with one path and facet counts for that value are split

Bug LFV-541 · Area: facet / taxonomy writer (DirectoryTaxonomyWriter category identity: writer cache key vs taxonomy index term, perhapsFillCache) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-541.html](../../bugs/LFV-541.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a facet value with an unpaired surrogate, for example FacetField("dim", "x\uD83D") built from a string cut inside an emoji, in one writer session and the same value again in a later session, with the default DirectoryTaxonomyWriter; you expect the existing ordinal back, as TaxonomyWriter.addCategory promises, and one facet entry with count 2. Instead, once the second session has filled its cache from the index (after 11 new categories by default), the cached key is the stored form "x\uFFFD", the original string misses, the writer trusts its now complete cache and indexes the label again (ordinal 14 instead of 2), so the facet result shows x\uFFFD (1) twice. The duplicate stays in the taxonomy for good; within one fresh session the writer likewise gives "x\uD83D" and "x\uFFFD" ordinals 2 and 3, although the taxonomy reader resolves both to 2.

## Expected vs actual

- Expected: The cut label keeps ordinal 2 in session 2, the value is one facet entry `x� (2)`, and the merged index counts both documents.
- Actual: Session 2 returns ordinal 14, the value shows as `x� (1)` twice, the writer and reader disagree (3 vs 2), and the merge counts 1 of 2 documents.

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyUnpairedSurrogateLabel.java](src/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyUnpairedSurrogateLabel.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestTaxonomyUnpairedSurrogateLabel
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestTaxonomyWriterUnpairedSurrogateLabel.java](TestTaxonomyWriterUnpairedSurrogateLabel.java)
- [TestTaxonomyUnpairedSurrogateLabel.java](TestTaxonomyUnpairedSurrogateLabel.java)
- [TestUnpairedSurrogateTaxonomyLabel.java](TestUnpairedSurrogateTaxonomyLabel.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
