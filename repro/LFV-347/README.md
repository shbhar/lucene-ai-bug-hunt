# Monitor with RegexpQueryHandler silently misses regexp matches on any short token that contains a supplementary character (an emoji, a CJK Extension B ideograph, a Gothic or Adlam letter), because SuffixingNGramTokenFilter emits no ngrams for that token

Bug LFV-347 · Area: monitor / presearcher (RegexpQueryHandler document-side ngram filter SuffixingNGramTokenFilter) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-347.html](../../bugs/LFV-347.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a Monitor whose presearcher uses RegexpQueryHandler, register the query /hel.*/ and match a document whose text is 'hello😀' (WhitespaceAnalyzer, one token). You expect a match, because the regexp matches that token and the presearcher may only skip queries that cannot match. Instead SuffixingNGramTokenFilter, which should emit every substring of the token with the suffix 'XX' so that the query's index term 'helXX' is found, starts its gram size at the token's UTF-16 length (7) but checks it against the token's code-point count (6), gives up on the token without emitting a single ngram, and the query is silently never run.

## Expected vs actual

- Expected: `Monitor.match` reports `/hel.*/` for `hello😀` as it does for `hello` and as a Monitor without filtering does; `a😀` gives `{a😀, a😀XX, aXX, 😀XX, XX}`.
- Actual: `/hel.*/` is not reported for `hello😀` (0 queries run, no error), `/𞤢.*/` is not reported for an Adlam word, and `a😀` gives only `[a😀]`.

## Files

- [src/org/apache/lucene/monitor/TestSuffixingNGramSupplementaryChars.java](src/org/apache/lucene/monitor/TestSuffixingNGramSupplementaryChars.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestSuffixingNGramSupplementaryChars
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestRegexpQueryHandlerSupplementaryChars.java](TestRegexpQueryHandlerSupplementaryChars.java)
- [fix-with-test.diff](fix-with-test.diff)
- [TestRegexpHandlerDefaultAnalyzersV3.java](TestRegexpHandlerDefaultAnalyzersV3.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
