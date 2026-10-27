# tryDeleteDocument / tryUpdateDocValue running while commit flushes returns a sequence number above the commit's, yet its change is in that commit

Bug LFV-471 · Area: index / IndexWriter sequence numbers (tryDeleteDocument, tryUpdateDocValue vs commit) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-471.html](../../bugs/LFV-471.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call tryDeleteDocument or tryUpdateDocValue with an NRT reader while another thread is inside commit, after the commit has fixed its sequence number and before it snapshots its segments. You expect what the commit javadoc promises ('All sequence numbers &lt;= this value will be reflected in the commit, and all others will not'): a change whose returned number is above the commit's is not in that commit. Instead the call returns the commit's number plus one (8 against 7), yet the delete or doc-values update is already in the commit point, while deleteDocuments(Term), deleteDocuments(Query) and updateNumericDocValue(Term) in the same window get 8 too and correctly stay out.

## Expected vs actual

- Expected: A change numbered 8 is not in commit 7: document `a` is still live with v=1 in the commit point, as it is for the term-based calls.
- Actual: Both try calls return 8, the commit returns 7, and commit 7 already has `a` deleted (or v=42); the three controls pass.

## Files

- [src/org/apache/lucene/index/TestTryDeleteDuringCommitSeqNo.java](src/org/apache/lucene/index/TestTryDeleteDuringCommitSeqNo.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestTryDeleteDuringCommitSeqNo
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestTryModifyDocumentCommitSeqNo.java](TestTryModifyDocumentCommitSeqNo.java)
- [TestSeqNoCommitBoundaryControlV3.java](TestSeqNoCommitBoundaryControlV3.java)
- [TestSecurityTryOpCommitRollback.java](TestSecurityTryOpCommitRollback.java)

Formal models and scripts this bug's part of the hunt used: [../../models/dw-flush-control-deletequeue-tla/](../../models/dw-flush-control-deletequeue-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
