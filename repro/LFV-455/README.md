# Intervals.term(term, payloadFilter) throws IllegalArgumentException ('no indexed payloads') for every segment in which no document indexed a payload for the field, so the whole search fails and whether it fails depends on segment layout

Bug LFV-455 · Area: queries / intervals (PayloadFilteredTermIntervalsSource per-segment hasPayloads check) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Index a text field in which only some tokens carry payloads (for example with DelimitedPayloadTokenFilter: 'big|adj dog|noun' next to 'dog walks') and search it with Intervals.term("dog", payloadFilter). You expect the documents whose 'dog' payload passes the filter, however the documents happen to be split into segments. Instead, as soon as one segment contains only documents without payloads for that field, the whole search throws IllegalArgumentException 'Cannot create a payload-filtered iterator over field f because it has no indexed payloads', while the same documents after forceMerge(1) return the right hits.

## Expected vs actual

- Expected: You expect the documents whose 'dog' payload passes the filter, however the documents happen to be split into segments.
- Actual: As soon as one segment contains only documents without payloads for that field, the whole search throws IllegalArgumentException 'Cannot create a payload-filtered iterator over field f because it has no indexed payloads', while the same documents after forceMerge(1) return the right hits.

## Files

- [src/org/apache/lucene/queries/intervals/TestPayloadFilteredIntervalsSegmentWithoutPayloads.java](src/org/apache/lucene/queries/intervals/TestPayloadFilteredIntervalsSegmentWithoutPayloads.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestPayloadFilteredIntervalsSegmentWithoutPayloads
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
