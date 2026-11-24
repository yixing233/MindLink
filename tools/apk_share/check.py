import json, urllib.request, urllib.error
base = "http://127.0.0.1:8098"
try:
    urllib.request.urlopen(base + "/api/files")
    print("FAIL: unauthenticated access allowed")
except urllib.error.HTTPError as e:
    print("PASS: unauth blocked", e.code)
req = urllib.request.Request(base + "/api/login", data=json.dumps({"password": "xcb157342"}).encode(),
                             headers={"Content-Type": "application/json"})
r = urllib.request.urlopen(req)
cookie = r.headers["Set-Cookie"].split(";")[0]
print("PASS: login", r.status)
bad = urllib.request.Request(base + "/api/login", data=json.dumps({"password": "wrong"}).encode(),
                             headers={"Content-Type": "application/json"})
try:
    urllib.request.urlopen(bad); print("FAIL: wrong password accepted")
except urllib.error.HTTPError as e:
    print("PASS: wrong password rejected", e.code)
req = urllib.request.Request(base + "/api/files", headers={"Cookie": cookie})
d = json.load(urllib.request.urlopen(req))
print("PASS: files", len(d["files"]), "|", d["files"][0]["name"])
req = urllib.request.Request(base + d["files"][0]["url"], headers={"Cookie": cookie, "Range": "bytes=0-15"})
r = urllib.request.urlopen(req)
print("PASS: range download", r.status, r.headers["Content-Range"], len(r.read()))
