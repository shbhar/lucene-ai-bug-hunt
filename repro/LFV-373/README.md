# Hunspell spell-checker: CHECKCOMPOUNDTRIPLE misses an x|xx triple letter (one letter before the boundary, two after) at the second and later boundaries of a compound, so words such as operabareeel (opera + bare|eel) are accepted (absolute break position compared with the tail's relative length)

Bug LFV-373 · Area: analysis / hunspell (Hunspell.spell compound checking) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary with CHECKCOMPOUNDTRIPLE, which hunspell(5) defines as 'Forbid compounding, if compound word contains triple repeating letters (e.g. foo|ox or xo|oof)', and expect Hunspell.spell to reject a compound with a triple letter at any boundary, as C Hunspell 1.3.2 and 1.7.2 do and as Lucene's own checkcompoundtriple fixture requires for bareeel (bare|eel). Instead Lucene rejects bareeel but accepts operabareeel, barebareeel and foobareeel (opera, bare or foo + bare|eel), and with the public Swedish dictionary (sv_SE) it accepts sommarmusikradiooomskuren while it rejects musikradiooomskuren. The cause: Hunspell.mayBreakIntoCompounds receives an absolute break position (the tail's offset in the word plus the relative break) but tests 'breakPos &lt; length - 1' against the tail's relative length, so at the second and later boundaries the x|xx half of the check is skipped whenever the absolute break position is at least the tail length minus 1, while the xx|x half ('breakPos &gt; offset + 1') is offset-aware and still works.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Lucene rejects bareeel but accepts operabareeel, barebareeel and foobareeel (opera, bare or foo + bare|eel), and with the public Swedish dictionary (sv_SE) it accepts sommarmusikradiooomskuren while it rejects musikradiooomskuren.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestCheckCompoundTripleLaterBoundary.java](src/org/apache/lucene/analysis/hunspell/TestCheckCompoundTripleLaterBoundary.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.analysis.hunspell.TestCheckCompoundTripleLaterBoundary
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hunspell/](../../models/hunspell/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
