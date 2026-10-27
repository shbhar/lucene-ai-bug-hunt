# Two antipodal COSINE vectors make the OSQ centroid zero, so flush or merge fails and the writer closes

Bug LFV-047 · Area: codecs / vectors · Priority: medium · Impact: index unusable · Novelty: new

Bug page: [../../bugs/LFV-047.html](../../bugs/LFV-047.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

With the scalar-quantized (OSQ) vectors format and COSINE, you index vectors whose normalized sum is exactly zero, such as {1,0} and {-1,0}, into one segment. You expect flush and merge to work. Instead normalizing the zero centroid throws 'Cannot normalize a zero-length vector', the writer closes tragically with its buffered documents lost, and merges stay blocked.

## Expected vs actual

- Expected: `commit`, `forceMerge(1)` and `forceMergeDeletes` succeed and kNN finds both documents, as with the default vectors format.
- Actual: Each throws `IllegalArgumentException: Cannot normalize a zero-length vector` and closes the `IndexWriter`: 4 of 5 tests fail on main, 3 of 5 on 10.5.2.

## Files

- [src/TestOsqZeroCentroidVerify.java](src/TestOsqZeroCentroidVerify.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- How to read the outcome: this test asserts the current, buggy behaviour: it passes while the bug is present and fails once the bug is fixed.
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
     execute --select-class TestOsqZeroCentroidVerify
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestOsqAntipodalZeroCentroid.java](TestOsqAntipodalZeroCentroid.java)
- [TestOsqAntipodalZeroCentroidColumnar.java](TestOsqAntipodalZeroCentroidColumnar.java)

Formal models and scripts this bug's part of the hunt used: [../../models/study-quantization-smt/](../../models/study-quantization-smt/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
