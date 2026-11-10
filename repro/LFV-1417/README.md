# Test framework: BaseTokenStreamTestCase.assertTokenStreamContents never checks a null entry of its expected payloads array (it asserts that the expected entry is null, which is always true there, instead of reading the stream's payload), so a token stream that emits a payload where none is expected passes the check

Finding LFV-1417 · Area: test-framework / analysis: BaseTokenStreamTestCase (payload check of assertTokenStreamContents and assertAnalyzesTo) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1417.html](../../bugs/LFV-1417.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

In a unit test you check a token stream with BaseTokenStreamTestCase.assertTokenStreamContents and pass an expected payloads array whose entry i is null, meaning token i should carry no payload. You expect the check to fail when the stream puts a payload on that token, just as it fails for a wrong non-null payload. Instead the null branch asserts that the expected entry payloads[i] is null, which the enclosing if has already established, and never reads the stream's payload, so the test passes.

## Expected vs actual

- Expected: `assertTokenStreamContents` throws `AssertionError` because token 0 carries payload `[42]` and the entry expects none, so the test passes.
- Actual: The check passes; the test fails with `Expected exception AssertionError but no exception was thrown`, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/TestNullPayloadEntryIsChecked.java](src/org/apache/lucene/analysis/TestNullPayloadEntryIsChecked.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestNullPayloadEntryIsChecked
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestNullPayloadEntryIsChecked.java](TestNullPayloadEntryIsChecked.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
