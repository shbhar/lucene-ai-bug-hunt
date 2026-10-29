# DateTools strings stop sorting by date and stop round-tripping outside years AD 1..9999: the 'yyyy' year-of-era pattern drops the BC era (2 BC sorts after AD 1 and parses back as AD 2; 1 BC and AD 1 give the same string) and gives years after 9999 a fifth digit (10000 sorts before 2026, and stringToTime throws ParseException on DateTools' own output)

Bug LFV-645 · Area: core / document (DateTools date-to-string term encoding: timeToString/dateToString and stringToTime/stringToDate) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-645.html](../../bugs/LFV-645.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You convert a date before AD 1 or after 9999 with DateTools.timeToString (or dateToString) and expect what the class javadoc promises: strings whose lexicographic order is date order, which stringToTime turns back into the same time. Instead the SimpleDateFormat pattern 'yyyy...' prints the year of era with no era and at least four digits: 2 BC-01-01 becomes "00020101", which sorts after AD 1's "00010101" and parses back as AD 2-01-01 (at YEAR resolution 1 BC and AD 1 both give "0001"), and 10000-01-01 becomes "100000101", which sorts before "20260101" and makes stringToTime throw ParseException, because the parser is chosen by string length. Range queries, prefix queries and sorts on such terms therefore return wrong results with no error.

The test shows that `DateTools.timeToString(2 BC-01-01 GMT, DAY)` gives `"00020101"` (no era), and `stringToTime` turns DateTools' own string back into AD 2-01-01.

## Expected vs actual

Expected `-62198928000000` (2 BC-01-01); actual `-62104233600000` (AD 2-01-01): `2 BC-01-01 GMT -> 00020101 expected:<-62198928000000> but was:<-62104233600000>` (-ea and -da).

## Files

- [src/org/apache/lucene/document/TestDateToolsBCRoundTrip.java](src/org/apache/lucene/document/TestDateToolsBCRoundTrip.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestDateToolsBCRoundTrip
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
