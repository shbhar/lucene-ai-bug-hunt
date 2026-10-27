# MatchingFacetSetsCounts decodes the facet-set binary doc value from position 0 of BytesRef.bytes instead of BytesRef.offset, so a value that a reader returns as a slice of a larger array is counted from the wrong bytes (silently wrong facet-set counts, or NegativeArraySizeException / ArrayIndexOutOfBoundsException)

Bug LFV-405 · Area: facet / facet sets (MatchingFacetSetsCounts.count, a BinaryDocValues consumer; reached through MemoryIndex, custom readers and the backward-codecs Lucene80 BEST_COMPRESSION doc-values producer) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You count facet sets with MatchingFacetSetsCounts over a reader that hands back a document's facet-set binary doc value as a BytesRef slice of a larger array (offset != 0), for example a MemoryIndex holding a value that was packed into a shared buffer, and expect the same counts as for a compact copy of the same bytes. Instead count reads the dimension count from bytes[0] and the dimension values from bytes[4..length) of the backing array, ignoring the offset, so the document is counted under whatever bytes start that array: a document whose set is (3,4), stored right after an encoding of (1,2), is counted as (1,2)=1 and (3,4)=0, with no error. When the leading bytes do not decode to a sane dimension count, the MatchingFacetSetsCounts constructor throws NegativeArraySizeException or ArrayIndexOutOfBoundsException instead, while the same value indexed through IndexWriter with the default codec is counted correctly.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: count reads the dimension count from bytes[0] and the dimension values from bytes[4..length) of the backing array, ignoring the offset, so the document is counted under whatever bytes start that array: a document whose set is (3,4), stored right after an encoding of (1,2), is counted as (1,2)=1 and (3,4)=0, with no error.

## Files

- [src/org/apache/lucene/facet/facetset/TestMatchingFacetSetsCountsSliceValue.java](src/org/apache/lucene/facet/facetset/TestMatchingFacetSetsCountsSliceValue.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.facetset.TestMatchingFacetSetsCountsSliceValue
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
