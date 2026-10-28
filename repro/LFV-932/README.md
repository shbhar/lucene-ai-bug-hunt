# Hunspell Dictionary with FLAG UTF-8 and an 8-bit SET other than ISO8859-1 silently merges UTF-8 flags that share a lead byte, so spell and the stemmer accept forms the dictionary forbids (some SET values fail to load instead)

Bug LFV-932 · Area: analysis / hunspell (Dictionary FLAG UTF-8 parsing under the SET encoding) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary whose affix file says FLAG UTF-8 (flags are UTF-8 characters) and SET ISO8859-2, KOI8-R, microsoft-cp1251 or another 8-bit encoding except ISO8859-1, with two suffix classes whose flags are, say, U+00E1 and U+00E2, and expect what C Hunspell 1.3.2 and 1.7.2 do on the same bytes: a word that carries only the first flag takes only the first suffix. Instead Lucene reads the flags from text already decoded with the SET charset, so each 2-byte flag becomes two characters and an affix rule keeps only the first one, the shared lead byte: both classes merge, spell accepts the forbidden forms (kote, lasa in the repro) and the stemmer returns kot for kote, with no error. Only no SET, SET ISO8859-1 and SET UTF-8 work; ISO8859-14 merges too (its decoder reports ISO-8859-1 as its charset, and the re-encode turns remapped flag bytes into '?'), and with ISO8859-3, -6 or -8 the load throws UnmappableCharacterException because a flag byte is unassigned in that charset.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Lucene reads the flags from text already decoded with the SET charset, so each 2-byte flag becomes two characters and an affix rule keeps only the first one, the shared lead byte: both classes merge, spell accepts the forbidden forms (kote, lasa in the repro) and the stemmer returns kot for kote, with no error.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestFlagUtf8With8BitSet.java](src/org/apache/lucene/analysis/hunspell/TestFlagUtf8With8BitSet.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.hunspell.TestFlagUtf8With8BitSet
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
