# WordDelimiterGraphFilter catenates non-adjacent subwords and gives catenations a too-long position length when a catenation run starts with a subword typed ALPHA|DIGIT (any supplementary character such as an emoji, or a character mapped to ALPHANUM in a types table), so graph phrase queries can match documents that lack a subword (released since 6.5.0)

Bug LFV-337 · Area: analysis / WordDelimiterGraphFilter catenation runs (CATENATE_WORDS, CATENATE_NUMBERS; WordDelimiterIterator character types; factory 'types' option; term-level half shared with the deprecated WordDelimiterFilter) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-337.html](../../bugs/LFV-337.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run WordDelimiterGraphFilter with CATENATE_WORDS or CATENATE_NUMBERS on a token such as 'U+20000-ab-12' (a supplementary character at the start) or '@x-ab-12' with '@' mapped to ALPHANUM, and expect catenations only of adjacent subwords of one kind, each placed over exactly the parts it contains, as the ASCII control 'x-ab-12' gets (xab at [0,2), no number catenation). Instead, because such a subword is typed ALPHA|DIGIT and a run started by it is never ended by a subword it does not absorb, the filter emits catenations of non-adjacent parts ('U+20000 12', '@x12', 'U+20000abcd' across '12') and catenations whose position length also covers the skipped part ('U+20000ab' at [0,3), over '12'). At search time QueryBuilder turns that graph into a phrase query with a bare-term alternative, so the phrase 'U+20000hot-sale-50' also matches a document 'U+20000hot-sale-49' (executed).

The test shows that `WordDelimiterGraphFilter` with `CATENATE_NUMBERS` catenates non-adjacent parts when a run starts with a subword typed ALPHA|DIGIT: `#x-ab-12` with `#` typed `ALPHANUM` (public constructor type table) emits `#x12` across the word part `ab`.

## Expected vs actual

Expected tokens `[#x, ab, 12]`; actual has an extra first token `#x12` (`term 0 expected:<#x[]> but was:<#x[12]>`), with `-ea` and `-da`; passes with the doc's option A fix.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestWordDelimiterGraphFilterAlphanumCatenation.java](src/org/apache/lucene/analysis/miscellaneous/TestWordDelimiterGraphFilterAlphanumCatenation.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestWordDelimiterGraphFilterAlphanumCatenation
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
