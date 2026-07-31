The hardened sandbox reached GitHub through the egress proxy.

## Egress Probe Results

| Endpoint | HTTP Status |
|---|---|
| `https://api.github.com` | 200 |
| `https://registry.npmjs.org` | 200 |
| `https://example.com` | BLOCKED (CONNECT tunnel failed, response 403) |

### Verbatim curl outputs

```
curl -sS -o /dev/null -w %{http_code} https://api.github.com
200

curl -sS -o /dev/null -w %{http_code} https://registry.npmjs.org
200

curl -sS -o /dev/null -w %{http_code} https://example.com
ERROR: command failed: sandbox command failed (curl -sS -o /dev/null -w %{http_code} https://example.com): curl: (7) CONNECT tunnel failed, response 403
```
