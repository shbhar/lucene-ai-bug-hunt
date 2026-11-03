# ICUTransformFilterFactory accepts direction="Forward" or "FORWARD" without error (it validates the value case-insensitively) but then builds the reverse transliterator, so every token is silently transformed the wrong way (regression of LUCENE-4877, since 4.3.0)

Finding LFV-1119 · Area: analysis / icu (ICUTransformFilterFactory: 'direction' parameter parsing through AbstractAnalysisFactory.get(..., caseSensitive=false)) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1119.html](../../bugs/LFV-1119.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an ICUTransformFilterFactory with id=Cyrillic-Latin and direction="Forward", which the factory accepts because it checks direction case-insensitively, and analyze 'Российская Федерация'. You expect the forward Cyrillic-Latin transliteration [Rossijskaâ, Federaciâ], as with direction="forward". You get the Cyrillic tokens back unchanged, with no error: the validation hands back the caller's own spelling, the factory then tests "forward".equals(direction), and every accepted spelling other than exact lower-case 'forward' builds the reverse (Latin-Cyrillic) transliterator.

## Expected vs actual

- Expected: `Federaciâ`, the forward Cyrillic-to-Latin result, as with `direction="forward"`.
- Actual: `term 0 expected:<[Federaciâ]> but was:<[Федерация]>`: the reverse transform ran; the same with assertions off.

## Files

- [src/org/apache/lucene/analysis/icu/TestICUTransformForwardDirection.java](src/org/apache/lucene/analysis/icu/TestICUTransformForwardDirection.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.TestICUTransformForwardDirection
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestICUTransformForwardDirection.java](TestICUTransformForwardDirection.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
