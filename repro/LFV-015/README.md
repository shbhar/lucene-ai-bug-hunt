# After rolled-back segment names are reused, reopening an old NRT reader throws a misleading IllegalStateException

Bug LFV-015 · Area: index / IndexWriter NRT · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an NRT reader from a writer that is then rolled back without committing, a later writer reuses the same segment names and commits, and you call openIfChanged(oldReader, commit). You expect a new reader or an AlreadyClosedException, as documented. Instead the LUCENE-5931 guard throws an IllegalStateException that blames the application for removing index files, although Lucene's own IndexFileDeleter deleted them, and its OpenMode.CREATE advice also fails.

## Expected vs actual

- Expected: You expect a new reader or an AlreadyClosedException, as documented.
- Actual: The LUCENE-5931 guard throws an IllegalStateException that blames the application for removing index files, although Lucene's own IndexFileDeleter deleted them, and its OpenMode.CREATE advice also fails.

## Files

- [src/TestRolledBackSegmentNameReuse.java](src/TestRolledBackSegmentNameReuse.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 8 test(s) run, 3 failed (testCandidateScenarioByteBuffers, testCandidateScenarioNIOFS, testCommittedBaseVariant).
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestRolledBackSegmentNameReuse
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
