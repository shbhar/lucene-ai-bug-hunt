"""Check that the repro's BLOCKS are the page's code blocks verbatim (after HTML-entity decoding and
removing the page's common indentation), with the right first page line. Usage: check_verbatim.py <page> <test.java>"""
import html, re, sys, textwrap
page = open(sys.argv[1], encoding="utf-8").read().split("\n")
test = open(sys.argv[2], encoding="utf-8").read()
# page blocks
blocks, cur = [], None
for i, raw in enumerate(page, 1):
    s = re.sub(r"^\s*\*\s?", "", raw)
    if cur is None and "<pre" in s: cur = []; continue
    if cur is not None:
        if "</pre>" in s: blocks.append(cur); cur = None; continue
        cur.append((i, html.unescape(s)))
# test blocks: {N, """ ... """}
tb = re.findall(r"\{\s*(\d+),\s*\"\"\"\n(.*?)\n\s*\"\"\"\s*\}", test, re.S)
assert len(tb) == len(blocks), (len(tb), len(blocks))
ok = True
for (first, body), pb in zip(tb, blocks):
    # drop leading blank lines like the page block's first code line
    pl = [t for _, t in pb]
    while pl and not pl[0].strip(): pb = pb[1:]; pl = pl[1:]
    want = textwrap.dedent("\n".join(pl)).split("\n")
    got = textwrap.dedent(body).split("\n")
    same = [w.rstrip() for w in want] == [g.rstrip() for g in got]
    print("block@%s page-first=%d lines=%d verbatim=%s" % (first, pb[0][0], len(want), same and int(first) == pb[0][0]))
    ok &= same and int(first) == pb[0][0]
print("ALL_VERBATIM=" + str(ok))
