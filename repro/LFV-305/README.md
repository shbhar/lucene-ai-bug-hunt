# Classic QueryParser and MultiFieldQueryParser ignore an explicit phrase slop ("..."~N) when the phrase analyzes to a token graph (multi-word synonyms, WordDelimiterGraphFilter) or, for QueryParser, carries token boosts, and search with the default phraseSlop instead: fewer hits with the default slop 0, extra hits for an explicit ~0 when a larger default slop is set

Bug LFV-305 · Area: queryparser / classic QueryParser and MultiFieldQueryParser phrase slop (QueryParserBase.getFieldQuery(String,String,int), MultiFieldQueryParser.getFieldQuery and applySlop; core util/QueryBuilder graph and boosted phrases) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse "guinea pig food"~1 with the classic QueryParser over an analyzer with a multi-word synonym 'guinea pig =&gt; cavy' and expect the documented proximity search, the words within one position of each other on every synonym path, as "pig food"~1 gives for a phrase without synonyms. Instead the parser analyzes the phrase with its default phraseSlop (0) and applies the explicit ~1 afterwards only to a top-level PhraseQuery or MultiPhraseQuery, so the BooleanQuery of per-path phrases that a token graph produces ('body:"cavy food" body:"guinea pig food"') and the BoostQuery that a token boost produces ('(body:"pig food")^2.0' for "pig|2.0 food"~1) keep slop 0, and 'my guinea pig dry food' is not found. The same root cause also over-matches: after setPhraseSlop(1), an explicit exact "guinea pig food"~0 is searched with slop 1 and returns extra documents; MultiFieldQueryParser has the graph defect in both directions but not the boost one, because its applySlop unwraps BoostQuery (since 9.6.0, #12196).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The parser analyzes the phrase with its default phraseSlop (0) and applies the explicit ~1 afterwards only to a top-level PhraseQuery or MultiPhraseQuery, so the BooleanQuery of per-path phrases that a token graph produces ('body:"cavy food" body:"guinea pig food"') and the BoostQuery that a token boost produces ('(body:"pig food")^2.0' for "pig|2.0 food"~1) keep slop 0, and 'my guinea pig dry food' is not found.

## Files

- [src/org/apache/lucene/queryparser/classic/TestExplicitPhraseSlopDropped.java](src/org/apache/lucene/queryparser/classic/TestExplicitPhraseSlopDropped.java) (JUnit test)
- [src/GraphPhraseSlopBisect.java](src/GraphPhraseSlopBisect.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.classic.TestExplicitPhraseSlopDropped

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" GraphPhraseSlopBisect
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
