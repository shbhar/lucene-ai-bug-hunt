# Grouping LongRangeFactory and DoubleRangeFactory give the last range below max the full width, so its reported group bounds run past max and overlap the range for values &gt;= max

Bug LFV-407 · Area: grouping / range group selectors (LongRangeFactory and DoubleRangeFactory, used by LongRangeGroupSelector and DoubleRangeGroupSelector) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You group documents with new LongRangeFactory(10, 10, 25), meaning buckets 10 wide starting at 10 and everything at or above 25 in one bucket, and expect what the javadoc says: [10, 20), a shorter final bucket [20, 25), and [25, MAX). Instead the value 22 is labelled LongRange(20, 30) while 27 is labelled LongRange(25, MAX), so the two reported ranges overlap; each group holds the right documents, but a range query built from the label [20, 30), the way Lucene's own selector tests rebuild a group, also returns the document with value 27 from the other group. DoubleRangeFactory behaves the same, because both getRange methods set the bucket's upper bound to its lower bound + width and never cap it at max.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The value 22 is labelled LongRange(20, 30) while 27 is labelled LongRange(25, MAX), so the two reported ranges overlap; each group holds the right documents, but a range query built from the label [20, 30), the way Lucene's own selector tests rebuild a group, also returns the document with value 27 from the other group.

## Files

- [src/org/apache/lucene/search/grouping/TestRangeFactoryFinalRange.java](src/org/apache/lucene/search/grouping/TestRangeFactoryFinalRange.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestRangeFactoryFinalRange
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
