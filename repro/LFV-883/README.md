# GroupReducer lets one group's CollectionTerminatedException escape SecondPassGroupingCollector, so IndexSearcher ends the whole segment and every other group silently misses its remaining documents in that segment

Bug LFV-883 · Area: grouping / GroupReducer and SecondPassGroupingCollector (second-pass per-group collectors) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a GroupReducer whose per-group collector needs only the first documents of its group in a segment (for example a stock TopFieldCollector in index order with a hit threshold of 1, which then throws CollectionTerminatedException, as collectors may), run it through SecondPassGroupingCollector, and expect every group's collector still to see the documents of its own group, as GroupReducer's javadoc says and as MultiCollector does for its children. Instead GroupReducer.collect does not catch the exception, SecondPassGroupingCollector passes it on, and IndexSearcher ends the segment for the whole second pass, so all the other groups silently miss their remaining documents in that segment. In the repro one segment holds docs 0-2 in group a and docs 3-4 in group b: group a's collector stops on doc 1, group b reports no hit instead of doc 3, and the second pass counts 2 of 5 documents.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: GroupReducer.collect does not catch the exception, SecondPassGroupingCollector passes it on, and IndexSearcher ends the segment for the whole second pass, so all the other groups silently miss their remaining documents in that segment.

## Files

- [src/org/apache/lucene/search/grouping/TestGroupReducerCollectionTerminated.java](src/org/apache/lucene/search/grouping/TestGroupReducerCollectionTerminated.java) (JUnit test)
- [src2/org/apache/lucene/search/grouping/TestGroupReducerCollectionTerminated.java](src2/org/apache/lucene/search/grouping/TestGroupReducerCollectionTerminated.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestGroupReducerCollectionTerminated
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestGroupReducerCollectionTerminated
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
