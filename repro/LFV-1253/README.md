# CheckIndex's check that ImpactsEnum.advance agrees with PostingsEnum.advance overflows int on its first step (NO_MORE_DOCS - doc with doc == -1), so for every field whose String.hashCode() is odd (for example "id", "text", "content") the first target is NO_MORE_DOCS and the check compares no document, at every check level (since 8.0.0)

Finding LFV-1253 · Area: index / CheckIndex postings check (impacts vs postings advance walk) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1253.html](../../bugs/LFV-1253.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run CheckIndex.testPostings on a segment whose postings format returns, from ImpactsEnum.advance(target), the document after the right one, for a field named "text" (or any field whose String.hashCode() is odd). You expect the CheckIndexException 'Impacts do not advance to the same document as postings for target ...' that the walk under '// Now check advancing' exists to throw, as CheckIndex does for the same reader when the field is named "body". Instead testPostings reports no error: on the walk's first step docID() is -1, so the step bound NO_MORE_DOCS - doc overflows int to Integer.MIN_VALUE, the first target becomes NO_MORE_DOCS, both enums return NO_MORE_DOCS and the walk ends without comparing any document.

## Expected vs actual

- Expected: `CheckIndex.testPostings` reports "Impacts do not advance to the same document as postings for target 114, postings: 114, impacts: 115", as it does with the fix.
- Actual: `status.error` is null, so `assertNotNull` fails with `java.lang.AssertionError`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestCheckIndexImpactsAdvance.java](src/org/apache/lucene/index/TestCheckIndexImpactsAdvance.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckIndexImpactsAdvance
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCheckIndexImpactsAdvance.java](TestCheckIndexImpactsAdvance.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
