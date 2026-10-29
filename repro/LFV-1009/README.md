# FacetSetsField.create sizes its buffer from the first FacetSet's sizePackedBytes, so a list of custom variable-size facet sets (e.g. compressed, which the FacetSet javadoc and FacetSets.adoc allow) throws ArrayIndexOutOfBoundsException when a later set packs to more bytes than the first, and whether a document can be built depends only on the order of its sets

Bug LFV-1009 · Area: facet / facet sets (FacetSetsField packing of custom FacetSet encodings) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a FacetSet subclass that compresses its value (one length byte plus the value's significant bytes, so sizePackedBytes is 2 for the value 7 and 7 for 2^40) with a matching FacetSetDecoder, and you expect FacetSetsField.create("f", set(7), set(2^40)) to work just like the same call with the two sets swapped. Instead create allocates 4 + the first set's sizePackedBytes times the number of sets (8 bytes) and then writes each set's own packed bytes (13 needed), so the second set's packValues throws ArrayIndexOutOfBoundsException 'Index 8 out of bounds for length 8'. With the larger set first the same two sets pack, index and count correctly.

The test shows that `FacetSetsField.create` sizes its buffer from the first set's `sizePackedBytes()`, so a custom variable-size `FacetSet` that packs larger than the first set overflows it.

## Expected vs actual

Expected `create("f", set(7), set(2^40))` (2 and 7 packed bytes) to return a field; actual: `ArrayIndexOutOfBoundsException: Index 8 out of bounds for length 8` from `FacetSetsField.toPackedValues`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/facetset/TestFacetSetsFieldVariableSizeSets.java](src/org/apache/lucene/facet/facetset/TestFacetSetsFieldVariableSizeSets.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.facetset.TestFacetSetsFieldVariableSizeSets
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
