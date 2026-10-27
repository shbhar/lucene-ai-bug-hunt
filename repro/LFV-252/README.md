# ControlledRealTimeReopenThread.close never unregisters its refresh listener, so one later refresh of the still-open ReferenceManager overwrites the Long.MAX_VALUE 'closed' generation and an untimed waitForGeneration on the closed instance can block forever

Bug LFV-252 · Area: search / ControlledRealTimeReopenThread (NRT generation tracking: close, the HandleRefresh refresh listener, waitForGeneration) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use a ControlledRealTimeReopenThread with a SearcherManager, close the reopen thread (for example at shutdown), the application refreshes the still-open manager once more, and a request thread that still holds the closed instance calls waitForGeneration(gen) for its own new change. You expect the call to return, because close sets the searching generation to Long.MAX_VALUE 'so any waiting search threads will return', the method's @return says it returns when 'this instance was closed', and a wait with no refresh in between does return at once. Instead the HandleRefresh listener that the constructor registered on the manager is never removed, so that one refresh writes the writer's generation back over Long.MAX_VALUE, and the untimed wait blocks until some later refresh covers gen, which never comes once the manager is closed; only an interrupt releases the thread.

## Expected vs actual

- Expected: You expect the call to return, because close sets the searching generation to Long.MAX_VALUE 'so any waiting search threads will return', the method's @return says it returns when 'this instance was closed', and a wait with no refresh in between does return at once.
- Actual: The HandleRefresh listener that the constructor registered on the manager is never removed, so that one refresh writes the writer's generation back over Long.MAX_VALUE, and the untimed wait blocks until some later refresh covers gen, which never comes once the manager is closed; only an interrupt releases the thread.

## Files

- [src/org/apache/lucene/search/TestWaitForGenerationAfterClose.java](src/org/apache/lucene/search/TestWaitForGenerationAfterClose.java) (JUnit test)
- [src2/org/apache/lucene/search/TestWaitForGenerationAfterClose.java](src2/org/apache/lucene/search/TestWaitForGenerationAfterClose.java) (JUnit test)
- [src3/org/apache/lucene/search/TestWaitForGenerationAfterClose.java](src3/org/apache/lucene/search/TestWaitForGenerationAfterClose.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')
javac -d classes-src3 -cp "$CP" $(find src3 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestWaitForGenerationAfterClose
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestWaitForGenerationAfterClose
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src3:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestWaitForGenerationAfterClose
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/nrt-reference-managers/](../../models/nrt-reference-managers/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
