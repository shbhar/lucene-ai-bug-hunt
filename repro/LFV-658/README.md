# Nori: user-dictionary nouns still use the context ids of mecab-ko-dic 2.0.3 after Lucene 10.0 moved to mecab-ko-dic 2.1.1, so the particle after a user noun gets the wrong part of speech (학교가: 가 becomes the verb 'go'; 학생이: 이 becomes the copula) and KoreanAnalyzer indexes spurious terms such as 가 and 이

Bug LFV-658 · Area: analysis / nori KoreanTokenizer user dictionary (UserDictionary right ids, UserMorphData left id vs the bundled mecab-ko-dic-2.1.1 connection costs) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-658.html](../../bugs/LFV-658.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a noun such as 학교 ('school') to a nori user dictionary, or a new word such as 깃허브, and analyze '학교가' or '깃허브가 있다' with KoreanTokenizer or KoreanAnalyzer, and expect the noun to connect to the following particle like a plain common noun (NNG) with the same final syllable, which is what the constants' own comments say ('NNG left', 'NNG right with hangul and a coda on the last char', '... no coda') and what 9.x does. Instead the four hard-coded user-noun context ids are still the numbers of mecab-ko-dic 2.0.3, while 10.0.0 switched the bundled dictionary to mecab-ko-dic 2.1.1, in which those numbers name other contexts (the coda flag is inverted and the left id is a 'state change' noun subclass), so the subject particle 가 is analyzed as the verb 가 'go' (VV) and 이 as the copula (VCP) or a complement particle, and KoreanAnalyzer indexes [학교, 가] instead of [학교] and [깃허브, 가, 있] instead of [깃허브, 있]. Setting the constants to the 2.1.1 ids (LEFT_ID 1780, RIGHT_ID 3532, RIGHT_ID_T 3534, RIGHT_ID_F 3533) restores the analysis the bundled dictionary gives the same noun.

The test shows that adding the plain noun `학교` to a nori user dictionary changes how `KoreanAnalyzer` analyses `학교가`, because the user-noun context ids are still those of mecab-ko-dic 2.0.3 while the bundled dictionary is 2.1.1.

## Expected vs actual

Expected `[학교]`, the terms without the user entry; actual: `[학교, 가]` (`expected:<[학교]> but was:<[학교, 가]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/ko/TestUserNounParticle.java](src/org/apache/lucene/analysis/ko/TestUserNounParticle.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.TestUserNounParticle
```

The module(s) analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
