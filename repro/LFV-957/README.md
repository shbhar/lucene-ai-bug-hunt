# The Lucene 10.6.0 RC1 release notes credit the CheckIndex '.si file could not be read' change to GITHUB#17820, an issue that does not exist, instead of GITHUB#7820, so the generated Changes page links to a 404; the extra digit came in with the stable-branch backports of #16788 (documentation defect, unreleased; main is correct)

Bug LFV-957 · Area: release notes / lucene/CHANGES.txt 10.6.0 Improvements (CheckIndex / CorruptSegmentInfoException entry, GITHUB#7820) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the Lucene 10.6.0 release notes (CHANGES.txt or the generated Changes.html of RC1) and follow the issue link of the entry 'CheckIndex now reports which segment's.si file could not be read'. You expect it to lead to GITHUB#7820, the issue the feature commit, CheckIndex.java and main's copy of the entry all name; instead the entry says GITHUB#17820 and its GitHub issue link (number 17820) returns 404. The extra digit was added when #16788 was backported to branch_10x and branch_10_6 (main's commit of the same PR kept 7820), and it ships in the RC1 source release.

The test shows that the 10.6.0 RC1 `lucene/CHANGES.txt` (branch_10_6 4ae946e5a9b, line 112) credits the CheckIndex '.si file could not be read' entry to `GITHUB#17820`, an issue that does not exist; main's copy of the entry is correct.

## Expected vs actual

Expected `GITHUB#7820` (the issue CheckIndex.java links); actual `GITHUB#17820` (`expected:<GITHUB#[]7820> but was:<GITHUB#[1]7820>`, -ea and -da).

## Files

- [src/org/apache/lucene/index/TestChangesCheckIndexEntryIssue.java](src/org/apache/lucene/index/TestChangesCheckIndexEntryIssue.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- Not runnable from this site as is: this repro also needs extra JVM setup (such as an instrumentation hook) that is not exported here; see the comments at the top of the source.

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
     execute --select-class org.apache.lucene.index.TestChangesCheckIndexEntryIssue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
