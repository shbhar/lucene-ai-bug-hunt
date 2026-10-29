# DelimitedPayloadTokenFilterFactory creates one instance of a custom PayloadEncoder class and hands it to every filter it creates, so all threads using the analyzer share it, and nothing documents that a custom encoder must be thread-safe: an encoder that reuses its BytesRef silently stores other documents' payloads during ordinary concurrent indexing, and one whose payloads vary in length makes the next flush throw and the IndexWriter close itself

Bug LFV-598 · Area: analysis / analysis-common payloads (DelimitedPayloadTokenFilterFactory, PayloadEncoder, DelimitedPayloadTokenFilter) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-598.html](../../bugs/LFV-598.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure DelimitedPayloadTokenFilterFactory with your own PayloadEncoder class whose encode reuses one BytesRef, which is correct inside any single token stream because PayloadAttributeImpl deep-copies payloads, and you index from four threads; you expect every document to keep its own payload, as it does when each filter gets its own encoder through the public constructor. Instead the factory creates the encoder once in inform and passes that one instance to every filter in create, so the per-thread analyzer components share it and another thread's encode overwrites a payload before the indexer reads it: on main, 384 to 7,153 of 20,000 documents end up with another document's payload, with no exception. PayloadEncoder, the factory and the analysis-factory javadocs say nothing about the encoder being shared or needing to be thread-safe, while the sibling PhoneticFilterFactory creates its class-loaded encoder per filter for exactly this reason.

The test shows that two filters created by one `DelimitedPayloadTokenFilterFactory` (as an Analyzer creates one per thread) share the one custom `PayloadEncoder` instance made in `inform()`, so an encoder that reuses its BytesRef lets filter b overwrite filter a's payload.

## Expected vs actual

Expected: a's token `x|1.0` keeps payload 1.0 after b reads `y|2.0`. Actual: `payload of x|1.0 expected:<1.0> but was:<2.0>` (single thread, -ea and -da).

## Files

- [src/org/apache/lucene/analysis/payloads/TestDelimitedPayloadFactoryEncoderSharing.java](src/org/apache/lucene/analysis/payloads/TestDelimitedPayloadFactoryEncoderSharing.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.payloads.TestDelimitedPayloadFactoryEncoderSharing
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
