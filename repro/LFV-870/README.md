# Word2VecSynonymFilter documents minAcceptedSimilarity as a cosine similarity, but Word2VecSynonymProvider compares it with the normalised score (1 + cosine) / 2, so the effective cosine cutoff is 2m - 1 (the default 0.8 accepts cosine 0.6, 0.5 accepts orthogonal terms, values below 0.5 accept negatively correlated terms); documentation defect, the javadoc fix is the default proposal

Bug LFV-870 · Area: analysis-common / synonym.word2vec (Word2VecSynonymFilter minAcceptedSimilarity javadoc vs Word2VecSynonymProvider threshold test) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build Word2VecSynonymFilter with minAcceptedSimilarity 0.8 (the factory default), whose only documentation says it is the 'minimal value of cosine similarity between the searched vector and the retrieved ones', so you expect only model terms at cosine 0.8 or more as synonyms. Word2VecSynonymProvider compares the threshold with the DOT_PRODUCT score of the unit-length model vectors, which is (1 + cosine) / 2, so the real cosine cutoff is 2m - 1: at 0.8 a term at cosine 0.65 is emitted, at 0.5 an orthogonal term (cosine 0) is emitted, and at 0.2 a term at cosine -0.5 is emitted. The authors' intent points to the score scale (the factory accepts only (0, 1]), so the fix is a javadoc clarification, not a code change.

The test shows that Word2VecSynonymFilter's minAcceptedSimilarity, documented as a minimal cosine similarity, is compared with the score (1 + cosine) / 2: at 0.8 (the factory default) a term at cosine 0.707 is emitted as a synonym.

## Expected vs actual

Expected only the token `a` (b's cosine 0.707 is below 0.8); actual: `b` is added too (`TokenStream has more tokens than expected (expected count=1); extra token=b`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/synonym/word2vec/TestWord2VecMinAcceptedSimilarityCosine.java](src/org/apache/lucene/analysis/synonym/word2vec/TestWord2VecMinAcceptedSimilarityCosine.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.synonym.word2vec.TestWord2VecMinAcceptedSimilarityCosine
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/word2vec-synonym-provider-vs-exact-cosine-reference/](../../models/word2vec-synonym-provider-vs-exact-cosine-reference/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
