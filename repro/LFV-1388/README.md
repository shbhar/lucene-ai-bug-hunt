# TeluguAnalyzer folds the long vowel sign ో (ō) to ొ (o) before TeluguStemFilter runs, so TeluguStemmer's locative '-లో' (-lō) rule can never fire, and 'నగరంలో' (nagaramlō, 'in the city') is indexed as 'నగరంల' (nagaramla) instead of 'నగరం' (nagaram)

Finding LFV-1388 · Area: analysis-common / Telugu analyzer (TeluguAnalyzer filter chain; TeluguStemmer suffix table versus TeluguNormalizer) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1388.html](../../bugs/LFV-1388.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Telugu text with the stock TeluguAnalyzer; a document says 'నగరంలో' (nagaramlō, 'in the city') and a query says 'నగరం' (nagaram, 'city'). You expect both to get the term 'నగరం', because the analyzer's last filter, TeluguStemFilter, applies TeluguStemmer, whose rule table strips the locative suffix '-లో' (-lō), and the filter on its own does turn నగరంలో into నగరం. Instead TeluguNormalizationFilter, which runs earlier in the chain, always rewrites the long vowel sign ో (ō) to the short ొ (o), so the stemmer's 'లో' (lō) rule can never match: only the final vowel sign is removed, 'నగరంలో' is indexed as 'నగరంల' (nagaramla), and the query finds nothing, with no error.

## Expected vs actual

- Expected: The term నగరం (nagaram), the same term the bare noun gets.
- Actual: The term నగరంల (nagaramla): `term 0 expected:<నగరం[]> but was:<నగరం[ల]>`, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/te/TestTeluguAnalyzerLocativeSuffix.java](src/org/apache/lucene/analysis/te/TestTeluguAnalyzerLocativeSuffix.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.te.TestTeluguAnalyzerLocativeSuffix
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTeluguAnalyzerLocativeSuffix.java](TestTeluguAnalyzerLocativeSuffix.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
