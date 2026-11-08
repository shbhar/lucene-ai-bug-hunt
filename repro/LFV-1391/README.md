# A document block that IndexWriter rejects at a child (a documented, non-aborting IllegalArgumentException such as an immense term) leaves the block's already numbered documents in the in-memory segment as deleted documents with no parent after them, and the next accepted block's parent adopts them: ToParentBlockJoinQuery count() under every ScoreMode and ScoreMode.None searches return that parent although none of its live children matches (every release since 5.3.0)

Finding LFV-1391 · Area: join / block join (ToParentBlockJoinQuery's Scorer path: count() and ScoreMode.None) over index / IndexWriter rollback of a rejected document block (DocumentsWriterPerThread) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1391.html](../../bugs/LFV-1391.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a block [red child, a child with a term longer than MAX_TERM_LENGTH, parent], which IndexWriter rejects with its documented IllegalArgumentException, then add a second block [blue child, parent], commit, and count ToParentBlockJoinQuery(color:red, parents, ScoreMode.None). You expect 0, because addDocuments promises that a reader sees all or none of a block and the only red child was in the rejected block (you do get 0 when that block is never added). Instead count() returns 1: the rejected block's first two documents kept doc IDs 0 and 1 as deleted documents with no parent after them, and the join maps the deleted red child to the next parent, doc 3, which belongs to the accepted block.

## Expected vs actual

- Expected: count = 0: the only red child was in the rejected block
- Actual: count = 1, the accepted block's parent: `expected:<0> but was:<1>`

## Files

- [src/org/apache/lucene/search/join/TestRejectedBlockJoin.java](src/org/apache/lucene/search/join/TestRejectedBlockJoin.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestRejectedBlockJoin
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRejectedBlockJoin.java](TestRejectedBlockJoin.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
