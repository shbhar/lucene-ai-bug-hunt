# WordDelimiterGraphFilter with IGNORE_KEYWORDS moves the position hole of a swallowed delimiter-only token (a standalone '/', '-' or '&amp;') to after the next keyword token, so exact phrases across the keyword miss or over-match (released since 7.4.0)

Bug LFV-353 · Area: analysis / WordDelimiterGraphFilter position increments (IGNORE_KEYWORDS keyword bypass; factory ignoreKeywords option) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-353.html](../../bugs/LFV-353.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze 'lucene / solr rocks' with a whitespace tokenizer, a keyword marker that marks 'solr', and WordDelimiterGraphFilter(GENERATE_WORD_PARTS | IGNORE_KEYWORDS), and expect the hole left by the swallowed '/' to stay in front of 'solr' (position increments {1, 2, 1}), exactly as when 'solr' is not a keyword, is marked without IGNORE_KEYWORDS, or is a protected word. Instead the keyword branch returns 'solr' before the pending increment is applied, so 'solr' gets 1 and 'rocks' gets 2 ({1, 1, 2}); with the same analyzer at index and query time the exact phrase "solr rocks" finds 0 of the 1 matching documents, and the phrase "lucene solr" matches the document 'lucene / solr', which the chain without the keyword mark does not.

## Expected vs actual

- Expected: Increments `{1, 2, 1}` for `lucene / solr rocks`; the phrase "solr rocks" finds 1 document and "lucene solr" finds 0.
- Actual: Increments `{1, 1, 2}` (`posIncrement 1 term=solr expected:<2> but was:<1>`); "solr rocks" finds 0 documents and "lucene solr" finds 2.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestWdgfKeywordPendingHole.java](src/org/apache/lucene/analysis/miscellaneous/TestWdgfKeywordPendingHole.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestWdgfKeywordPendingHole
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestWordDelimiterGraphFilterKeywordHole.java](TestWordDelimiterGraphFilterKeywordHole.java)
- [test-upstream.diff](test-upstream.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
