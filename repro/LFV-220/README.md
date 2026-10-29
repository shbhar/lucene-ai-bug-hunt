# ToParentBlockJoinSortField treats a parent as having a missing child value whenever its block holds any document outside childFilter, even if every document has the sort field (regression in 10.5.0)

Bug LFV-220 · Area: join / ToParentBlockJoinSortField nested sort (ToParentDocValues missing-child count, #15727) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-220.html](../../bugs/LFV-220.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index blocks with two kinds of non-parent documents, for example P1 = [type:a s=y n=40, type:b s=x n=7, parent P1] and P2 = [type:a s=d n=10, parent P2], and sort the parents of a ToParentBlockJoinQuery with new ToParentBlockJoinSortField("s", STRING, false, parents, childFilter type:a), expecting 'P2=d P1=y', which is what Lucene 10.4.0 returns. Instead 10.5.0 and later return 'P1=null P2=d', sorting P1 as if it had a child without the field although every document in both blocks has it, and a LONG sort with childMissingValue=0 gives 'P1=0 P2=10' instead of 'P2=10 P1=40'. The cause is that ToParentDocValues decides whether some child lacks a value by comparing the number of childFilter documents that have a value with the number of all documents in the block, so any document outside childFilter, with or without the field, forces the missing value.

The test shows that `ToParentBlockJoinSortField` (STRING, ascending, childFilter `type:a`) sorts parent P1 as if it had a child without a value only because its block also holds a `type:b` sibling outside childFilter, although every document has the sort field.

## Expected vs actual

Expected `P2=d P1=y` (MIN over the type:a children, as on 10.4.0); actual: `P1=null P2=d`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/join/TestBlockJoinSortFilteredOutSibling.java](src/org/apache/lucene/search/join/TestBlockJoinSortFilteredOutSibling.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.join.TestBlockJoinSortFilteredOutSibling
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
