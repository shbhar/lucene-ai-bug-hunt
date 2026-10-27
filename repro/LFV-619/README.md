# DateRangePrefixTree.parseCalendar (and parseShape) rejects day 32 but accepts a day that its month does not have (2014-11-31, 2014-02-30, 2013-02-29) and silently rolls it into the next month, so the range [2014-11-01 TO 2014-11-31] also matches December 1st

Bug LFV-619 · Area: spatial-extras / prefix trees (DateRangePrefixTree date parsing: parseCalendar, also reached through NumberRangePrefixTree.parseShape for single dates and range bounds) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse a date such as 2014-11-31, or a range such as [2014-11-01 TO 2014-11-31], with DateRangePrefixTree.parseCalendar or parseShape and expect a ParseException, which the parser has given since LUCENE-8640 for day 32, month 13, hour 25 and minute 60. Instead the day is checked only against 1..31 and set on a lenient java.util.Calendar, which moves it into the next month when the fields are read: 2014-11-31 becomes 2014-12-01, 2014-02-30 becomes 2014-03-02, and the range parses to [2014-11 TO 2014-12-01], so an Intersects query on a NumberRangePrefixTreeStrategy index also returns a document dated 2014-12-01.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The day is checked only against 1..31 and set on a lenient java.util.Calendar, which moves it into the next month when the fields are read: 2014-11-31 becomes 2014-12-01, 2014-02-30 becomes 2014-03-02, and the range parses to [2014-11 TO 2014-12-01], so an Intersects query on a NumberRangePrefixTreeStrategy index also returns a document dated 2014-12-01.

## Files

- [src/org/apache/lucene/spatial/prefix/tree/TestDateRangeParseNonexistentDay.java](src/org/apache/lucene/spatial/prefix/tree/TestDateRangeParseNonexistentDay.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.prefix.tree.TestDateRangeParseNonexistentDay
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
