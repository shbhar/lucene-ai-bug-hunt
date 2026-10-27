# QueryParser.setTimeZone (and StandardQueryParser.setTimeZone) is used only to round the inclusive end of a date range to 23:59:59.999; both dates are still parsed in the JVM default time zone, so with a non-default zone d:[6/1/2005 TO 6/4/2005] covers a shifted window and exclusive ranges ignore the setting

Bug LFV-283 · Area: queryparser / classic and flexible date ranges (QueryParserBase.getRangeQuery, TermRangeQueryNodeProcessor; setTimeZone and ConfigurationKeys.TIMEZONE) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give the query parser a time zone with setTimeZone (plus setLocale and setDateResolution) and parse d:[6/1/2005 TO 6/4/2005], expecting what the QueryParser javadoc promises, a range 'between 2005-06-01 and 2005-06-04', read in that zone: June 1 00:00 to June 4 23:59:59.999 there. Instead both dates are parsed by a DateFormat that still uses the JVM default zone, and only the rounding of the inclusive end to 23:59:59.999 uses the configured zone, so the window starts at midnight in the JVM zone and ends at the end of the configured-zone day that contains the JVM-zone midnight of June 4: with the JVM on UTC and the parser on New York the June 4 New York documents are silently lost (and May 31 from 20:00 matches), with the JVM on UTC and the parser on Tokyo the first nine hours of June 1 are lost. Exclusive ranges such as {6/1/2005 TO 6/4/2005} ignore the configured zone entirely, and the classic and the flexible parser behave the same.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Both dates are parsed by a DateFormat that still uses the JVM default zone, and only the rounding of the inclusive end to 23:59:59.999 uses the configured zone, so the window starts at midnight in the JVM zone and ends at the end of the configured-zone day that contains the JVM-zone midnight of June 4: with the JVM on UTC and the parser on New York the June 4 New York documents are silently lost (and May 31 from 20:00 matches), with the JVM on UTC and the parser on Tokyo the first nine hours of June 1 are lost.

## Files

- [src/org/apache/lucene/queryparser/classic/TestDateRangeTimeZone.java](src/org/apache/lucene/queryparser/classic/TestDateRangeTimeZone.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.classic.TestDateRangeTimeZone
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
