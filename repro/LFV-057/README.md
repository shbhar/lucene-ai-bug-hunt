# UTF32ToUTF8 still accepts overlong UTF-8 for ranges that span into 3- and 4-byte code points

Bug LFV-057 · Area: util / automaton · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-057.html](../../bugs/LFV-057.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You convert a code-point range that starts below U+0800 and ends at or above U+1000 into a UTF-8 automaton, as makeAnyChar and regex '.' do, and match it against binary terms that are not valid UTF-8. You expect only valid UTF-8 to match. Instead the overlong sequences E0 80..9F xx are accepted.

## Expected vs actual

- Expected: [U+007F, U+1000] compiles to 3,970 byte strings and rejects `E0 80 80`; `RegexpQuery(".")`, `WildcardQuery("?")` and `AutomatonQuery` [U+0001, U+10FFFF] each count 1 hit.
- Actual: 6,018 strings (2,048 overlong) and `E0 80 80` accepted, so each query counts 2 hits; [U+007F, U+0FFF] is exact (3,969 strings).

## Files

- [src/TestUtf32ToUtf8OverlongVerify.java](src/TestUtf32ToUtf8OverlongVerify.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
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
java -ea -esa -Xmx4g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestUtf32ToUtf8OverlongVerify
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [OverlongUtf8Repro.java](OverlongUtf8Repro.java)
- [TestUTF32ToUTF8OverlongResidual.java](TestUTF32ToUTF8OverlongResidual.java)
- [test-utf32toutf8.diff](test-utf32toutf8.diff)

Formal models and scripts this bug's part of the hunt used: [../../models/study-automaton-verification/](../../models/study-automaton-verification/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
