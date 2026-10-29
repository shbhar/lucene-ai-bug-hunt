# UTF32ToUTF8 still accepts overlong UTF-8 for ranges that span into 3- and 4-byte code points

Bug LFV-057 · Area: util / automaton · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-057.html](../../bugs/LFV-057.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You convert a code-point range that starts below U+0800 and ends at or above U+1000 into a UTF-8 automaton, as makeAnyChar and regex '.' do, and match it against binary terms that are not valid UTF-8. You expect only valid UTF-8 to match. Instead the overlong sequences E0 80..9F xx are accepted.

The test shows that the UTF-8 automaton that UTF32ToUTF8 builds (via ByteRunAutomaton) for the code-point range [U+0000, U+1000] accepts the overlong 3-byte sequence E0 80 80.

## Expected vs actual

Expected: rejected (E0 80 80 is an overlong U+0000 and the UTF-8 encoding of no code point); actual (-ea and -da): accepted.

## Files

- [src/org/apache/lucene/util/automaton/TestUTF32ToUTF8Overlong.java](src/org/apache/lucene/util/automaton/TestUTF32ToUTF8Overlong.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.automaton.TestUTF32ToUTF8Overlong
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-automaton-verification/](../../models/study-automaton-verification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
