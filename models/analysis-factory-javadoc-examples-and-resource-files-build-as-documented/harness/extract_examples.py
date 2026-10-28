# Extract the hand-written <fieldType>/<analyzer> examples from every analysis factory's class javadoc
# (all non-sandbox modules: lucene/**/src/java/**/*Factory.java), both class="..." and name="..." element forms.
# For each example block it also checks XML well-formedness of the unescaped snippet (stdlib parser).
# Output: examples.tsv (file, line, kind, ref-attr, ref, attrs JSON) and wellformed.tsv (file, line, status, detail).
# Usage: python3 -I extract_examples.py <lucene-dir> <out-dir>
import os, re, sys, json, html
import xml.etree.ElementTree as ET

root = sys.argv[1]
out_dir = sys.argv[2]
rows, wf = [], []
for dp, dn, fn in os.walk(root):
    if "/src/java/" not in dp + "/" or "/sandbox/" in dp + "/":
        continue
    for f in sorted(fn):
        if not f.endswith("Factory.java"):
            continue
        p = os.path.join(dp, f)
        src = open(p, encoding="utf-8").read()
        m = re.search(r"/\*\*(.*?)\*/\s*(?:@[^\n]*\n\s*)*public\s+(?:final\s+|abstract\s+)*class\s+" + f[:-5] + r"\b", src, re.S)
        if not m:
            continue
        doc_start_line = src[: m.start(1)].count("\n") + 1
        raw = m.group(1)
        lines = raw.splitlines()
        doc = "\n".join(l.strip()[1:] if l.strip().startswith("*") else l.strip() for l in lines)
        for pre in re.finditer(r"<pre[^>]*>(.*?)</pre>", doc, re.S):
            body = pre.group(1)
            body = re.sub(r"</?code[^>]*>", "", body)
            text = html.unescape(body)
            line = doc_start_line + doc[: pre.start()].count("\n")
            rel = os.path.relpath(p, root)
            if "<" not in text:
                continue
            # well-formedness of the snippet as XML
            try:
                ET.fromstring("<root>" + text + "</root>")
                wf.append((rel, str(line), "well-formed", ""))
            except ET.ParseError as e:
                wf.append((rel, str(line), "MALFORMED", str(e)))
            for el in re.finditer(r"<(tokenizer|filter|charFilter)\b([^<>]*?)/?>", text, re.S):
                attrs = dict(re.findall(r"([\w.\-]+)=\"([^\"]*)\"", el.group(2)))
                if "class" in attrs:
                    ref_attr, ref = "class", attrs.pop("class")
                elif "name" in attrs:
                    ref_attr, ref = "name", attrs.pop("name")
                else:
                    ref_attr, ref = "none", ""
                el_line = line + text[: el.start()].count("\n")
                rows.append((rel, str(el_line), el.group(1), ref_attr, ref, json.dumps(attrs, ensure_ascii=False)))
os.makedirs(out_dir, exist_ok=True)
with open(os.path.join(out_dir, "examples.tsv"), "w", encoding="utf-8") as o:
    for r in rows:
        o.write("\t".join(r) + "\n")
with open(os.path.join(out_dir, "wellformed.tsv"), "w", encoding="utf-8") as o:
    for r in wf:
        o.write("\t".join(r) + "\n")
print("example elements", len(rows), "factory files", len({r[0] for r in rows}), "blocks", len(wf),
      "malformed blocks", sum(1 for r in wf if r[2] == "MALFORMED"))
