# BaseSimilarityTestCase's 'scores don't decrease as terms get rarer' check is dead code

Bug LFV-073 · Area: test-framework / similarities · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You validate a custom Similarity with lucene-test-framework's BaseSimilarityTestCase. You expect it to check that scores do not decrease as terms get rarer, as documented. Instead the check rescores term instead of prevTerm and can never fail; fixing it fails TestBasicModelG 8/8, Indri 4/8 and SPL 1/8 seeds.

## Expected vs actual

- Expected: You expect it to check that scores do not decrease as terms get rarer, as documented.
- Actual: The check rescores term instead of prevTerm and can never fail; fixing it fails TestBasicModelG 8/8, Indri 4/8 and SPL 1/8 seeds.

## Files

- [src/AntiIdfSimilarity.java](src/AntiIdfSimilarity.java)
- [src/AntiIdfSimilarityConformanceTest.java](src/AntiIdfSimilarityConformanceTest.java) (JUnit test)
- [src/RealisticRarerTermTest.java](src/RealisticRarerTermTest.java) (JUnit test)
- [src/VerifyRarerTermCheckTest.java](src/VerifyRarerTermCheckTest.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- This repro reads private Lucene state by reflection (setAccessible), so it depends on internals that may change.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class VerifyRarerTermCheckTest

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class AntiIdfSimilarityConformanceTest \
       --select-class RealisticRarerTermTest
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-similarity-impacts-smt/](../../models/study-similarity-impacts-smt/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
