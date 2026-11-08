# QueryAutoStopWordAnalyzer's percentage constructors compute the stop-word threshold from numDocs() (live documents) but compare it with docFreq(), which still counts deleted documents, so after updateDocument terms below the configured percentage become stop words and are silently dropped from queries (since 2.4.0)

Finding LFV-1364 · Area: analysis / QueryAutoStopWordAnalyzer (analysis-common query package; percentage-constructor stop-word threshold: IndexReader.numDocs vs TermsEnum.docFreq) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1364.html](../../bugs/LFV-1364.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index three documents whose body fields are 'w', 'x' and 'y', re-index each one unchanged with updateDocument (the deleted copies are not merged away yet), open a reader and build new QueryAutoStopWordAnalyzer(analyzer, reader) with its default 40% threshold; you expect no stop words, because the javadoc makes a term a stop word only when it is in more than maxPercentDocs 'of index documents', and each term is in 1 of the 3 live documents and in 2 of the 6 documents that count the deleted copies (33% either way). Instead getStopWords("body") returns [w, x, y] and the analyzer drops 'w' from a query: the percentage constructor computes the threshold from live documents only, (int) (numDocs() * 0.4f) = 1 (QueryAutoStopWordAnalyzer.java:113), but compares it with TermsEnum.docFreq() = 2 (:140), which still counts the deleted copies, so the 'percentage' it tests is 67% here and can exceed 100%.

## Expected vs actual

- Expected: `getStopWords("body")` is empty: each term is in 33% of the documents, below the default 40%.
- Actual: `AssertionError: expected:<[]> but was:<[w, x, y]>`: every term of the field is a stop word.

## Files

- [src/org/apache/lucene/analysis/query/TestQueryAutoStopWordAnalyzerDeletes.java](src/org/apache/lucene/analysis/query/TestQueryAutoStopWordAnalyzerDeletes.java) (JUnit test)

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
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.query.TestQueryAutoStopWordAnalyzerDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestQueryAutoStopWordAnalyzerDeletes.java](TestQueryAutoStopWordAnalyzerDeletes.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
