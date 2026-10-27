# SegmentInfos#readLatestCommit(Directory, int) and #readCommit(Directory, String, int) javadocs misdescribe the minimum-version check: readLatestCommit still names the index-created major, and both say 'strictly greater' although the check is inclusive (javadoc only; the code is correct)

Bug LFV-119 · Area: index / SegmentInfos version check (javadoc) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call SegmentInfos.readLatestCommit(dir, m) or readCommit(dir, file, m) and expect what their javadocs say: an IndexFormatTooOldException unless the commit's created major (readLatestCommit) or each segment's minVersion (readCommit) is strictly greater than m. Instead the code (SegmentInfos.java:529) rejects only a segment whose minVersion major is below m and ignores the created major, so a commit at exactly m loads, and readLatestCommit also loads a commit created at m-1 whose segments are all at m. The code is the intended behaviour (CHANGES GITHUB#14607 / GITHUB#15431, 'ignores indexCreatedVersionMajor'); the javadoc text is wrong.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The code (SegmentInfos.java:529) rejects only a segment whose minVersion major is below m and ignores the created major, so a commit at exactly m loads, and readLatestCommit also loads a commit created at m-1 whose segments are all at m.

## Files

- [src/org/apache/lucene/index/TestSegmentInfosMinMajorJavadoc.java](src/org/apache/lucene/index/TestSegmentInfosMinMajorJavadoc.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSegmentInfosMinMajorJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
