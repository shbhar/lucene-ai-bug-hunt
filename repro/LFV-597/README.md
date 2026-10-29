# FacetsConfig writes the bare dimension term of a flat multiValued + requireDimCount SortedSetDocValues dimension without escaping it, so a dimension name containing U+001F or U+001E is read back as a different path: the dimension loses one label and reports that label's count as its own, a phantom dimension appears, a name with two U+001F makes facet-state construction throw for the whole field, and (-ea) a name ending in U+001E throws AssertionError

Bug LFV-597 · Area: facet / SortedSetDocValues faceting (FacetsConfig.processSSDVFacetFields path encoding with DELIM_CHAR U+001F / ESCAPE_CHAR U+001E, decoded by DefaultSortedSetDocValuesReaderState) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-597.html](../../bugs/LFV-597.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index three documents under a flat SortedSetDocValues facet dimension named 'color&lt;U+001F&gt;code', configured multiValued and requireDimCount, with labels {x, y}, {x} and {z}, and expect getTopChildren to return value=3 with x=2, y=1, z=1, as taxonomy faceting does for the same name. Instead it returns value=2 with only y=1 and z=1, and getAllDims also shows a phantom dimension 'color' with label 'code'. FacetsConfig escapes U+001F/U+001E in every facet term except this one bare dimension term (FacetsConfig.java:494), while the reader decodes every term as if it were escaped, so the raw term is read back as a different path.

The test shows that for a flat SortedSetDocValues facet dimension configured multiValued + requireDimCount whose name contains U+001F (`"c\u001Fd"`), FacetsConfig writes the bare dimension term unescaped, so the reader loses one label.

## Expected vs actual

One document with labels x and y; expected `getTopChildren` labels `[x (1), y (1)]`; actual `[y (1)]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/sortedset/TestSortedSetDocValuesDimCountReservedChar.java](src/org/apache/lucene/facet/sortedset/TestSortedSetDocValuesDimCountReservedChar.java) (JUnit test)

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
     execute --select-class org.apache.lucene.facet.sortedset.TestSortedSetDocValuesDimCountReservedChar
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
