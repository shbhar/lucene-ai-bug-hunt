# ParentsChildrenBlockJoinQuery: advance does not count the matching children it skips, so conjunctions, sorted searches and explain return children beyond childLimitPerParent

Bug LFV-1048 · Area: join / block join iterator (ParentsChildrenBlockJoinQuery advance and Weight#explain; reached through ConjunctionDISI, sorted-search competitive iterators and IndexSearcher.explain) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the blocks [a, b, p1] and [c, p2] and build a ParentsChildrenBlockJoinQuery with childLimitPerParent 1, which on its own matches a and c, the first matching child of each parent. You expect the BooleanQuery +join #id:b to match nothing and explain(join, b) to say it is not a match. Instead the conjunction returns b and explain reports b as a match: advance(target) starts counting the limit at the target instead of at the block's first child, so it returns a child that nextDoc never returns, which breaks the DocIdSetIterator#advance contract.

## Expected vs actual

- Expected: You expect the BooleanQuery +join #id:b to match nothing and explain(join, b) to say it is not a match.
- Actual: The conjunction returns b and explain reports b as a match: advance(target) starts counting the limit at the target instead of at the block's first child, so it returns a child that nextDoc never returns, which breaks the DocIdSetIterator#advance contract.

## Files

- [src/org/apache/lucene/search/join/TestParentsChildrenChildLimitAdvance.java](src/org/apache/lucene/search/join/TestParentsChildrenChildLimitAdvance.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestParentsChildrenChildLimitAdvance
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
