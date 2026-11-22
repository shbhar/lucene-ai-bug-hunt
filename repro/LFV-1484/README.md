# The Monitor's default term presearcher can silently miss a matching TermQuery on a binary (non-UTF-8) term, or throw ArrayIndexOutOfBoundsException from Monitor.register or Monitor.match, because TermsEnumTokenStream's UTF-8 decode reads past the end of the term (every release since 8.2.0)

Finding LFV-1484 · Area: monitor / presearcher term streams (TermsEnumTokenStream under TermFilteredPresearcher and MultipassTermFilteredPresearcher: binary, non-UTF-8 document and query terms) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1484.html](../../bugs/LFV-1484.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register a TermQuery on a binary term (for example the one-byte id {C8}, built with Term(String, BytesRef)) in a Monitor with the default presearcher, and match a document that holds the same id in a StringField plus one more field. You expect the query to be reported, as it is with Presearcher.NO_FILTERING, because the document contains the query's term. Instead Monitor.match reports 0 matches with no error: the presearcher turns every term into text with a UTF-8 decoder that, for a term ending in the first byte of a multi-byte character, reads on into whatever bytes lie behind the term, so the query side and the document side get different tokens for the same term and the query is never run; a term whose last byte is F0 or above (F1..F7 with assertions on and off, F9..FF without assertions, F0 and, without assertions, F8 depending on the bytes behind the term) can instead make Monitor.register or Monitor.match throw ArrayIndexOutOfBoundsException. The same miss is reached with ordinary text through a stock analyzer. A default Monitor over ICUCollationKeyAnalyzer (root locale, PRIMARY strength) holds the query that QueryBuilder builds for the Russian word день (day). For a document with день in one field and a second field added after it, it reports 0 matches instead of 1. The collation key of день ends in the bytes E8 00 (the key is 62 1A 28 78 E8 00 with ICU4J 77.1, 78.3 and 79.1). The decoder takes these two bytes for the first two bytes of a three-byte sequence and reads one byte behind the key.

## Expected vs actual

- Expected: 1 match: the document contains the query's term (`Presearcher.NO_FILTERING` reports 1).
- Actual: 0 matches and no error (`expected:<1> but was:<0>` at line 41), with assertions on and off.

## Files

- [src/org/apache/lucene/monitor/TestBinaryTermPresearcher.java](src/org/apache/lucene/monitor/TestBinaryTermPresearcher.java) (JUnit test)

## Run it

- Tested on Lucene main 1a59f96fc6de (a commit of 2026-11-21): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it registers a TermQuery on the one-byte term {C8} in a Monitor with the default presearcher and matches one document that holds that term in a StringField plus a second field; it asserts 1 match and gets 0. The ArrayIndexOutOfBoundsException for terms that end in F1..F7 and the collation-key case of the summary (the Russian word день under ICUCollationKeyAnalyzer at PRIMARY strength) are not part of this test.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestBinaryTermPresearcher
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestBinaryTermPresearcher.java](TestBinaryTermPresearcher.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
