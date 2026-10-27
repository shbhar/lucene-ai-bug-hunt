# FeatureField.newSigmoidQuery (and the IntervalQuery sigmoid) return NaN scores when pivot^exp overflows or underflows a double, for pivot and exp values the factories accept

Bug LFV-258 · Area: document / FeatureField sigmoid scoring (FeatureField.SigmoidFunction behind newSigmoidQuery; the same arithmetic is copied in queries / intervals IntervalScoreFunction.SigmoidFunction behind the 4-argument IntervalQuery constructor) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You rank documents with FeatureField.newSigmoidQuery (or IntervalQuery's pivot-and-exponent constructor) and pick a steep exponent with a large or tiny pivot, values the factory accepts because it only requires both to be positive and finite. You expect the documented score weight * S^exp / (S^exp + pivot^exp), a number between 0 and weight that equals weight/2 at S = pivot. Instead SigmoidFunction precomputes pivot^exp as a double, which becomes +Infinity or 0 for such parameters, so scores come out as Inf/Inf or 0/0 = NaN: with pivot 2^20 and exponent 60 every document scores NaN; since 10.2.0 NaN hits rank ahead of correctly scored ones, so the best document can come last, and on 9.x (assertions off) TopDocs even returns placeholder hits with doc id 2147483647.

## Expected vs actual

- Expected: You expect the documented score weight * S^exp / (S^exp + pivot^exp), a number between 0 and weight that equals weight/2 at S = pivot.
- Actual: SigmoidFunction precomputes pivot^exp as a double, which becomes +Infinity or 0 for such parameters, so scores come out as Inf/Inf or 0/0 = NaN: with pivot 2^20 and exponent 60 every document scores NaN; since 10.2.0 NaN hits rank ahead of correctly scored ones, so the best document can come last, and on 9.x (assertions off) TopDocs even returns placeholder hits with doc id 2147483647.

## Files

- [src/org/apache/lucene/document/TestFeatureFieldSigmoidPivotPower.java](src/org/apache/lucene/document/TestFeatureFieldSigmoidPivotPower.java) (JUnit test)
- [src/org/apache/lucene/queries/intervals/TestIntervalQuerySigmoidPivotPower.java](src/org/apache/lucene/queries/intervals/TestIntervalQuerySigmoidPivotPower.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestFeatureFieldSigmoidPivotPower \
       --select-class org.apache.lucene.queries.intervals.TestIntervalQuerySigmoidPivotPower
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/featurefield/](../../models/featurefield/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
