# Hunspell spell and suggest throw AssertionError under -ea when a word ends with a CHECKCOMPOUNDPATTERN replacement whose beginchars are empty, because the compound check looks up a zero-length second part

Bug LFV-713 · Area: analysis / hunspell (Hunspell.spell and suggest compound checking, CHECKCOMPOUNDPATTERN replacements) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary with a compound rule such as 'CHECKCOMPOUNDPATTERN o /X z' (a simplified compound form whose second part may begin with anything: hunspell(5) allows empty beginchars with a flag and an optional replacement) and call Hunspell.spell("foz"), and expect true or false; C Hunspell 1.3.2 and 1.7.2 both say false. Instead, with assertions enabled, spell and suggest throw java.lang.AssertionError from WordStorage.lookupWord ('assert length &gt; 0'). The cause: checkCompoundPatternReplacements expands the final 'z' to 'o' plus empty beginchars and puts the break right after 'foo', but never checks that a second part remains, so checkCompoundsAfter looks up a second part of length 0; with -da that lookup finds nothing and spell returns false for the repro.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: With assertions enabled, spell and suggest throw java.lang.AssertionError from WordStorage.lookupWord ('assert length &gt; 0').

## Files

- [src/org/apache/lucene/analysis/hunspell/TestCompoundPatternEmptyTail.java](src/org/apache/lucene/analysis/hunspell/TestCompoundPatternEmptyTail.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hunspell.TestCompoundPatternEmptyTail
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
