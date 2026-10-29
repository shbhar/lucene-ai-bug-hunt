# Geo3D distance sorts read a document's doc values twice in any segment where some document has two or more points, so top-N and searchAfter results come back in the wrong order with another document's distance as the sort value (even a filtered-out or deleted document's), or the search throws EOFException (every release since 7.0.0)

Bug LFV-551 · Area: spatial3d / Geo3DDocValuesField sort comparators (Geo3DPointDistanceComparator, Geo3DPointOutsideDistanceComparator; all seven Geo3D sort factories) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-551.html](../../bugs/LFV-551.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents with Geo3DDocValuesField, at least one of them with two or more points (the javadoc allows this and says the closest one is used), and ask for the top 2 hits sorted by Geo3DDocValuesField.newDistanceSort; you expect the head of the full sorted order, each hit carrying its own closest distance. Instead the top 2 come back in the wrong order and a hit reports the distance of the next document, because the comparator reads the document's values once in compareBottom (or compareTop) and again in copy without re-positioning the SortedNumericDocValues iterator, which its javadoc forbids ('Do not call this more than docValueCount times'), so the second read takes the following document's values or runs past the end of the data and throws EOFException. The same happens with searchAfter paging and all seven Geo3D sort factories, and because document order decides which value is read, a hit can report the distance of a document that a filter, a live-docs wrapper or a delete hides from the search.

The test shows that a top-2 `Geo3DDocValuesField.newDistanceSort` search over a segment with a two-point document returns the wrong order and another document's distance, because the comparator reads the document's doc values twice.

## Expected vs actual

Expected the head of the full sort, doc 2 at 55,599 m then doc 0 at 111,194 m; actual: doc 0, then doc 2 at 555,976 m (doc 3's distance), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DDistanceSortMultiValued.java](src/org/apache/lucene/spatial3d/TestGeo3DDistanceSortMultiValued.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DDistanceSortMultiValued
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
