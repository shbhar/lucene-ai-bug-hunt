"""Static audit of SPI analysis factories: javadoc example attributes vs parameters the constructor reads.
Usage: python3 -I scan_factories.py <lucene/ dir>"""
import os, re, sys, html
root = sys.argv[1]
consts = {}
res = []
for dp, dn, fn in os.walk(root):
    if '/src/java/' not in dp + '/':
        continue
    for f in fn:
        if not f.endswith('Factory.java'):
            continue
        p = os.path.join(dp, f)
        s = open(p, encoding='utf-8').read()
        m = re.search(r'class\s+(\w+)\s+extends\s+(TokenizerFactory|TokenFilterFactory|CharFilterFactory)', s)
        if not m:
            continue
        cls = m.group(1)
        # string constants
        cmap = dict(re.findall(r'static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"', s))
        read = set()
        for mm in re.finditer(r'(?:get|getInt|getBoolean|getFloat|getChar|getSet|getPattern|require|requireInt|requireBoolean|requireFloat|requireChar|getSnowballWordSet|getWordSet|getLines)\s*\(\s*args\s*,\s*([A-Za-z_][\w.]*|"[^"]*")', s):
            a = mm.group(1)
            read.add(a.strip('"') if a.startswith('"') else cmap.get(a.split('.')[-1], '?' + a))
        for mm in re.finditer(r'args\.(?:remove|get|containsKey)\(\s*([A-Za-z_][\w.]*|"[^"]*")', s):
            a = mm.group(1)
            read.add(a.strip('"') if a.startswith('"') else cmap.get(a.split('.')[-1], '?' + a))
        lenient = 'args.isEmpty()' not in s
        # javadoc example: the element that names this class
        doc = html.unescape(s.split('public class')[0] if 'public class' in s else s)
        docattrs = set()
        for el in re.finditer(r'<(?:filter|tokenizer|charFilter)\s+class="solr\.' + cls + r'"([^>]*)/?>', doc, re.S):
            for k in re.findall(r'(\w+)\s*=\s*"', el.group(1)):
                docattrs.add(k)
        docattrs.discard('class')
        missing = sorted(docattrs - read)
        res.append((cls, p.replace(root, ''), sorted(read), sorted(docattrs), missing, lenient))
for cls, p, read, docattrs, missing, lenient in sorted(res):
    flag = []
    if missing: flag.append('DOC-ATTR-NOT-READ=' + ','.join(missing))
    if lenient: flag.append('NO-UNKNOWN-PARAM-CHECK')
    if flag:
        print(cls, p, '|', ' '.join(flag), '| reads', read)
print('factories scanned:', len(res))
