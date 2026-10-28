# AnalyzingSuggester and FuzzySuggester take the new input's payload flag before build can fail, so after a failed rebuild (or during a rebuild) lookups decode the previous FST in the wrong payload format: the payload glued to the suggestion text, or the suggestion returned as the payload

Bug LFV-784 · Area: suggest / analyzing suggester (AnalyzingSuggester.build failure atomicity: hasPayloads is published before the new FST; inherited by FuzzySuggester) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-784.html](../../bugs/LFV-784.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingSuggester (or FuzzySuggester) from input with payloads, for example 'foo' with weight 10 and payload 'p1', then rebuild the same instance from a dictionary without payloads whose input throws an IOException partway through, and you expect lookups to keep returning the previous build ('foo', payload 'p1') or nothing. Instead lookup('fo') returns the text 'foo&lt;U+001F&gt;p1' with payload null, because build stores the new input's payload flag at its start (AnalyzingSuggester.java:394) but replaces the FST only at its end (:589-590), so the old FST is read with the new flag; in the opposite direction (old build without payloads, failed rebuild with payloads) lookups return empty text with 'foo' as the payload, or trip the suggester's own assertion with -ea. A lookup made while such a rebuild is still reading its input sees the same mixture, and store writes it to disk, so load brings it back.

## Expected vs actual

- Expected: `[foo (weight 10, payload p1)]` after the failed rebuild and after store+load; `[foo (weight 10, payload null)]` in the reverse case.
- Actual: `[foo<U+001F>p1 (weight 10, payload null)]`; reverse case `AssertionError` (assertions on) or `[ (weight 10, payload foo)]` (off): 4 of 5 tests fail, the control passes.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestFailedBuildPayloadFlag.java](src/org/apache/lucene/search/suggest/analyzing/TestFailedBuildPayloadFlag.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestFailedBuildPayloadFlag
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestAnalyzingSuggesterFailedRebuild.java](TestAnalyzingSuggesterFailedRebuild.java)
- [fix.diff](fix.diff)
- [TestFailedBuildPayloadFlag.java](TestFailedBuildPayloadFlag.java)
- [TestRebuildPayloadFlagV1.java](TestRebuildPayloadFlagV1.java)
- [PayloadFlagHistoryProbe.java](PayloadFlagHistoryProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
