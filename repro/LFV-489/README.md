# SearcherTaxonomyManager's IndexWriter (near-real-time) constructors open the taxonomy reader before the index reader, so a manager created while another thread indexes can hand out a first pair whose searcher holds documents with categories the paired taxonomy reader does not know (facet counting throws ArrayIndexOutOfBoundsException until the index changes and the manager refreshes)

Bug LFV-489 · Area: facet / taxonomy (SearcherTaxonomyManager NRT constructors: index and taxonomy reader open order) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents with taxonomy facets in one thread and meanwhile create a SearcherTaxonomyManager from the IndexWriter and DirectoryTaxonomyWriter. You expect its first SearcherAndTaxonomy, documented as 'a matched pair', to know every category its searcher's documents use, because TaxonomyReader's javadoc says to open the taxonomy after the main index and the manager's own refresh does exactly that. Instead the constructor opens the taxonomy reader first, so a document indexed in between is visible to the searcher while its category is missing from the taxonomy reader: getOrdinal returns -1, FastTaxonomyFacetCounts throws ArrayIndexOutOfBoundsException, and maybeRefresh keeps the broken pair until the index changes again.

## Expected vs actual

- Expected: You expect its first SearcherAndTaxonomy, documented as 'a matched pair', to know every category its searcher's documents use, because TaxonomyReader's javadoc says to open the taxonomy after the main index and the manager's own refresh does exactly that.
- Actual: The constructor opens the taxonomy reader first, so a document indexed in between is visible to the searcher while its category is missing from the taxonomy reader: getOrdinal returns -1, FastTaxonomyFacetCounts throws ArrayIndexOutOfBoundsException, and maybeRefresh keeps the broken pair until the index changes again.

## Files

- [src/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCtorOrder.java](src/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCtorOrder.java) (JUnit test)
- [src2/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCtorOrder.java](src2/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCtorOrder.java) (JUnit test)
- [src3/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCtorOrder.java](src3/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCtorOrder.java) (JUnit test)
- [src/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCtorRacePlain.java](src/org/apache/lucene/facet/taxonomy/TestSearcherTaxonomyManagerCtorRacePlain.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')
javac -d classes-src3 -cp "$CP" $(find src3 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.TestSearcherTaxonomyManagerCtorOrder
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.TestSearcherTaxonomyManagerCtorOrder
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src3:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.TestSearcherTaxonomyManagerCtorOrder

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.TestSearcherTaxonomyManagerCtorRacePlain
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/taxonomy-index-pair-tla/](../../models/taxonomy-index-pair-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
