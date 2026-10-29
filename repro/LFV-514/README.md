# Bundled commons-codec upgrades changed phonetic filter tokens (RefinedSoundex in 10.2.0; Metaphone, ColognePhonetic and Daitch-Mokotoff on main), and neither CHANGES.txt nor MIGRATE.md mentions it or asks for re-indexing (documentation defect)

Bug LFV-514 · Area: analysis / phonetic (PhoneticFilter and DaitchMokotoffSoundexFilter output from the bundled commons-codec); release notes (lucene/CHANGES.txt, lucene/MIGRATE.md) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a field with a phonetic filter, for example PhoneticFilter with ColognePhonetic, upgrade Lucene and keep searching the same index; you expect the same tokens or a CHANGES/MIGRATE note telling you to re-index, as Lucene gave in 2014 for the Beider-Morse change of the commons-codec 1.10 upgrade (LUCENE-6058) and gives on main for the Snowball upgrade (GITHUB#15505). Instead the bundled commons-codec moved from 1.13 (up to 10.1.0) to 1.17.2 (10.2.0 to 10.5.2) and to 1.23.0 (main) with no note anywhere, and its encoder fixes change the tokens: 'Köln' gets the RefinedSoundex code K378 from 10.2.0 (10.1.0 kept only the word), and on main 'chantaient' becomes XNTN instead of KNTN (Metaphone), 'quick' 4 instead of 44 (ColognePhonetic) and "it's" 040000 instead of 034000 (Daitch-Mokotoff). An index written by the older version and searched by the newer one silently misses or changes matches in that field until it is re-indexed (executed: ColognePhonetic with inject=false, the query 'quick' finds no document in a 10.5.2 index searched by main, against 2 in a fresh index).

The test shows that after main's bundled commons-codec upgrade (1.17.2 in 10.5.2, 1.23.0 on main), `PhoneticFilter` with `Metaphone` emits a different token for "chair", and neither CHANGES.txt nor MIGRATE.md says that phonetic fields must be re-indexed. The new code is the correct one; the missing note is the defect.

## Expected vs actual

Expected: KR, the 10.5.2 token (the same test passes on 10.5.2). Actual on main: `term 0 expected:<[K]R> but was:<[X]R>`.

## Files

- [src/org/apache/lucene/analysis/phonetic/TestMetaphoneTokenSinceLastRelease.java](src/org/apache/lucene/analysis/phonetic/TestMetaphoneTokenSinceLastRelease.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/phonetic/build/libs/lucene-analysis-phonetic-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.phonetic.TestMetaphoneTokenSinceLastRelease
```

The module(s) analysis-phonetic also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
