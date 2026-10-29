# DirectoryTaxonomyReader.getBulkPath overwrites and duplicates entries of its ordinals argument when a cached ordinal comes before an uncached one (since 10.5.0), although the TaxonomyReader javadoc says the method may only reorder them (documentation/contract defect; the returned labels are correct)

Bug LFV-797 · Area: facet / taxonomy (DirectoryTaxonomyReader.getBulkPath: side effect on the ordinals argument of the category-cache path added by #15711) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You pass an array of taxonomy ordinals to DirectoryTaxonomyReader.getBulkPath, whose inherited TaxonomyReader javadoc says the method 'may change (reorder elements) its parameter', so you expect the array to hold the same ordinals afterwards, perhaps in another order, as it does in 9.0.0 to 10.4.0. Since 10.5.0 (#15711, which made getBulkPath use the category cache) the method packs the uncached ordinals into the front of your array and sorts only that prefix, so a cached ordinal that comes before an uncached one is overwritten and another entry is duplicated. With ordinal 2 cached, getBulkPath(new int {3, 2, 1}) returns the right labels [c, b, a] but leaves the array as [1, 3, 1].

The test shows (javadoc/contract defect) that `DirectoryTaxonomyReader.getBulkPath` overwrites entries of its argument when a cached ordinal comes before an uncached one, although the `TaxonomyReader` javadoc says it may only reorder them.

## Expected vs actual

Expected: `{2, 1}` (with ordinal 2 cached) still holds 1 and 2 in some order after the call. Actual (-ea and -da): it holds `[1, 1]`. The returned labels are correct.

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestGetBulkPathArgumentOnlyReordered.java](src/org/apache/lucene/facet/taxonomy/directory/TestGetBulkPathArgumentOnlyReordered.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestGetBulkPathArgumentOnlyReordered
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
