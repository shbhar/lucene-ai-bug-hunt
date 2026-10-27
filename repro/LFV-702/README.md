# KoreanPartOfSpeechStopFilterFactory's class javadoc example tags="E,J" fails with IllegalArgumentException ('No enum constant org.apache.lucene.analysis.ko.POS.Tag.E') since 10.0.0: GITHUB#11778 replaced the POS tags E and J with fine-grained tags and updated the factory test, but not the javadoc example, and no CHANGES or MIGRATE note survives (documentation defect; the tag change is intended)

Bug LFV-702 · Area: analysis-nori / KoreanPartOfSpeechStopFilterFactory (javadoc example of the stop tags) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure KoreanPartOfSpeechStopFilterFactory exactly as its class javadoc shows, tags="E,J" (verbal endings and particles), through the constructor, the SPI name koreanPartOfSpeechStop or CustomAnalyzer, and expect an analyzer that drops endings and particles, as the same setting does on 9.x. Since 10.0.0 the factory constructor instead throws IllegalArgumentException 'No enum constant org.apache.lucene.analysis.ko.POS.Tag.E', because GITHUB#11778 deliberately replaced E and J with fine-grained tags (EP, EF, EC, ETN, ETM and JKS... JC) and POS.resolveTag became an exact Tag.valueOf lookup. The tag change itself is intended; the defect is that the javadoc example (KoreanPartOfSpeechStopFilterFactory.java:28-36) was not updated and no CHANGES or MIGRATE entry tells 9.x users.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/analysis/ko/TestKoreanPosStopFactoryJavadocExample.java](src/org/apache/lucene/analysis/ko/TestKoreanPosStopFactoryJavadocExample.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.TestKoreanPosStopFactoryJavadocExample
```

The module(s) analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
