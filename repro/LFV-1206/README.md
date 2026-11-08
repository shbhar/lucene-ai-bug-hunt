# DateTools.stringToDate and stringToTime parse with a lenient SimpleDateFormat and ignore trailing text, so a string of a supported length that is not in the yyyyMMdd... format ("2024123X", the ISO date "2019-12-31", month 13) silently returns another date instead of the documented ParseException (since 1.9)

Finding LFV-1206 · Area: core / document (DateTools string-to-date decoding: stringToDate and stringToTime) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1206.html](../../bugs/LFV-1206.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call DateTools.stringToDate (or stringToTime) on a string that is not in DateTools' yyyyMMdd... format, for example "2024123X" or the ISO date "2019-12-31", and expect the ParseException that both methods document ('if dateString is not in the expected format') and already give for a wrong length or an unreadable start such as "aaaa". Instead, because the string has one of the seven supported lengths, it goes to a lenient SimpleDateFormat through DateFormat.parse(String), which ignores trailing text and rolls impossible fields over, so a different date comes back with no error: "2024123X" gives 2024-12-03T00:00:00Z, "2019-12-31" gives 2018-10-31T17:00:00Z and "20041301" (month 13) gives 2005-01-01T00:00:00Z.

## Expected vs actual

- Expected: `stringToDate("2024123X")` throws `ParseException`.
- Actual: `AssertionFailedError: Expected exception ParseException but no exception was thrown` (the call returns 2024-12-03T00:00:00Z), with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestDateToolsTrailingCharacter.java](src/org/apache/lucene/document/TestDateToolsTrailingCharacter.java) (JUnit test)

## Run it

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestDateToolsTrailingCharacter
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDateToolsTrailingCharacter.java](TestDateToolsTrailingCharacter.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
