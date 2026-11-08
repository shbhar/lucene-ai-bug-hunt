# The merge-time postings view (MappedMultiFields) accepts a PostingsEnum built for an earlier merge as the reuse hint, because MappedMultiTermsEnum.postings(reuse, flags) compares only the field name and not the MergeState; the reused MappingMultiPostingsEnum keeps the earlier merge's per-segment doc maps, so a postings format that passes such an enum back gets wrong doc ids ([0, 1, 2, 3, 1] instead of [0, 1, 2, 3, 4]) or an ArrayIndexOutOfBoundsException (every release since 5.0.0)

Finding LFV-1305 · Area: core / index: merge-time postings view (MappedMultiFields, MappingMultiPostingsEnum) that FieldsConsumer.merge hands to FieldsConsumer.write · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1305.html](../../bugs/LFV-1305.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a postings format whose FieldsConsumer.write pulls postings from the Fields that a merge hands it, keep the PostingsEnum it returned, and pass that enum back as the reuse hint in a later merge. You expect the result to list the same documents as a fresh enum, since TermsEnum.postings documents reuse only as a hint ('pass a prior PostingsEnum for possible reuse'). Instead MappedMultiFields accepts the old enum because only the field name matches, resets it against the earlier merge's per-segment doc maps and returns wrong doc ids ([0, 1, 2, 3, 1] instead of [0, 1, 2, 3, 4] in the repro), or throws ArrayIndexOutOfBoundsException when the new merge has more segments; no Lucene codec passes an enum from one merge into another, so only such custom codec code is affected.

## Expected vs actual

- Expected: Merge 2's postings for `f:t` are `[0, 1, 2]`, as with `reuse = null`.
- Actual: `[0, 1, 1]`: `AssertionError: expected:<[0, 1, 2]> but was:<[0, 1, 1]>`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestMergePostingsReuse.java](src/org/apache/lucene/index/TestMergePostingsReuse.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMergePostingsReuse
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMergePostingsReuse.java](TestMergePostingsReuse.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
