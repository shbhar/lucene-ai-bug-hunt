# LMSimilarity.DefaultCollectionModel's javadoc gives p(w|C) as ttf / (T + 1) (and the LMSimilarity class javadoc as ttf / T), but computeProbability returns (ttf + 1) / (T + 1), so a user who computes p(w|C) from the docs is off by a factor (ttf + 1) / ttf (2x for a term that occurs once); Lucene's scores are correct (documentation defect)

Finding LFV-1444 · Area: core / search similarities (LMSimilarity.DefaultCollectionModel javadoc, language-model similarities) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1444.html](../../bugs/LFV-1444.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use LMDirichletSimilarity or LMJelinekMercerSimilarity with the default collection model and read the javadoc to work out p(w|C), the collection probability that both scores divide by. The DefaultCollectionModel javadoc says it is the term's number of occurrences in the collection divided by the total number of tokens + 1 (the LMSimilarity class javadoc says divided by the total number of tokens), so for a term that occurs once in a field of 9 tokens you expect 1/10. The code returns (ttf + 1) / (T + 1) = 2/10, on purpose since LUCENE-3430 (2011), so Lucene's scores are right and only the two javadoc sentences are stale.

## Expected vs actual

- Expected: 0.1: the javadoc's occurrences / (tokens + 1) = 1 / (9 + 1).
- Actual: `java.lang.AssertionError: expected:<0.1> but was:<0.2>` with assertions on and off: the code returns (1 + 1) / (9 + 1).

## Files

- [src/org/apache/lucene/search/similarities/TestDefaultCollectionModelFormula.java](src/org/apache/lucene/search/similarities/TestDefaultCollectionModelFormula.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.similarities.TestDefaultCollectionModelFormula
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDefaultCollectionModelFormula.java](TestDefaultCollectionModelFormula.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
