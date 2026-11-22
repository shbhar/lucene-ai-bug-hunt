# GroupReducer lets one group's CollectionTerminatedException escape SecondPassGroupingCollector, so IndexSearcher ends the whole segment and every other group silently misses its remaining documents in that segment

Finding LFV-883 · Area: grouping / GroupReducer and SecondPassGroupingCollector (second-pass per-group collectors) · Priority: low · Impact: silent wrong results · Novelty: known upstream

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

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main a9779ef3c193 (a commit of 2026-10-26): reproduced.
- This folder holds two copies of the test. The copy under src2/ is the form that compiles with the jars of 9.1.0 and 9.12.3: it calls TopFieldCollector.create(Sort, int, int) (line 95), which the 10.6.0 release, the 11.0 branch and main do not have, and new MatchAllDocsQuery() (line 48), whose constructor #16962 (merged 2026-11-03) made private on main (24d9e7d1a0d) and on the 11.0 branch (branch_11x, a6a26f9b251). So on a build of main or of the 11.0 branch and with the jars of the 10.6.0 release the second javac line stops (at both lines on the two builds, at line 95 with the 10.6.0 jars) and the second java command finds no test: leave these two commands out there. The first javac line and the first java command are the repro: run on 2026-11-22 with assertions on and off, the src/ copy fails 1 of 1 with 'first document of group b expected:&lt;1&gt; but was:&lt;0&gt;' on a build of main b5a4546a5964, on a build of the 11.0 branch (branch_11x 262e019500ec; there V=11.0.0-SNAPSHOT) and with the 10.6.0 jars (Maven Central, org.apache.lucene, in place of the five $LUCENE jars; V=10.6.0). The src2/ copy fails the same way with the jars of 9.12.3 and 9.1.0 (run the same day with assertions on), where the src/ copy does not compile.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
