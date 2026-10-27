# FilterMergePolicy.getSizeUnit returns BYTES instead of the wrapped policy's unit, so a wrapped LogDocMergePolicy makes compound-file decisions against the byte threshold and forceMerge(1) rewrites an already merged segment every time

Bug LFV-179 · Area: index / merge policies (FilterMergePolicy size unit, compound-file threshold) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a LogDocMergePolicy in a FilterMergePolicy (or in SoftDeletesRetentionMergePolicy, UpgradeIndexMergePolicy or OneMergeWrappingMergePolicy) and expect the wrapper to make the same compound-file choices as the policy it wraps, as it did in 10.x. Instead, on main FilterMergePolicy.size still returns the wrapped policy's document count while getSizeUnit says BYTES, so the compound-file check compares a document count with the 64 MiB byte threshold: forceMerge(1) of 70,000 small documents writes a compound segment where the plain policy writes a non-compound one, and every later forceMerge(1) rewrites that already merged segment again (3 of 3 calls).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: On main FilterMergePolicy.size still returns the wrapped policy's document count while getSizeUnit says BYTES, so the compound-file check compares a document count with the 64 MiB byte threshold: forceMerge(1) of 70,000 small documents writes a compound segment where the plain policy writes a non-compound one, and every later forceMerge(1) rewrites that already merged segment again (3 of 3 calls).

## Files

- [src/org/apache/lucene/index/TestFilterMergePolicySizeUnit.java](src/org/apache/lucene/index/TestFilterMergePolicySizeUnit.java) (JUnit test)
- [src/org/apache/lucene/index/TestWrappedLogDocMergePolicyCompoundFile.java](src/org/apache/lucene/index/TestWrappedLogDocMergePolicyCompoundFile.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestFilterMergePolicySizeUnit

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestWrappedLogDocMergePolicyCompoundFile
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
