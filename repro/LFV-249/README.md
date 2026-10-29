# SharedTermsUniformSplit merges silently drop the postings of fields that a source segment stored under another postings format or another shared-terms group (after a per-field postings mapping change, or addIndexes from an index with another mapping), because STUniformSplitTermsWriter.merge reads only the shared-terms block of the first field with terms in each source segment

Bug LFV-249 · Area: codecs / SharedTermsUniformSplit postings format (STUniformSplitTermsWriter.merge under PerFieldPostingsFormat and PerFieldMergeState) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-249.html](../../bugs/LFV-249.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index fields a and b with a per-field codec, a in SharedTermsUniformSplit and b in the default postings format, then open a new writer whose codec maps both a and b to one SharedTermsUniformSplit instance (PerFieldPostingsFormat records the mapping per segment, and merging segments written under different mappings is supported), add a document and force-merge. You expect every term to be found after the merge, as with any other postings format. Instead the merge reads only a's shared-terms block from the old segment, so b's old postings are never copied: docFreq(b:b1) is 0 instead of 1, the document stays in the index but cannot be found through b, and CheckIndex reports the index clean.

The test shows that a SharedTermsUniformSplit merge drops the old postings of a field that an earlier segment wrote under another postings format, after a per-field mapping change puts that field into the shared-terms group.

## Expected vs actual

Expected `docFreq(b:b1)` to be 1 after `forceMerge(1)`; actual 0 (`expected:<1> but was:<0>`), with `-ea` and `-da`; the document stays but cannot be found through `b`.

## Files

- [src/org/apache/lucene/codecs/uniformsplit/sharedterms/TestSTUniformSplitMergeMappingChange.java](src/org/apache/lucene/codecs/uniformsplit/sharedterms/TestSTUniformSplitMergeMappingChange.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.codecs.uniformsplit.sharedterms.TestSTUniformSplitMergeMappingChange
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
