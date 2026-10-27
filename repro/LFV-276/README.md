# MatchRegionRetriever's default OffsetsFromTokens strategy ignores QueryVisitor#consumeTermsMatching, so prefix, wildcard, regexp, term-range and multi-term TermInSetQuery matches on fields without positions get no hit regions

Bug LFV-276 · Area: highlighter / matchhighlight (MatchRegionRetriever default offset strategy OffsetsFromTokens for fields indexed without positions; MatchHighlighter on top of it) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a stored field without positions (one document 'foo bar', IndexOptions DOCS_AND_FREQS, or a StringField), run a PrefixQuery('ba') that matches it, and ask MatchRegionRetriever (or MatchHighlighter) with its default strategies for the hit regions; you expect the region [4,7) for 'bar', which a TermQuery('bar') on the same field, a FuzzyQuery('baz'~1) and the same PrefixQuery on a field with positions all give. Instead the field gets no hit regions at all ({}), although the document is a hit and Weight#matches reports a match on that field. The default strategy for such fields, OffsetsFromTokens, collects the query's terms only through QueryVisitor#consumeTerms, while constant-score multi-term queries report theirs through consumeTermsMatching, so its term set stays empty and no re-analysed token is kept.

## Expected vs actual

- Expected: You expect the region [4,7) for 'bar', which a TermQuery('bar') on the same field, a FuzzyQuery('baz'~1) and the same PrefixQuery on a field with positions all give.
- Actual: The field gets no hit regions at all ({}), although the document is a hit and Weight#matches reports a match on that field.

## Files

- [src/org/apache/lucene/search/matchhighlight/TestOffsetsFromTokensMultiTermQuery.java](src/org/apache/lucene/search/matchhighlight/TestOffsetsFromTokensMultiTermQuery.java) (JUnit test)
- [src2/org/apache/lucene/search/matchhighlight/TestOffsetsFromTokensMultiTermQuery.java](src2/org/apache/lucene/search/matchhighlight/TestOffsetsFromTokensMultiTermQuery.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.matchhighlight.TestOffsetsFromTokensMultiTermQuery
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.matchhighlight.TestOffsetsFromTokensMultiTermQuery
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
