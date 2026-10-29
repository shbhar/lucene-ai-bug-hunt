# The Lucene 10.6.0 release notes (CHANGES.txt in the 10.6.0 RC1 and in main's copy of the 10.6.0 section) say StableTflSimilarity 'also supports k3 query-term frequency saturation', but 10.6.0 has no k3: the 10.6 backport left k3 out and dropped the sentence on review request, and an unrelated later backport put the sentence back (documentation defect, unreleased)

Bug LFV-948 · Area: release notes / lucene/CHANGES.txt 10.6.0 New Features (GITHUB#16243 entry) x search / similarities (StableTflSimilarity) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the Lucene 10.6.0 release notes, whose GITHUB#16243 entry says StableTflSimilarity 'also supports k3 query-term frequency saturation', and write new StableTflSimilarity(1.2f, 0.917f, 7f) against the 10.6.0 jar. You expect it to compile, as it does on main; instead javac finds no (float, float, float) constructor and no getK3, because the 10.x backport (#16322) deliberately left k3 out ('remove k3 which isn't in 10') and, on the feature author's review request, also dropped the k3 sentence, which a later, unrelated backport commit (3a75616c943, the #16300 backport) put back. The sentence ships in the RC1 source release's CHANGES.txt and Changes.html and is also in main's copy of the 10.6.0 section.

## Expected vs actual

- Expected: You expect it to compile, as it does on main; instead javac finds no (float, float, float) constructor and no getK3, because the 10.x backport (#16322) deliberately left k3 out ('remove k3 which isn't in 10') and, on the feature author's review request, also dropped the k3 sentence, which a later, unrelated backport commit (3a75616c943, the #16300 backport) put back.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/similarities/TestStableTflK3AsAdvertised.java](src/org/apache/lucene/search/similarities/TestStableTflK3AsAdvertised.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.similarities.TestStableTflK3AsAdvertised
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
