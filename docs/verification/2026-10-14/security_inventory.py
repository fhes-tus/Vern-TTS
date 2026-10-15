"""Read-only source/APK inventory; never emits credential values."""
from pathlib import Path
import hashlib, json, re, xml.etree.ElementTree as ET, zipfile
from urllib.parse import urlsplit

root = Path(__file__).resolve().parents[3]
source = root / "app/src/main"
files = list(source.rglob("*.kt")) + list(source.rglob("*.java"))
hosts, sensitive_hits, risky_calls = {}, [], []
secret_patterns = {
    "google_key_literal": re.compile(r"AIza[0-9A-Za-z_-]{35}"),
    "provider_key_literal": re.compile(r"\bsk-(?:proj-|ant-)?[A-Za-z0-9_-]{30,}"),
    "private_key_header": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
}
call_pattern = re.compile(r"addJavascriptInterface|DexClassLoader|ServerSocket\(|Runtime\.getRuntime\(\)\.exec|\.proceed\(\)|setDefaultHostnameVerifier|setWebContentsDebuggingEnabled")
for path in files:
    text = path.read_text(encoding="utf-8", errors="replace")
    for number, line in enumerate(text.splitlines(), 1):
        for name, pattern in secret_patterns.items():
            if pattern.search(line):
                sensitive_hits.append({"path": str(path.relative_to(root)), "line": number, "kind": name})
        if call_pattern.search(line):
            risky_calls.append({"path": str(path.relative_to(root)), "line": number})
    for url in re.findall(r'https?://[^\s"<>]+', text):
        host = urlsplit(url).hostname
        if host and not any(c in host for c in "${}"):
            hosts.setdefault(host, set()).add(str(path.relative_to(root)))

ns = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(source / "AndroidManifest.xml").getroot()
application = manifest.find("application")
exports = [{"type": node.tag, "name": node.get(ns + "name"), "permission": node.get(ns + "permission"),
            "enabled": node.get(ns + "enabled", "true")}
           for node in application if node.get(ns + "exported") == "true"]
apk = root / "output_apks/Vern-v2.6.2-debug-arm64.apk"
native = []
with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if name.startswith("lib/") and name.endswith(".so"):
            sha = hashlib.sha256()
            with archive.open(name) as binary:
                for block in iter(lambda: binary.read(1024 * 1024), b""):
                    sha.update(block)
            item = {"entry": name, "sha256": sha.hexdigest()}
            source_binary = source / "jniLibs" / Path(name).relative_to("lib")
            stripped_binary = root / "app/build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out" / name
            for label, binary_path in (("source_sha256", source_binary), ("stripped_build_sha256", stripped_binary)):
                if binary_path.is_file():
                    with binary_path.open("rb") as binary:
                        item[label] = hashlib.file_digest(binary, "sha256").hexdigest()
            item["matches_stripped_build"] = item.get("stripped_build_sha256") == item["sha256"]
            native.append(item)
result = {"source_files_scanned": len(files), "literal_secret_pattern_hits_redacted": sensitive_hits,
          "risky_call_pattern_hits": risky_calls, "exported_source_components": exports,
          "hardcoded_url_hosts": {key: sorted(value) for key, value in sorted(hosts.items())},
          "packaged_native_libraries": native,
          "limits": "Pattern inventory is not proof of absence of malware, nor a complete dependency or native-binary audit."}
output = Path(__file__).with_name("security-inventory.json")
output.write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps({"source_files": len(files), "credential_pattern_hits": len(sensitive_hits),
                  "risky_call_hits": len(risky_calls), "url_hosts": len(hosts), "native_libraries": native}))
