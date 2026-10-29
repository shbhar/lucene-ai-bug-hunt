# DatasetSplitter.split can silently leave a whole labelled class out of all three output indexes when some documents have no class value, and throws NullPointerException, leaving the output writers locked, when a segment has no class field

Bug LFV-683 · Area: classification / utils: DatasetSplitter (split: class count used as the GroupingSearch group limit) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-683.html](../../bugs/LFV-683.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You split an index with DatasetSplitter.split(reader, train, test, cv, analyzer, false, "cls"), where one segment holds d0 without a class value, d1 of class a and d2 of class b. You expect every live document in one of the three new indexes, and every class present, which is what the grouping by class was added for (LUCENE-7196). Instead d2, and with it all of class b, is in none of the outputs: split asks GroupingSearch for as many groups as there are class values, but the documents without a value form one more (null) group that takes one of those slots; and if a segment has no class field at all, split throws NullPointerException from terms(classFieldName).size before its try block, so its three IndexWriters are never closed.

The test shows that `DatasetSplitter(0.1, 0.1).split(..., "cls")` on one segment `d0` (no class value), `d1` (class a), `d2` (class b) asks `GroupingSearch` for 2 groups (the class count), but the unlabelled document forms a third (null) group that takes a slot, so class b is cut.

## Expected vs actual

Expected `d2` (the only document of class b) in one of the three outputs; actual: the outputs hold `[d0, d1]`.

## Files

- [src/org/apache/lucene/classification/utils/TestDatasetSplitterUnlabelledDocs.java](src/org/apache/lucene/classification/utils/TestDatasetSplitterUnlabelledDocs.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/classification/build/libs/lucene-classification-$V.jar
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.classification.utils.TestDatasetSplitterUnlabelledDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
