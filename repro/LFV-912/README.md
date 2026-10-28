# HardlinkCopyDirectoryWrapper.copyFrom unwraps both directory chains to the innermost FSDirectory and hard-links there, so every FilterDirectory in between is skipped for the copied file: an inner TrackingDirectoryWrapper does not record it, an inner LockValidatingDirectoryWrapper does not check its lock, and a one-sided byte-transforming or namespace-owning layer is bypassed

Bug LFV-912 · Area: misc / store: HardlinkCopyDirectoryWrapper#copyFrom hard-link path (against the core/store Directory#copyFrom and FilterDirectory#copyFrom contracts; reached through core/index IndexWriter.addIndexes(Directory...)) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap an FSDirectory in TrackingDirectoryWrapper (or LockValidatingDirectoryWrapper, or your own FilterDirectory), wrap that in HardlinkCopyDirectoryWrapper, and copy a file in with copyFrom or IndexWriter.addIndexes(Directory...). You expect the copy to pass through the wrapped directory, as createOutput through the same wrapper does: on main Directory#copyFrom says 'Any per-file bookkeeping performed by createOutput must also cover copied files', and FilterDirectory#copyFrom names this class as one that delegates to in.copyFrom. Instead copyFrom unwraps both chains to the innermost FSDirectory and hard-links there, so no layer in between runs: getCreatedFiles stays , the copy succeeds after the inner write lock was released, and a byte-transforming layer on one side reads back the raw bytes (through addIndexes, a committed index that cannot be opened), with no error at copy time.

## Expected vs actual

- Expected: You expect the copy to pass through the wrapped directory, as createOutput through the same wrapper does: on main Directory#copyFrom says 'Any per-file bookkeeping performed by createOutput must also cover copied files', and FilterDirectory#copyFrom names this class as one that delegates to in.copyFrom.
- Actual: copyFrom unwraps both chains to the innermost FSDirectory and hard-links there, so no layer in between runs: getCreatedFiles stays , the copy succeeds after the inner write lock was released, and a byte-transforming layer on one side reads back the raw bytes (through addIndexes, a committed index that cannot be opened), with no error at copy time.

## Files

- [src/org/apache/lucene/misc/store/TestHardlinkCopyBypassesInnerWrappers.java](src/org/apache/lucene/misc/store/TestHardlinkCopyBypassesInnerWrappers.java) (JUnit test)
- [src/org/apache/lucene/store/TestHardlinkCopyBypassesInnerWrappers.java](src/org/apache/lucene/store/TestHardlinkCopyBypassesInnerWrappers.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.store.TestHardlinkCopyBypassesInnerWrappers

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestHardlinkCopyBypassesInnerWrappers
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hardlink-copy-unwrap-bypasses-intermediate-directory-wrappers/](../../models/hardlink-copy-unwrap-bypasses-intermediate-directory-wrappers/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
