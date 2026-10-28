# Word2VecModel: a term added twice silently shifts the vector of every later term, so Word2VecSynonymFilter returns synonyms computed from other terms' vectors

Bug LFV-867 · Area: analysis-common / synonym.word2vec (Word2VecModel.addTermAndVector term-to-vector mapping; Dl4jModelReader; Word2VecSynonymProvider and Word2VecSynonymFilter as consumers) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-867.html](../../bugs/LFV-867.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a Word2VecModel in which one term appears twice, through the public addTermAndVector or by loading a Deeplearning4j-format zip with Dl4jModelReader or Word2VecSynonymFilterFactory, and you expect every term to keep its own vector, as the class javadoc ('containing the vectors for each word in dictionary') and the upstream test of vectorValue(term) say. addTermAndVector stores each entry at the next array slot but ignores that BytesRefHash.add gives no new id to a term it already holds, while vectorValue(term) looks the vector up by that id, so after the first repeat every later distinct term reads the slot of the term before it. For the model [a(1,0), a(1,0), b(0,1), c(-1,0)] vectorValue(b) returns a's vector and vectorValue(c) returns b's, and with the model [car, car, auto, banana, fruit] and the factory defaults 'banana' is expanded with the neighbours of 'auto' ([auto, car, car]) instead of [fruit], with no error or warning.

## Expected vs actual

- Expected: `vectorValue("b")` = (0, 1) and `vectorValue("c")` = (-1, 0); `banana` analyses to `[banana, fruit]` (or the repeated term is rejected).
- Actual: `vectorValue("b")` = (1, 0), a's vector, and `banana` analyses to `[banana, auto, car, car]`; the model loads without any error.

## Files

- [src/org/apache/lucene/analysis/synonym/word2vec/TestWord2VecModelRepeatedTerm.java](src/org/apache/lucene/analysis/synonym/word2vec/TestWord2VecModelRepeatedTerm.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.synonym.word2vec.TestWord2VecModelRepeatedTerm
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestWord2VecRepeatedTerm.java](TestWord2VecRepeatedTerm.java)
- [TestWord2VecModelRepeatedTerm.java](TestWord2VecModelRepeatedTerm.java)
- [TestWord2VecDuplicateTermV1.java](TestWord2VecDuplicateTermV1.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
