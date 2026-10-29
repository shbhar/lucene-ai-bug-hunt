# The Lucene 10.6.0 RC1 release notes credit the CheckIndex '.si file could not be read' change to GITHUB#17820, an issue that does not exist, instead of GITHUB#7820, so the generated Changes page links to a 404; the extra digit came in with the stable-branch backports of #16788 (documentation defect, unreleased; main is correct)

Bug LFV-957 · Area: release notes / lucene/CHANGES.txt 10.6.0 Improvements (CheckIndex / CorruptSegmentInfoException entry, GITHUB#7820) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the Lucene 10.6.0 release notes (CHANGES.txt or the generated Changes.html of RC1) and follow the issue link of the entry 'CheckIndex now reports which segment's.si file could not be read'. You expect it to lead to GITHUB#7820, the issue the feature commit, CheckIndex.java and main's copy of the entry all name; instead the entry says GITHUB#17820 and its GitHub issue link (number 17820) returns 404. The extra digit was added when #16788 was backported to branch_10x and branch_10_6 (main's commit of the same PR kept 7820), and it ships in the RC1 source release.

## Expected vs actual

- Expected: You expect it to lead to GITHUB#7820, the issue the feature commit, CheckIndex.java and main's copy of the entry all name; instead the entry says GITHUB#17820 and its GitHub issue link (number 17820) returns 404.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/ChangesIssueRefCheck.java](src/ChangesIssueRefCheck.java) (main() class)

## Run it

- How to read the outcome: a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" ChangesIssueRefCheck
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
