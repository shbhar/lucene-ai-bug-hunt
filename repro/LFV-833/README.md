# SpanPayloadCheckQuery.toString and IndexSearcher.explain throw NullPointerException when payloadToMatch contains a null entry, although the matcher accepts null as 'no payload at this position' and the query searches and counts correctly (since 5.4.0, LUCENE-6716)

Bug LFV-833 · Area: queries / payloads (SpanPayloadCheckQuery.toString, reached from SpanWeight.explain) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a SpanPayloadCheckQuery over the span 'a b' with payloadToMatch = [null, 'p'], meaning that 'a' must have no payload and 'b' the payload 'p'; the matcher supports exactly this (a null entry matches a position without a payload, a non-null entry never does), and the search finds the document. You expect toString to print the query and IndexSearcher.explain to explain the hit. Both throw NullPointerException ('Cannot read field "bytes" because "termText" is null'), because toString hands every entry, null included, to Term.toString(BytesRef), and explain builds its description from the query string.

The test shows that SpanPayloadCheckQuery.toString() throws NullPointerException when payloadToMatch holds a null entry, which the matcher accepts as 'no payload at this position'.

## Expected vs actual

Expected: a string that describes the query. Actual (-ea and -da): NullPointerException from Term.toString(null) at SpanPayloadCheckQuery.toString.

## Files

- [src/org/apache/lucene/queries/payloads/TestSpanPayloadCheckQueryNullEntryToString.java](src/org/apache/lucene/queries/payloads/TestSpanPayloadCheckQueryNullEntryToString.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.payloads.TestSpanPayloadCheckQueryNullEntryToString
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
