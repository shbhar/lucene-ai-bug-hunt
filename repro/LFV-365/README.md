# NRTCachingDirectory.createOutput does not throw FileAlreadyExistsException when the name already exists in its other home (RAM cache vs wrapped directory), so the file ends up in both homes: deleteFile then leaves the other copy listed and readable, and close fails and, with assertions disabled, loses the file

Bug LFV-365 · Area: store / NRTCachingDirectory (Directory write-once contract) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a directory in NRTCachingDirectory, write a file with IOContext.DEFAULT (it goes to the wrapped directory), and then call createOutput again for the same name with a small flush context (which goes to the RAM cache), or the other way round. You expect FileAlreadyExistsException, which the Directory contract requires for any existing name. Instead the second create succeeds and the name lives in both homes, so deleteFile removes only the cached copy while the other copy stays listed and readable (in the order above, with the older content), and close fails (an AssertionError with -ea; with -da, on 9.0.0 and later, a FileAlreadyExistsException, after which the file is in neither home).

## Expected vs actual

- Expected: You expect FileAlreadyExistsException, which the Directory contract requires for any existing name.
- Actual: The second create succeeds and the name lives in both homes, so deleteFile removes only the cached copy while the other copy stays listed and readable (in the order above, with the older content), and close fails (an AssertionError with -ea; with -da, on 9.0.0 and later, a FileAlreadyExistsException, after which the file is in neither home).

## Files

- [src/org/apache/lucene/store/TestNRTCachingCreateOutputExisting.java](src/org/apache/lucene/store/TestNRTCachingCreateOutputExisting.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestNRTCachingCreateOutputExisting
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
