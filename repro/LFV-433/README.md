# LongValueFacetCounts count-all constructors that take a LongValuesSource or MultiLongValuesSource count deleted documents, although they promise the result of a MatchAllDocsQuery (LUCENE-10070 fixed only the field-based constructor)

Bug LFV-433 · Area: facet / LongValueFacetCounts (count-all constructors with a LongValuesSource or MultiLongValuesSource) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You delete or update documents and then count all values of a long field with new LongValueFacetCounts("field", LongValuesSource.fromLongField("field"), reader), or with a MultiLongValuesSource, and expect what both constructors' javadoc promises, 'the same result as computing facets on a MatchAllDocsQuery', which sees only live documents. Instead the two value-source count-all loops visit every doc id below maxDoc and never check getLiveDocs, so deleted documents are counted: on upstream's own testCountAll index (10 documents, 1 deleted) you get value=10 and '0 (5)' instead of value=9 and '0 (4)', and after updateDocument the replaced value is still listed as a label. LUCENE-10070 fixed exactly this in 8.11.0 for the field-based count-all constructor of the same class, but left the value-source paths out.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The two value-source count-all loops visit every doc id below maxDoc and never check getLiveDocs, so deleted documents are counted: on upstream's own testCountAll index (10 documents, 1 deleted) you get value=10 and '0 (5)' instead of value=9 and '0 (4)', and after updateDocument the replaced value is still listed as a label.

## Files

- [src/org/apache/lucene/facet/TestLongValueFacetCountsCountAllDeletes.java](src/org/apache/lucene/facet/TestLongValueFacetCountsCountAllDeletes.java) (JUnit test)
- [src2/org/apache/lucene/facet/TestLongValueFacetCountsCountAllDeletes.java](src2/org/apache/lucene/facet/TestLongValueFacetCountsCountAllDeletes.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestLongValueFacetCountsCountAllDeletes
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestLongValueFacetCountsCountAllDeletes
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
