# SortedSetDocValues faceting throws NullPointerException from getTopChildren/getAllChildren for a dimension that is configured hierarchical but has no document in the reader, instead of returning null as the Facets javadoc says (regression since 9.3.0)

Bug LFV-527 · Area: facet / SortedSetDocValues faceting (AbstractSortedSetDocValueFacetCounts#prepareChildIteration, shared by SortedSetDocValuesFacetCounts and ConcurrentSortedSetDocValuesFacetCounts) · Priority: medium · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You declare a facet dimension as hierarchical (FacetsConfig.setHierarchical("category", true)), index SortedSetDocValues facets in which no document has "category" yet, and call getTopChildren(10, "category") or getAllChildren("category") on SortedSetDocValuesFacetCounts or ConcurrentSortedSetDocValuesFacetCounts, expecting null, because the Facets javadoc says both methods return null 'if this dimension was never seen' and the same call for a flat dimension does. Instead both throw NullPointerException: Cannot read field "dimStartOrd" because "dimTree" is null (getTopChildren when the query has hits, always with the count-all constructor and the concurrent class; getAllChildren always). The cause is a shortcut added in 9.3.0 to AbstractSortedSetDocValueFacetCounts#prepareChildIteration, which reads dimTree.dimStartOrd for an empty path without checking that state.getDimTree(dim) is non-null, while the flat branch and the path branch next to it return null for the same case.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Both throw NullPointerException: Cannot read field "dimStartOrd" because "dimTree" is null (getTopChildren when the query has hits, always with the count-all constructor and the concurrent class; getAllChildren always).

## Files

- [src/org/apache/lucene/facet/sortedset/TestSsdvHierarchicalDimNotIndexed.java](src/org/apache/lucene/facet/sortedset/TestSsdvHierarchicalDimNotIndexed.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.sortedset.TestSsdvHierarchicalDimNotIndexed
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
