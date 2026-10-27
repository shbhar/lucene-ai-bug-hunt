# ToChildBlockJoinQuery and JoinUtil's scoring global-ordinals join inherit FilterWeight#matches, so Weight#matches reports the wrapped query's documents instead of the join's hits (child hits lose their highlights, Lucene Monitor's HighlightsMatch matcher reports the wrong documents)

Bug LFV-236 · Area: join / block-join and global-ordinals join Weight#matches (ToChildBlockJoinQuery, GlobalOrdinalsWithScoreQuery) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index parent/child blocks, search +ToChildBlockJoinQuery(color:red) +body:shirt and highlight the hit with the default UnifiedHighlighter, and expect 'small cotton &lt;b&gt;shirt&lt;/b&gt;', because Weight#matches must return Matches for every hit of its query and null for every other document. Instead the join's weight inherits FilterWeight#matches, which answers for the wrapped parent query: it returns Matches for the red parent and null for the child hits, so the conjunction's Matches is null and the hit comes back as 'small cotton shirt'; JoinUtil's scoring global-ordinals join has the same defect and reports Matches on every to-side document, not only the joined ones. The search hits themselves are correct; only Weight#matches and the features built on it (Matches-based highlighting, Lucene Monitor's HighlightsMatch matcher) are wrong.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The join's weight inherits FilterWeight#matches, which answers for the wrapped parent query: it returns Matches for the red parent and null for the child hits, so the conjunction's Matches is null and the hit comes back as 'small cotton shirt'; JoinUtil's scoring global-ordinals join has the same defect and reports Matches on every to-side document, not only the joined ones.

## Files

- [src/org/apache/lucene/search/join/TestJoinWeightMatchesContract.java](src/org/apache/lucene/search/join/TestJoinWeightMatchesContract.java) (JUnit test)
- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterWithToChildJoin.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterWithToChildJoin.java) (JUnit test)
- [src2/org/apache/lucene/search/join/TestJoinWeightMatchesContract.java](src2/org/apache/lucene/search/join/TestJoinWeightMatchesContract.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestJoinWeightMatchesContract
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestJoinWeightMatchesContract

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterWithToChildJoin
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
