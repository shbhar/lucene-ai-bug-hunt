# ParentsChildrenBlockJoinQuery's Weight#matches ignores the join and returns Matches for documents the query does not match (parent documents, children of non-matching parents, children over childLimitPerParent), so as a MUST_NOT clause it strips the Matches and highlights of real hits, and Lucene Monitor's HighlightsMatch matcher reports the wrong documents

Bug LFV-508 · Area: join / block join Weight#matches (ParentsChildrenBlockJoinQuery; consumers BooleanWeight#matches, UnifiedHighlighter, Monitor HighlightsMatch) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-508.html](../../bugs/LFV-508.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index parent/child blocks, build a ParentsChildrenBlockJoinQuery (children that match childQuery and whose parent matches parentQuery, at most childLimitPerParent per parent) and ask its Weight for Matches, directly, inside a BooleanQuery, through the default UnifiedHighlighter or through Lucene Monitor, and you expect non-null Matches exactly for the join's hits, as Weight#matches documents ('or null if the document does not match the parent query'). Instead matches returns the union of the parent query's and the child query's Matches on the same document without checking the join, so it is non-null for the parent document itself, for children of parents that do not match and for children over the limit (in the finder's test the only hit is doc 0, but docs 0, 1, 2 and 3 get Matches). Searches stay correct, but a query such as +text:size -join loses the Matches and highlights of its real hits, and Monitor's HighlightsMatch matcher reports documents the stored query does not match.

The test shows that `ParentsChildrenBlockJoinQuery`'s Weight returns Matches for the parent document of a one-child block, which the join never returns.

## Expected vs actual

Expected `matches(leaf, 1)` == null for parent doc 1, because the only hit is child doc 0. Actual: non-null (parentQuery text:red's Matches), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/join/TestParentsChildrenMatchesParentDoc.java](src/org/apache/lucene/search/join/TestParentsChildrenMatchesParentDoc.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestParentsChildrenMatchesParentDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
