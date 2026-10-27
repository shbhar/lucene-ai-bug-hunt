# BrazilianStemmer throws away the result of its "logias" -&gt; "log" rule, so plural -logias nouns stay unstemmed and stop matching their singular

Bug LFV-663 · Area: analysis-common / Brazilian Portuguese stemmer (BrazilianStemmer.step1; BrazilianStemFilter, BrazilianAnalyzer and the 'brazilianStem' factory) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Brazilian Portuguese text with the stock BrazilianAnalyzer (or BrazilianStemFilter), one document says 'metodologia' and a query says 'metodologias'; you expect both words to stem to 'metodolog' and match, as the stemmer's own rule table says ('logias' in R2 is replaced by 'log', exactly like the singular 'logia'). Instead step1 calls replaceSuffix(CT, "logias", "log") without assigning the result and still returns true, which also makes stem skip step2 and step4, so the plural is indexed as the whole word 'metodologias' and the search finds 0 hits. Long -logias plurals such as metodologias, antropologias, terminologias, epistemologias, fenomenologias and odontologias are hit; shorter ones such as tecnologias, ideologias and biologias are not, because the rule does not fire for them and step2 stems them.

## Expected vs actual

- Expected: You expect both words to stem to 'metodolog' and match, as the stemmer's own rule table says ('logias' in R2 is replaced by 'log', exactly like the singular 'logia').
- Actual: Step1 calls replaceSuffix(CT, "logias", "log") without assigning the result and still returns true, which also makes stem skip step2 and step4, so the plural is indexed as the whole word 'metodologias' and the search finds 0 hits.

## Files

- [src/org/apache/lucene/analysis/br/TestBrazilianLogiasPlural.java](src/org/apache/lucene/analysis/br/TestBrazilianLogiasPlural.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.br.TestBrazilianLogiasPlural
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
