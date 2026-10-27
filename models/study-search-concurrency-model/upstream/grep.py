import json,sys,re
items=json.load(open(sys.argv[1]))
pats=[re.compile(p,re.I) for p in sys.argv[2:]]
for it in sorted(items,key=lambda x:x['number']):
    text=(it.get('title') or '')+'\n'+(it.get('body') or '')
    if all(p.search(text) for p in pats):
        kind='PR' if 'pull_request' in it else 'ISSUE'
        print(it['number'],kind,it['state'],it['created_at'][:10],it['title'][:110])
