# TaxonomyMergeUtils.merge (OrdinalMappingLeafReader) does not re-map the facet ordinals stored in the index field chosen by an overridden FacetsConfig#getDefaultDimConfig, so merged documents silently point at other categories

Bug LFV-722 · Area: facet / taxonomy (TaxonomyMergeUtils.merge, OrdinalMappingLeafReader; FacetsConfig default DimConfig) · Priority: medium · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass FacetsConfig so that getDefaultDimConfig puts every unconfigured dimension into the index field myfacets, index color/red into a destination index and size/large into a source index (each with its own taxonomy), and merge the source in with TaxonomyMergeUtils.merge, passing that FacetsConfig. You expect facet counts over myfacets to show color: red=1 and size: large=1, as they do when the same field is set with setIndexFieldName. Instead the merged document keeps its source ordinal, which in the destination taxonomy is color/red, so color shows red=2 and size is missing, with no error, because OrdinalMappingLeafReader re-maps only the fields of getDimConfigs plus the static DEFAULT_DIM_CONFIG field $facets, not the config's own default field.

## Expected vs actual

- Expected: You expect facet counts over myfacets to show color: red=1 and size: large=1, as they do when the same field is set with setIndexFieldName.
- Actual: The merged document keeps its source ordinal, which in the destination taxonomy is color/red, so color shows red=2 and size is missing, with no error, because OrdinalMappingLeafReader re-maps only the fields of getDimConfigs plus the static DEFAULT_DIM_CONFIG field $facets, not the config's own default field.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestMergeDefaultDimConfigField.java](src/org/apache/lucene/facet/taxonomy/TestMergeDefaultDimConfigField.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.facet.taxonomy.TestMergeDefaultDimConfigField
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
