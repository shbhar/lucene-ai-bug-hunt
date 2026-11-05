# GreekStemmer rule11 tests the suffix 'ομαστε' before 'ιομαστε', so its 'ιομαστε' branch can never run and -ιόμαστε verb forms keep a trailing ι: GreekAnalyzer stems 'αγαπιόμαστε' to 'αγαπι' instead of 'αγαπ', so it no longer matches the other forms of the same verb

Finding LFV-1385 · Area: analysis-common / Greek stemmer (GreekStemmer.rule11; GreekStemFilter, GreekAnalyzer and the 'greekStem' factory) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Greek text with the stock GreekAnalyzer; one document says 'αγαπιόμαστε' ('we are loved') and a query says 'αγαπιέμαι' ('I am loved'). You expect both to stem to 'αγαπ', because GreekStemmer says it follows Ntais's thesis, whose rule-set 11 removes both -ομαστε and -ιομαστε, gives ΑΓΑΠΙΟΜΑΣΤΕ -&gt; ΑΓΑΠ as its own example and always applies the longest suffix, and the class even has a dedicated branch for 'ιομαστε'. Instead rule11 tests 'ομαστε' first (GreekStemmer.java:475), which every -ιομαστε word also ends with, so it removes only 6 characters and the 'ιομαστε' branch at :480 never runs: 'αγαπιόμαστε' is indexed as 'αγαπι' and the query finds nothing, with no error.

## Expected vs actual

- Expected: αγαπιόμαστε (agapiómaste) is analyzed to the single term `αγαπ` (agap), and the test passes.
- Actual: The term is `αγαπι` (agapi): `term 0 expected:<αγαπ[]> but was:<αγαπ[ι]>`.

## Files

- [src/org/apache/lucene/analysis/el/TestGreekStemmerIomaste.java](src/org/apache/lucene/analysis/el/TestGreekStemmerIomaste.java) (JUnit test)

## Run it

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
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.el.TestGreekStemmerIomaste
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/greek-stemmer-rule11-iomaste-branch-unreachable/](../../models/greek-stemmer-rule11-iomaste-branch-unreachable/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
