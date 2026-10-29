# OrdinalMappingLeafReader's class javadoc example for re-mapping facet ordinals during an index merge does not compile on any release since 5.0.0: it uses IndexWriterConfig(Version, Analyzer) and addIndexes(IndexReader...), both removed in 5.0.0, a 2-argument constructor the class never had, and a static-style call to the instance method addTaxonomy (documentation defect)

Bug LFV-1033 · Area: facet / taxonomy merge (OrdinalMappingLeafReader class javadoc example) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The OrdinalMappingLeafReader javadoc says 'For re-mapping the ordinals during index merge, do the following:' and shows a recipe: merge the old taxonomy into the new one, then wrap each old segment in an OrdinalMappingLeafReader and add it to the new index. Copied into code it does not compile on any release since 5.0.0: it builds an IndexWriterConfig from a Version and passes a MultiReader to addIndexes (both removed in 5.0.0), calls a 2-argument OrdinalMappingLeafReader constructor (the only one also takes the source FacetsConfig), and calls the instance method addTaxonomy as if it were static. Written the way TaxonomyMergeUtils.merge does it (destTaxoWriter.addTaxonomy, IndexWriterConfig(analyzer), the 3-argument constructor wrapped with SlowCodecReaderWrapper.wrap, addIndexes(CodecReader...)), the same steps compile and give correct facet counts, so only the javadoc is wrong.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestOrdinalMappingLeafReaderJavadocExample.java](src/org/apache/lucene/facet/taxonomy/TestOrdinalMappingLeafReaderJavadocExample.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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
     execute --select-class org.apache.lucene.facet.taxonomy.TestOrdinalMappingLeafReaderJavadocExample
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
