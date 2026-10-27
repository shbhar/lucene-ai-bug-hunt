# NearestFuzzyQuery (used by KNearestFuzzyClassifier) computes idf from reader.numDocs (live documents) and reader.docFreq (which still counts deleted documents), so when deleted documents that are not yet merged away make up most of the index the idf is negative and rewrite throws 'boost must be a positive float' (released since 8.0.0)

Bug LFV-393 · Area: classification / NearestFuzzyQuery (KNearestFuzzyClassifier) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 6 documents containing 'apple' (class fruit), delete 5 of them under NoMergePolicy (or soft-delete them and keep them with SoftDeletesRetentionMergePolicy), and then search NearestFuzzyQuery('apple') or call KNearestFuzzyClassifier.assignClass('apple'), expecting the one live document and the class fruit. Instead both throw IllegalArgumentException 'boost must be a positive float, got -0.25276297'. NearestFuzzyQuery computes idf = ln((numDocs+1)/(docFreq+1)) + 1 from reader.numDocs, which counts only live documents, and reader.docFreq, which still counts the 5 deleted copies, so idf = ln(2/7) + 1 &lt; 0, and since LUCENE-7996 (8.0.0) BoostQuery rejects the resulting negative boost.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Both throw IllegalArgumentException 'boost must be a positive float, got -0.25276297'.

## Files

- [src/org/apache/lucene/classification/TestNearestFuzzyQueryDeletedDocs.java](src/org/apache/lucene/classification/TestNearestFuzzyQueryDeletedDocs.java) (JUnit test)
- [src2/org/apache/lucene/classification/TestNearestFuzzyQueryDeletedDocs.java](src2/org/apache/lucene/classification/TestNearestFuzzyQueryDeletedDocs.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/classification/build/libs/lucene-classification-$V.jar
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.classification.TestNearestFuzzyQueryDeletedDocs
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.classification.TestNearestFuzzyQueryDeletedDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
