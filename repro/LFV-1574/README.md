# AlbanianNormalizer decides whether a token keeps its c before it handles combining marks, so a word under a cwords.txt entry written with e + combining diaeresis inside the matched part can fold its c to q: the token k, e, U+0308, p, u, c, e, U+0308 gives 'kepuqe', the precomposed spelling of the same word gives 'kepuce' (new code on main and branch_11x, both unreleased; in no release)

Finding LFV-1574 · Area: analysis-common / Albanian normalizer (AlbanianNormalizer.normalize and keepsC; AlbanianNormalizationFilter, the 'albanianNormalization' factory and AlbanianAnalyzer); new on main and branch_11x, both unreleased · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze Albanian text with the new AlbanianAnalyzer or AlbanianNormalizationFilter (code that is on main and on branch_11x since 2026-11-20 and in no release). The normalizer's class javadoc says ë is folded to e, and ç and c to q, 'whether precomposed or written as a base letter followed by a combining diaeresis / cedilla (text copied from PDFs and some web pages comes in that decomposed form)', and that words that really have a c keep it because they are listed in cwords.txt, so you expect a listed word such as këpucë (entry kepuc*) to get one term however its ë is encoded. Instead the normalizer decides whether the token keeps its c before it handles the combining marks, so këpucë written with e + U+0308 becomes 'kepuqe' while the precomposed spelling and the spelling typed without marks become 'kepuce' (through the analyzer 'kepuk' against 'kepuc'), and in the runs a term query in one spelling found no document indexed in the other, with no error.

## Expected vs actual

- Expected: You expect a listed word such as këpucë (entry kepuc*) to get one term however its ë is encoded.
- Actual: The normalizer decides whether the token keeps its c before it handles the combining marks, so këpucë written with e + U+0308 becomes 'kepuqe' while the precomposed spelling and the spelling typed without marks become 'kepuce' (through the analyzer 'kepuk' against 'kepuc'), and in the runs a term query in one spelling found no document indexed in the other, with no error.

## Files

- [src/org/apache/lucene/analysis/sq/TestAlbanianDecomposedKeepC.java](src/org/apache/lucene/analysis/sq/TestAlbanianDecomposedKeepC.java) (JUnit test)

## Run it

- Tested on Lucene main 1a59f96fc6de (a commit of 2026-11-21): still reproduces.
- Tested on Lucene branch_11x 3221dd4cf3a1 (a commit of 2026-11-20): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there.
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.sq.TestAlbanianDecomposedKeepC
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
