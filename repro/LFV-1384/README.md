# BrazilianStemmer step2 tests for the suffix 'ira' but removes 'ava', so -ira words (mentira, partirá) stay unstemmed and -ava verb forms (cantava, ficava) lose only the final 'a', and a search for one form misses the others (mentiras, cantavam, ficar)

Finding LFV-1384 · Area: analysis-common / Brazilian Portuguese stemmer (BrazilianStemmer.step2; BrazilianStemFilter, BrazilianAnalyzer and the 'brazilianStem' factory) · Priority: medium · Impact: silent wrong results · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Brazilian Portuguese text with the stock BrazilianAnalyzer; one document says 'mentiras' or 'eles cantavam', and a query says 'mentira' or 'cantava'. You expect each pair to share a stem (ment, cant), because step2 is documented to delete the verb suffix it finds in RV, the class's own rule list deletes both -ira and -ava, and Snowball Portuguese does the same. Instead the branch at BrazilianStemmer.java:958 tests 'ira' but removes 'ava', so -ira words keep the whole word (mentira stays mentira) and the class's correct 'ira' rule further down can never run, while no branch tests -ava at all, so cantava loses only its final 'a' (cantav); both queries find 0 hits.

## Expected vs actual

- Expected: You expect each pair to share a stem (ment, cant), because step2 is documented to delete the verb suffix it finds in RV, the class's own rule list deletes both -ira and -ava, and Snowball Portuguese does the same.
- Actual: The branch at BrazilianStemmer.java:958 tests 'ira' but removes 'ava', so -ira words keep the whole word (mentira stays mentira) and the class's correct 'ira' rule further down can never run, while no branch tests -ava at all, so cantava loses only its final 'a' (cantav); both queries find 0 hits.

## Files

- [src/org/apache/lucene/analysis/br/TestBrazilianStep2IraAva.java](src/org/apache/lucene/analysis/br/TestBrazilianStep2IraAva.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.br.TestBrazilianStep2IraAva
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/brazilian-stemmer-step2-ira-branch-removes-ava/](../../models/brazilian-stemmer-step2-ira-branch-removes-ava/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
