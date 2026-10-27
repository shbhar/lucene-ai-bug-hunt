# DirectoryTaxonomyWriter counts a new ordinal in getSize before it stores that ordinal's parent, so a concurrent getParent(getSize - 1) returns 0 (the root) instead of the real parent, or throws ArrayIndexOutOfBoundsException

Bug LFV-512 · Area: facet / taxonomy writer (DirectoryTaxonomyWriter lock-free getSize/getParent reads vs a concurrent addCategory: nextID is published before TaxonomyIndexArrays.add stores the parent) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

One thread adds categories dim/0, dim/1,... to a DirectoryTaxonomyWriter while another thread reads n = getSize and calls getParent(n - 1). You expect the parent of that category (ordinal 1, 'dim'), because TaxonomyWriter documents that the taxonomy contains ordinals 0 through getSize-1 and that getParent throws only for an ordinal beyond the last available one. Instead addCategory makes the new ordinal visible through getSize a moment before it stores the ordinal's parent, so getParent silently returns 0 (the root) or, while the parent array grows, throws ArrayIndexOutOfBoundsException (an AssertionError with -ea).

## Expected vs actual

- Expected: You expect the parent of that category (ordinal 1, 'dim'), because TaxonomyWriter documents that the taxonomy contains ordinals 0 through getSize-1 and that getParent throws only for an ordinal beyond the last available one.
- Actual: addCategory makes the new ordinal visible through getSize a moment before it stores the ordinal's parent, so getParent silently returns 0 (the root) or, while the parent array grows, throws ArrayIndexOutOfBoundsException (an AssertionError with -ea).

## Files

- [src/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyWriterGetSizeGetParentRace.java](src/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyWriterGetSizeGetParentRace.java) (JUnit test)
- [src2/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyWriterGetSizeGetParentRace.java](src2/org/apache/lucene/facet/taxonomy/directory/TestTaxonomyWriterGetSizeGetParentRace.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestTaxonomyWriterGetSizeGetParentRace
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.taxonomy.directory.TestTaxonomyWriterGetSizeGetParentRace
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/taxonomy-writer-concurrent-add/](../../models/taxonomy-writer-concurrent-add/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
