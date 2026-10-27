# ArabicStemmer removes at most one prefix, so after it strips the conjunction waw it never strips the article that follows (wa+bi-al-, wa+li-l-, wa+ka-al-, wa+fa-al-), unlike the light10 algorithm ArabicAnalyzer documents

Bug LFV-665 · Area: analysis-common / Arabic light stemmer (ArabicStemmer.stemPrefix; ArabicStemFilter, ArabicAnalyzer and the 'arabicStem' factory) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-665.html](../../bugs/LFV-665.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Arabic text with the stock ArabicAnalyzer, whose javadoc says it implements light stemming 'as specified by' the light10 paper; that paper removes a leading waw ('and') first and then a definite article, so 'وبالكتاب' ('and with the book') should stem to 'كتاب' ('book'), like 'الكتاب' and 'بالكتاب'. Instead ArabicStemmer.stemPrefix scans one list (the six articles, then waw) and returns after the first match, so once it has removed the waw it never tries the articles: 'وبالكتاب' becomes 'بالكتاب' and 'وللطلاب' becomes 'للطلاب'. A TermQuery for the analyzed form of 'الكتاب' then finds 0 of 1 documents that contain only 'وبالكتاب', with no error.

## Expected vs actual

- Expected: `وبالكتاب`, `وللطلاب` and `وكالبحر` stem to `كتاب`, `طلاب` and `بحر`, the analyzer gives `[كتاب, كتاب, كتاب]`, and the search finds 1 document.
- Actual: They stem to `بالكتاب`, `للطلاب` and `كالبحر`, the analyzer gives `[كتاب, كتاب, بالكتاب]`, and the search finds 0: 6 tests, 5 failed.

## Files

- [src/org/apache/lucene/analysis/ar/TestArabicWawBeforeArticle.java](src/org/apache/lucene/analysis/ar/TestArabicWawBeforeArticle.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.ar.TestArabicWawBeforeArticle
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestArabicWawThenArticle.java](TestArabicWawThenArticle.java)
- [TestArabicWawBeforeArticle.java](TestArabicWawBeforeArticle.java)
- [HistArabicFilterProbe.java](HistArabicFilterProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
