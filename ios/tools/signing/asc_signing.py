#!/usr/bin/env python3
"""v52 imzalı iOS sürümü için App Store Connect API ile dağıtım sertifikası ve App Store profili hazırlar.

Sabit dağıtım anahtarı (SIGN_DIST_KEY, RSA PEM) kullanılır: Apple'da bu anahtarın açık anahtarına sahip geçerli bir
Apple Distribution sertifikası varsa o kullanılır, yoksa anahtardan CSR üretilip yeni sertifika istenir. Ardından
paket kimliği için bu sertifikayı içeren geçerli bir IOS_APP_STORE profili bulunur ya da oluşturulur.

Kullanım: asc_signing.py <çıkış klasörü>
Ortam: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_PATH (.p8), DIST_KEY_PATH (RSA PEM), BUNDLE_ID
Çıktı: <çıkış>/dist.cer (DER), <çıkış>/profile.mobileprovision, <çıkış>/profile.env (PROFILE_UUID, PROFILE_NAME)
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

import jwt  # PyJWT
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.x509.oid import NameOID

API = "https://api.appstoreconnect.apple.com/v1"
OUT = sys.argv[1]
KEY_ID, ISSUER = os.environ["ASC_KEY_ID"], os.environ["ASC_ISSUER_ID"]
BUNDLE = os.environ.get("BUNDLE_ID", "com.karapp.drawguidance")
PROFILE_NAME = "DrawGuidance App Store (CI)"


def token():
    key = open(os.environ["ASC_KEY_PATH"]).read()
    now = int(time.time())
    return jwt.encode({"iss": ISSUER, "iat": now, "exp": now + 1100, "aud": "appstoreconnect-v1"}, key,
                      algorithm="ES256", headers={"kid": KEY_ID, "typ": "JWT"})


def call(method, path, body=None):
    req = urllib.request.Request(API + path if path.startswith("/") else path, method=method,
                                 data=json.dumps(body).encode() if body else None,
                                 headers={"Authorization": "Bearer " + token(), "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            data = r.read()
            return json.loads(data) if data else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        raise SystemExit(f"App Store Connect {method} {path}: {e.code} {detail[:800]}")


def main():
    os.makedirs(OUT, exist_ok=True)
    priv = serialization.load_pem_private_key(open(os.environ["DIST_KEY_PATH"], "rb").read(), password=None)
    pub = priv.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)

    # 1) Bu anahtara ait geçerli dağıtım sertifikası
    cert_id, cert_der = None, None
    certs = call("GET", "/certificates?filter[certificateType]=DISTRIBUTION,IOS_DISTRIBUTION&limit=200")
    for c in certs.get("data", []):
        der = base64.b64decode(c["attributes"]["certificateContent"])
        cert = x509.load_der_x509_certificate(der)
        if cert.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo) == pub:
            cert_id, cert_der = c["id"], der
            print("Var olan dağıtım sertifikası kullanılıyor:", c["attributes"].get("serialNumber"))
            break
    if cert_id is None:
        csr = (x509.CertificateSigningRequestBuilder()
               .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "DrawGuidance CI"),
                                        x509.NameAttribute(NameOID.COUNTRY_NAME, "TR")]))
               .sign(priv, hashes.SHA256()))
        pem = csr.public_bytes(serialization.Encoding.PEM).decode()
        res = call("POST", "/certificates", {"data": {"type": "certificates", "attributes": {
            "certificateType": "DISTRIBUTION", "csrContent": pem}}})
        cert_id = res["data"]["id"]
        cert_der = base64.b64decode(res["data"]["attributes"]["certificateContent"])
        print("Yeni dağıtım sertifikası oluşturuldu:", res["data"]["attributes"].get("serialNumber"))
    open(os.path.join(OUT, "dist.cer"), "wb").write(cert_der)

    # 2) Paket kimliği
    ids = call("GET", f"/bundleIds?filter[identifier]={BUNDLE}&limit=20")
    bundle = next((b for b in ids.get("data", []) if b["attributes"]["identifier"] == BUNDLE), None)
    if bundle is None:
        raise SystemExit(f"{BUNDLE} paket kimliği Apple hesabında yok")

    # 3) Bu sertifikayı içeren geçerli App Store profili (yoksa eskiyi silip yenisi)
    profiles = call("GET", f"/bundleIds/{bundle['id']}/profiles?limit=200")
    chosen = None
    for p in profiles.get("data", []):
        a = p["attributes"]
        if a.get("profileType") != "IOS_APP_STORE" or a.get("name") != PROFILE_NAME:
            continue
        rel = call("GET", f"/profiles/{p['id']}/certificates?limit=50")
        has_cert = any(c["id"] == cert_id for c in rel.get("data", []))
        if a.get("profileState") == "ACTIVE" and has_cert:
            chosen = p
            break
        call("DELETE", f"/profiles/{p['id']}")
    if chosen is None:
        res = call("POST", "/profiles", {"data": {"type": "profiles",
            "attributes": {"name": PROFILE_NAME, "profileType": "IOS_APP_STORE"},
            "relationships": {"bundleId": {"data": {"type": "bundleIds", "id": bundle["id"]}},
                              "certificates": {"data": [{"type": "certificates", "id": cert_id}]}}}})
        chosen = res["data"]
        print("Yeni App Store profili oluşturuldu")
    else:
        print("Var olan App Store profili kullanılıyor")
    a = chosen["attributes"]
    open(os.path.join(OUT, "profile.mobileprovision"), "wb").write(base64.b64decode(a["profileContent"]))
    with open(os.path.join(OUT, "profile.env"), "w") as f:
        f.write(f"PROFILE_UUID={a['uuid']}\nPROFILE_NAME={a['name']}\n")


if __name__ == "__main__":
    main()
