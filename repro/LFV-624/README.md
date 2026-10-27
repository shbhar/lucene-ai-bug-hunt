# GetTermInfo.getTermInfo opens a DirectoryReader on the caller's Directory and never closes it, so every call leaks that reader's open index files (file descriptors or memory mappings)

Bug LFV-624 · Area: misc / index tools (GetTermInfo command-line helper) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call GetTermInfo.getTermInfo(dir, term) from Java to print a term's document frequency and total term frequency, and expect it to release the index files it opened, as its sibling HighFreqTerms.main does for its reader. Instead it opens a DirectoryReader, prints the two numbers and returns without closing the reader, which the caller never sees and so cannot close. Every call leaves that reader's files open, and closing the Directory does not release them: Lucene's own MockDirectoryWrapper then refuses to close ('cannot close: there are still 1 open files: {_0.cfs=1}'), and 20 calls left 40 descriptors (NIOFS) or 40 mappings (mmap, still there after System.gc) where a caller-closed reader leaves 0.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It opens a DirectoryReader, prints the two numbers and returns without closing the reader, which the caller never sees and so cannot close.

## Files

- [src/org/apache/lucene/misc/TestGetTermInfoClosesReader.java](src/org/apache/lucene/misc/TestGetTermInfoClosesReader.java) (JUnit test)

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
     execute --select-class org.apache.lucene.misc.TestGetTermInfoClosesReader
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
