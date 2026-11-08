# LevenshteinAutomata.toAutomaton(0) rebuilds the word through a UTF-16 String, so a high and a low surrogate held as two separate symbols merge into one code point: the zero-edit automaton for the expert int[] word [0xD800, 0xDC00] rejects that word and accepts [0x10000], while toAutomaton(1) keeps the symbols apart (every release since 4.10.0)

Finding LFV-1314 · Area: core / util/automaton: LevenshteinAutomata (zero-edit branch of toAutomaton) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1314.html](../../bugs/LFV-1314.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a zero-edit Levenshtein automaton for a word that holds a high surrogate and a low surrogate as two separate symbols, for example new LevenshteinAutomata(new int[] {0xD800, 0xDC00}, Character.MAX_CODE_POINT, false).toAutomaton(0). You expect it to accept exactly that two-symbol word, as toAutomaton(1) on the same object does and as Lucene 4.1 to 4.9 did. Instead it rejects the word and accepts the one-symbol word [0x10000], two edits away, because the n == 0 branch writes the symbols into a Java String with UnicodeUtil.newString and reads it back with Automata.makeString(String), which joins the two surrogates into one code point.

## Expected vs actual

- Expected: `toAutomaton(0)` accepts its own word `[0xD83D, 0xDE00]` and the test passes.
- Actual: The word is rejected: the `assertTrue` at line 28 fails (`java.lang.AssertionError`), with assertions on and off.

## Files

- [src/org/apache/lucene/util/automaton/TestLevenshteinZeroEdits.java](src/org/apache/lucene/util/automaton/TestLevenshteinZeroEdits.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.automaton.TestLevenshteinZeroEdits
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLevenshteinZeroEdits.java](TestLevenshteinZeroEdits.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
