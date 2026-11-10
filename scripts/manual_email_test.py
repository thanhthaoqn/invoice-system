"""Live HTTP tests against the local email-test instance, never sends real email.

Run with ai-service/venv/Scripts/python.exe scripts/manual_email_test.py.
Runtime credentials live in .local/email-test/config.json (gitignored).
"""
import base64
import io
import json
from pathlib import Path
import uuid
import zipfile

import requests
import pymupdf

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '.local' / 'email-test'
CONFIG = json.loads((OUT / 'config.json').read_text(encoding='utf-8-sig'))
BASE = 'http://127.0.0.1:8088'
RUN = uuid.uuid4().hex[:10]
session = requests.Session()
response = session.post(BASE + '/api/auth/login', json={
    'username': CONFIG['username'], 'password': CONFIG['password']}, timeout=30)
response.raise_for_status()
session.headers['X-CSRF-Token'] = response.json()['csrfToken']
results = []


def check(name, condition, evidence):
    results.append({'case': name, 'passed': bool(condition), 'evidence': evidence})
    print(('PASS ' if condition else 'FAIL ') + name + ': ' + str(evidence))


def xml(number, buyer=None, seller='0300000001'):
    return f'''<HDon><DLHDon><TTChung><KHMSHDon>1</KHMSHDon>
<KHHDon>C26TAA</KHHDon><SHDon>{RUN}-{number}</SHDon><NLap>2026-11-10</NLap>
</TTChung><NDHDon><NBan><Ten>Manual Test Supplier</Ten><MST>{seller}</MST></NBan>
<NMua><MST>{buyer or CONFIG['taxCode']}</MST></NMua><TToan>
<TgTCThue>100000</TgTCThue><TgTThue>10000</TgTThue><TgTTTBSo>110000</TgTTTBSo>
</TToan></NDHDon></DLHDon></HDon>'''.encode()


def attachment(name, content):
    return {'name': name, 'content': base64.b64encode(content).decode()}


def payload(name, files=(), html=None):
    return {'messageId': f'<{RUN}-{name}@supplier.test>', 'sender': CONFIG['sender'],
            'subject': f'Manual email test {RUN} {name}', 'html': html,
            'attachments': [attachment(n, c) for n, c in files]}


def send(body, secret=None):
    return requests.post(BASE + '/emails/webhook', json=body,
        headers={'X-Inbot-Email-Webhook-Secret': CONFIG['secret'] if secret is None else secret}, timeout=120)


def detail(message):
    result = session.get(BASE + f'/api/intake/sessions/{message["sessionId"]}', timeout=30)
    result.raise_for_status()
    return result.json()['items']


def archive(files):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as z:
        for name, content in files:
            z.writestr(name, content)
    return output.getvalue()


first_body = payload('valid', [('invoice.xml', xml('valid'))])
(OUT / 'sample-email.json').write_text(json.dumps(first_body, indent=2), encoding='utf-8')
r = send(first_body, 'incorrect-secret')
check('Wrong webhook secret', r.status_code == 401, r.status_code)
untrusted = payload('untrusted')
untrusted['sender'] = 'untrusted@example.test'
r = send(untrusted)
check('Sender whitelist', r.status_code == 403, r.status_code)
r = send(first_body)
r.raise_for_status()
first = r.json()
check('Valid XML intake', first['state'] == 'SUCCESS', first)
r = send(first_body)
check('Same Message-ID is claimed once', r.status_code == 200 and r.json()['id'] == first['id']
      and r.json()['sessionId'] == first['sessionId'], r.json())
r = send(payload('duplicate-content', [('renamed.xml', xml('valid'))]))
r.raise_for_status()
doc = detail(r.json())[0]
check('Same XML in different email creates no new invoice', doc['state'] == 'ALREADY_RECEIVED', doc['state'])

for name, data, expected in [
    ('wrong-buyer', xml('wrong', buyer='9999999999'), 'REJECTED_ROUTING'),
    ('outbound', xml('outbound', seller=CONFIG['taxCode']), 'REJECTED_OUTBOUND'),
    ('xxe', b'<!DOCTYPE HDon [<!ENTITY attack SYSTEM "file:///etc/passwd">]><HDon>&attack;</HDon>', 'PARSE_FAILED')]:
    r = send(payload(name, [('invoice.xml', data)]))
    r.raise_for_status()
    doc = detail(r.json())[0]
    check(name, doc['state'] == expected and doc.get('invoiceId') is None, doc['state'])

partial_body = payload('partial', [('good.xml', xml('partial')), ('bad.xml', b'<broken>')])
r = send(partial_body)
r.raise_for_status()
partial = r.json()
check('Mixed good and malformed XML', partial['state'] == 'PARTIAL', [d['state'] for d in detail(partial)])
old_invoice = next(d['invoiceId'] for d in detail(partial) if d['state'] == 'INGESTED')
r = session.post(BASE + f'/api/emails/{partial["id"]}/reprocess', timeout=120)
r.raise_for_status()
retried = r.json()
docs = detail(retried)
check('Reprocess retained original without duplicating successful invoice', retried['state'] == 'PARTIAL'
      and retried['attempts'] == 2 and any(d['state'] == 'ALREADY_RECEIVED' and d['invoiceId'] == old_invoice for d in docs),
      {'state': retried['state'], 'attempts': retried['attempts'], 'documents': [d['state'] for d in docs]})
session.post(BASE + f'/api/emails/{partial["id"]}/reprocess', timeout=120).raise_for_status()
r = session.post(BASE + f'/api/emails/{partial["id"]}/reprocess', timeout=30)
check('Retry limit', r.status_code == 409, r.status_code)

for name, zipped, expected in [
    ('zip-multiple', archive([('a.xml', xml('zip-a')), ('folder/b.xml', xml('zip-b'))]), 'ARCHIVE_PROCESSED'),
    ('zip-slip', archive([('../escape.xml', xml('slip'))]), 'ARCHIVE_FAILED'),
    ('zip-bomb-ratio', archive([('large.txt', b'0' * 2_000_000)]), 'ARCHIVE_FAILED')]:
    r = send(payload(name, [('batch.zip', zipped)]))
    r.raise_for_status()
    docs = detail(r.json())
    check(name, docs[0]['state'] == expected and
          (name != 'zip-multiple' or sum(d['state'] == 'INGESTED' for d in docs) == 2),
          [d['state'] for d in docs])

pdf = pymupdf.open()
page = pdf.new_page()
page.insert_text((50, 60), f'VAT INVOICE\nInvoice number: {RUN}\nDate: 10/11/2026\nSeller: Test Supplier\nTax code: 0300000001\nBuyer tax code: 0100000001\nSubtotal: 100000\nVAT: 10000\nTotal: 110000')
pdf_bytes = pdf.tobytes()
pdf.close()
r = send(payload('xml-pdf', [('invoice.pdf', pdf_bytes), ('invoice.xml', xml('paired'))]))
r.raise_for_status()
paired = r.json()
docs = detail(paired)
check('PDF attaches to XML without OCR', paired['state'] == 'SUCCESS' and any(d['state'] == 'PDF_ATTACHED' for d in docs),
      [d['state'] for d in docs])
pdf_doc = next(d for d in docs if d['state'] == 'PDF_ATTACHED')
r = session.get(BASE + f'/api/intake/items/{pdf_doc["id"]}/file', timeout=30)
check('Retained attachment bytes are unchanged', r.status_code == 200 and r.content == pdf_bytes, len(r.content))
r = send(payload('pdf-only', [('standalone.pdf', pdf_bytes)]))
r.raise_for_status()
pdf_only = r.json()
check('PDF-only goes to review through real AI service', pdf_only['state'] == 'NO_XML'
      and detail(pdf_only)[0]['state'] == 'OCR_REVIEW', [d['state'] for d in detail(pdf_only)])
r = send(payload('portal-only', html='<a href="https://supplier.test/lookup/123">Lookup</a>'))
r.raise_for_status()
check('Unsupported portal URL does not report success', r.json()['state'] == 'FAILED', r.json()['state'])
r = requests.get(BASE + '/api/emails', timeout=30)
check('Email history requires login', r.status_code == 401, r.status_code)
r = session.get(BASE + '/api/emails', timeout=30)
r.raise_for_status()
check('Authenticated email history', any(m['id'] == first['id'] for m in r.json()), len(r.json()))

(OUT / 'results.json').write_text(json.dumps({'run': RUN, 'results': results}, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Passed {sum(r["passed"] for r in results)}/{len(results)}; report: {OUT / "results.json"}')
raise SystemExit(0 if all(r['passed'] for r in results) else 1)
