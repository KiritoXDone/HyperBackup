#!/usr/bin/env python3
from pathlib import Path
from wsgidav.wsgidav_app import WsgiDAVApp
from cheroot import wsgi

root = Path('work/dav-fixture').resolve()
root.mkdir(parents=True, exist_ok=True)
app = WsgiDAVApp({
    'provider_mapping': {'/': str(root)},
    'simple_dc': {'user_mapping': {'*': {'ci': {'password': 'fixture-only'}}}},
    'http_authenticator': {'accept_basic': True, 'accept_digest': False, 'default_to_digest': False},
    'dir_browser': {'enable': False}, 'verbose': 1,
})
wsgi.Server(('127.0.0.1', 8081), app).start()
