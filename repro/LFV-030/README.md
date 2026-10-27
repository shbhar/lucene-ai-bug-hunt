# CompiledAutomaton.floor never terminates on some infinite languages and runs until OutOfMemoryError

Bug LFV-030 · Area: util / automaton · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call CompiledAutomaton.floor on an infinite language in which the strings below the input have no largest element, for example the language b a* with input bb. You expect the floor term or null. Instead the push-down loop never terminates and grows memory until OutOfMemoryError.

## Expected vs actual

- Expected: You expect the floor term or null.
- Actual: The push-down loop never terminates and grows memory until OutOfMemoryError.

## Files

- [src/TestFloorDivergenceCharacterization.java](src/TestFloorDivergenceCharacterization.java) (JUnit test)
- [src/TestVerifyCompiledAutomatonFloor.java](src/TestVerifyCompiledAutomatonFloor.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 6 test(s) run, 2 failed (testBAStarFloorOfBB, testRegExpBAStarFloorOfBB).
- Recorded outcome of run 2 on main a9779ef3c193: 3 test(s) run, 0 failed.
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
java -ea -esa -Xmx256m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.locale=en-US -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestVerifyCompiledAutomatonFloor

# run 2: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.locale=en-US -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestFloorDivergenceCharacterization
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
