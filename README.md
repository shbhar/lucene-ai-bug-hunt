# AI-assisted bug hunt in Apache Lucene

> [!NOTE]
> This site is maintained by an always-on AI agent that hunts, verifies and keeps it updated; the agent posts only to this repository. Every interaction with the Lucene project (issues, pull requests, comments, mailing list) is done by a person, per Lucene's AI policy.

A dated snapshot (2026-11-21) of AI-assisted work: findings in [Apache Lucene](https://github.com/apache/lucene). The findings the hunt made and confirmed went through AI-run checks. This is an independent list.

**Want to run a hunt like this in your own open-source project?** Start with the [how-to](https://shbhar.github.io/lucene-ai-bug-hunt/howto.html) (principles, methods and pipeline), and give your AI agent [HOWTO.md](HOWTO.md), the same method written as instructions.

Browse the table: open `index.html` (published with GitHub Pages).

## What is in this snapshot

- 1476 registered findings: 1169 new, for which the automated prior-report search found no earlier report (a keyword search; private reports are not visible), and 307 already known (218 upstream, 89 outside Lucene), where others reported or discussed the same behaviour (tracked, but not counted as new).
- 2 of them are withheld pending security review: they appear in the table and in every count, but only with their id, module, priority tier, novelty (new, known upstream or known outside Lucene) and the line "Details withheld pending security review".
- Fixed upstream: 5 new findings whose fix was merged in Lucene (apache/lucene main) since they were listed; all 5 are also on the 11.0 release branch and 1 on the 10.6 branch; 3 already known findings were fixed in that time too.
- Priority tiers: 7 high, 273 medium, 1196 low, 0 not yet ranked.
- 1177 public finding pages (`bugs/`), 1474 repro directories (`repro/`) and 189 units of formal models and scripts (`models/`).
- The full table as data: `data/bugs.csv` and `data/bugs.json`.

## Share a filtered view

The table keeps its filters, search and sort in the page address, so a link opens the same view; "Link to this view" and "Copy link", beside Reset, give the link to the current view. For example: [the findings new in 10.6](https://shbhar.github.io/lucene-ai-bug-hunt/?new106=1#bugs), [the findings new in 11.0](https://shbhar.github.io/lucene-ai-bug-hunt/?new11=1#bugs), [Medium priority only](https://shbhar.github.io/lucene-ai-bug-hunt/?priority=medium#bugs), or [a search for mmap](https://shbhar.github.io/lucene-ai-bug-hunt/?q=mmap#bugs).

## How the findings were found

The hunt used AI models (Anthropic Claude Opus and OpenAI GPT-5.6) with formal methods (TLA+/TLC model checking, Alloy, Z3/cvc5 SMT solving, Dafny proofs and bounded model checking) alongside fuzzing, model-based, property-based and differential testing, and static analysis. Of the 1168 new findings the hunt made that are shown in full, a formal tool's own output gave the first signal for 44; a semi-formal check (a check of the real jar against exact semantics over a bounded input space, exhaustive or for some harnesses randomly sampled, or a hypothesis formed while modelling that a tool then witnessed) for 764; code reading or other testing for 338; and a testing baseline for 22. Every finding ends in a minimal executable test that fails while the reported behaviour is present (on Lucene main, or on the release branch for a finding only a release branch has); for a finding that needs another kind of run, such as a build, the repro is the commands its repro page gives, whose output shows it.

## Lucene version

The hunt began on Lucene main ea57360912ed (2026-10-24); the newest rerun of the list as a whole was on main 7b8418faa2ff (2026-11-20). On 2026-11-20 and 2026-11-21, the repros of 1476 of the findings listed here were set up for a rerun on main 7b8418faa2ff. 1475 ran: 1457 still reproduced (1394 unchanged, 63 with changed behaviour: LFV-044, LFV-1016, LFV-241, LFV-491, LFV-722, LFV-563, LFV-234, LFV-524, LFV-723, LFV-547, LFV-1116, LFV-247, LFV-1132, LFV-150, LFV-815, LFV-816, LFV-217, LFV-1111, LFV-1106, LFV-617, LFV-1354, LFV-727, LFV-1104, LFV-900, LFV-1365, LFV-777, LFV-595, LFV-969, LFV-997, LFV-550, LFV-510, LFV-180, LFV-880, LFV-881, LFV-511, LFV-825, LFV-820, LFV-893, LFV-964, LFV-994, LFV-983, LFV-995, LFV-546, LFV-655, LFV-154, LFV-1068, LFV-1144, LFV-053, LFV-402, LFV-910, LFV-238, LFV-273, LFV-202, LFV-1222, LFV-198, LFV-228, LFV-509, LFV-174, LFV-1035, LFV-1036, LFV-626, LFV-071, LFV-1270, 62 of them with an adapted repro) and 9 no longer failed because upstream fixed them; 1 (LFV-467) was not run on that commit (its repro page says why and where it last ran); 9 (LFV-811, LFV-1565, LFV-1569, LFV-1505, LFV-1507, LFV-1575, LFV-1527, LFV-1571, LFV-948) passed, as before: the run on main is a control there (the finding is on a release branch only, or needs another Java runtime or processor; their pages say which). 731 of the findings shown in full ship a smaller repro, one minimal test, which replaces their earlier repro files. The repros published now were last run on these commits: 1473 on 7b8418faa2ff, 1 on 5adff78bb168; each repro page says where its repro was run, or that no run is on file.

## Reproduce a finding

Each `repro/<id>/` directory holds the minimal test sources, what they show, expected against actual, and a recipe: JDK 25, a Lucene checkout at the tested commit built with `./gradlew --no-daemon assemble`, and the JUnit Platform console launcher, whose vintage engine runs `LuceneTestCase` tests. A failing test is the finding reproducing; a rerun with the same seed replays the same random choices; for a finding that needs another kind of run, such as a build, the repro is the commands its repro page gives, whose output shows it.

## How to help

- If you know the code: pick a finding, check it, and say what you found in an issue in [this repository](https://github.com/shbhar/lucene-ai-bug-hunt/issues) ("real", "intended", "duplicate of ...", or a better fix).
- Please do not open issues or pull requests in apache/lucene about these findings unless a Lucene committer agrees one should be filed. Following Lucene's AI policy, upstream issues and pull requests are written by a person, in their own words.
- The 2 withheld rows rated security-sensitive (in scope of Lucene's SECURITY.md) go privately to security@lucene.apache.org first.
- If you believe any finding has security implications, report it privately to security@lucene.apache.org rather than in a public issue.

## Layout

| Path | Content |
|---|---|
| `index.html` | the findings table and the method notes |
| `bugs/<id>.html`, `assets/<id>/` | public finding pages and their diagrams |
| `repro/<id>/` | minimal repro sources, README and run recipe |
| `models/<unit>/` | formal models and scripts of the hunt units that worked on a public finding |
| `data/bugs.csv`, `data/bugs.json` | the table as data (public fields only) |
| `HOWTO.md`, `howto.html` | how to run a similar hunt in your project |

## License

Apache License 2.0 (see `LICENSE`). Apache Lucene and Apache are trademarks of The Apache Software Foundation; this project is independent and not endorsed by it. See `NOTICE`.
