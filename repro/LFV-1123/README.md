# DateTools.round returns a time near Long.MAX_VALUE (AD 292278994) for Long.MIN_VALUE, and for times before the first resolution-unit boundary above it, instead of rounding down: the rounded-down time lies below the long range and the lenient Calendar arithmetic silently wraps

Finding LFV-1123 · Area: core / document (DateTools resolution limiting: round(long, Resolution) and round(Date, Resolution)) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1123.html](../../bugs/LFV-1123.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call DateTools.round(Long.MIN_VALUE, Resolution.DAY), for example to align the open lower bound of a date range with day-rounded indexed values, and expect a time no later than the input, as the javadoc promises ('the date with all values more precise than resolution set to 0 or 1'). Instead you get 9223372036794351616, a time in AD 292278994 just below Long.MAX_VALUE, with no error: the start of that day lies below Long.MIN_VALUE, and the lenient GregorianCalendar that round() uses wraps around in its long arithmetic. YEAR, MONTH, HOUR, MINUTE and SECOND wrap the same way for inputs before the first unit boundary above Long.MIN_VALUE, and round(Date, Resolution) delegates, so it wraps too.

## Expected vs actual

- Expected: `round(Long.MIN_VALUE, DAY)` is no later than the input (`<= Long.MIN_VALUE`).
- Actual: `AssertionError: round(Long.MIN_VALUE, DAY) = 9223372036794351616`, with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestDateToolsRoundNearLongMin.java](src/org/apache/lucene/document/TestDateToolsRoundNearLongMin.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestDateToolsRoundNearLongMin
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDateToolsRoundNearLongMin.java](TestDateToolsRoundNearLongMin.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
