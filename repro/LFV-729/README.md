# A facet dimension set to DrillDownTermsIndexing.NONE that shares its index field ($facets by default) with term-indexing dimensions makes FacetsConfig emit two schemas for that field, so since 9.0.0 IndexWriter rejects documents of one kind once a document of the other kind is indexed, and after a commit until the index is cleared or rebuilt

Bug LFV-729 · Area: facet / FacetsConfig drill-down term indexing (DrillDownTermsIndexing.NONE in a shared indexFieldName) vs the core per-field schema check (IndexingChain FieldSchema, FieldInfo.verifySameIndexOptions) · Priority: medium · Impact: wrong error · Novelty: new

Bug page: [../../bugs/LFV-729.html](../../bugs/LFV-729.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call config.setDrillDownTermsIndexing("rating", NONE), leave every dimension in the default index field $facets, and index one document with only FacetField("rating", "5") and then one with FacetField("author", "Bob"); you expect both to be indexed, as they are on Lucene 8.9 to 8.11 and as they are when rating gets its own index field. FacetsConfig.build gives the first document a doc-values-only $facets (NONE adds no drill-down terms) and the second a $facets with drill-down terms, and since 9.0 Lucene requires one schema per field, so the second addDocument throws IllegalArgumentException 'Inconsistency of field data structures across documents for field [$facets] of doc [1]. index options: expected 'NONE', but it has 'DOCS'' (with taxonomy facets the reverse order fails the same way). After a commit, a new IndexWriter keeps rejecting documents of the other kind ('cannot change field "$facets" from index options=NONE to inconsistent index options=DOCS') until the index is cleared or rebuilt.

## Expected vs actual

- Expected: Both documents indexed in every order, `rating/5` and `author/Bob` counted once each, and `author` drills down.
- Actual: The second `addDocument` throws `IllegalArgumentException` ("... field [$facets]... index options: expected 'NONE', but it has 'DOCS'", or the reverse); after a commit: "cannot change field "$facets"...".

## Files

- [src/org/apache/lucene/facet/TestDrillDownNoneSharedField.java](src/org/apache/lucene/facet/TestDrillDownNoneSharedField.java) (JUnit test)
- [src/DrillDownNoneSharedFieldMain.java](src/DrillDownNoneSharedFieldMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.facet.TestDrillDownNoneSharedField

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" DrillDownNoneSharedFieldMain
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDrillDownTermsNoneSharedIndexField.java](TestDrillDownTermsNoneSharedIndexField.java)
- [TestDrillDownNoneSharedField.java](TestDrillDownNoneSharedField.java)
- [TestNoneDimSharedIndexField.java](TestNoneDimSharedIndexField.java)
- [DrillDownNoneHistoryMain.java](DrillDownNoneHistoryMain.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
