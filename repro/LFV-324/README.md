# SortedSetDocValues faceting builds its facet tree from the byte order of the encoded paths, so a label or dimension name that equals a sibling's name followed by a character below U+001F (TAB, CR, LF, or the U+001E that FacetsConfig writes when it escapes U+001E/U+001F) moves a label's children under that sibling, or drops a whole dimension or one of its labels

Bug LFV-324 · Area: facet / SortedSetDocValues faceting (DefaultSortedSetDocValuesReaderState: the per-dimension DimTree and OrdRange built from the field's ordinals in term order, shared by SortedSetDocValuesFacetCounts and ConcurrentSortedSetDocValuesFacetCounts) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-324.html](../../bugs/LFV-324.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index hierarchical SortedSetDocValues facets, for example cat/shoes/red in one document and cat/'shoes&lt;TAB&gt;' (an untrimmed input) in another, and expect getTopChildren(10, 'cat', 'shoes') to return red=1, as taxonomy faceting does for the same labels. Instead it returns null and the leaf 'shoes&lt;TAB&gt;' reports red=1: the encoded paths sort by byte, a character below the path delimiter U+001F places 'shoes&lt;TAB&gt;' between 'shoes' and 'shoes U+001F red', and the reader state infers parent, child and sibling links only from the order of consecutive ordinals. The same happens for a label that contains U+001F or U+001E (FacetsConfig escapes both with U+001E), and dimension names such as 'd' next to 'd&lt;TAB&gt;' make dimension d disappear or, for a flat multiValued + requireDimCount dimension, lose one label, all with no error.

The test shows that SortedSetDocValues hierarchical faceting moves the children of a label to a sibling label that is the same text plus a TAB (cat/shoes/red next to cat/"shoes\t").

## Expected vs actual

Expected `getTopChildren(10, "cat", "shoes")` to return value=1 with child red (1); actual: `null` (red is reported under "shoes\t"), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/sortedset/TestSsdvFacetChildrenMovedToTabSibling.java](src/org/apache/lucene/facet/sortedset/TestSsdvFacetChildrenMovedToTabSibling.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.sortedset.TestSsdvFacetChildrenMovedToTabSibling
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
