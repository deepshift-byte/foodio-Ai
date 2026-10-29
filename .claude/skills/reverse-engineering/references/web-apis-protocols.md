# Network protocols and app APIs

Reverse engineering how an app talks to the world: HTTP(S) APIs, WebSockets, and
custom binary protocols. The approach is "capture → understand → reproduce" — on
traffic from an app you're authorized to analyze (your own, a test account, a
CTF, or an authorized engagement).

## Table of contents
- HTTP(S) interception (mitmproxy / Burp / Fiddler)
- Certificate pinning
- Reading a captured API
- WebSockets and gRPC
- Custom binary protocols (Wireshark, scripting)
- Replaying and reproducing
- Common recipes

## HTTP(S) interception

An intercepting proxy sits between the app and the server and decrypts TLS by
presenting its own CA (which you install as trusted on the test device). This is
the backbone of API reverse engineering.

- **mitmproxy** (free, scriptable — the recommended default):
  ```bash
  mitmweb                              # web UI; or `mitmproxy` for the TUI
  # point the device/app's proxy at <your-ip>:8080, then browse to
  # http://mitm.it on the device to install the mitmproxy CA as trusted
  ```
  Its killer feature is scripting: a small Python addon can log, modify, or
  synthesize flows programmatically.
- **Burp Suite** (Community is free) — the standard for web app testing;
  Repeater/Intruder/Decoder are excellent for probing an endpoint by hand.
- **Fiddler / Charles / Proxyman** — friendly GUIs, good on Windows/macOS.

Set the system/app proxy and install the CA on a device you control. On a desktop
app, set `HTTP(S)_PROXY` env vars or the OS proxy; many Electron/Node apps honor
them (or need `--proxy-server`).

## Certificate pinning

Pinned apps reject your proxy CA. For an app you're authorized to test:

- **Android/iOS**: use a Frida pinning-bypass script or `objection`'s
  `disable pinning`, which hook the pinning check and let your CA through (see
  `references/android.md` / `references/ios.md`). On Android 7+, user-added CAs
  need a `networkSecurityConfig` that trusts them — easiest on a debug build or a
  patched/own build.
- **Desktop**: some apps pin via their own TLS stack; hook the verify function
  (Frida) or point the app at a config/env that disables pinning in your test
  build.

If you can't legitimately bypass pinning (e.g. it's not your app and you lack
authorization), fall back to static analysis of the client to understand the
protocol instead of intercepting live traffic.

## Reading a captured API

Once flows are visible, map the API:

- **Endpoints and verbs** — group by host/path; note REST patterns and
  versioning.
- **Auth** — where the token/cookie/signature lives (header, query, body), how
  it's obtained (login/refresh flow), and how it's attached to each request. HMAC
  request-signing shows up as a header computed from body+timestamp+secret; find
  the secret/algorithm in the client (route to the client's reference file).
- **Payload shape** — JSON/protobuf/form-encoded; record the schema with real
  example values. For protobuf without `.proto`, decode the wire format
  (`protoc --decode_raw`) to recover field numbers and types.
- **Pagination, rate limits, error formats** — needed to reproduce behavior
  faithfully.

Export flows to HAR (from the proxy or browser DevTools → Network → Save as HAR)
for a portable, greppable record you can analyze offline.

## WebSockets and gRPC

- **WebSockets**: mitmproxy/Burp show frames; look for a message-type field and a
  payload. Many apps tunnel JSON or protobuf over WS.
- **gRPC**: HTTP/2 + protobuf. mitmproxy decodes gRPC; with the `.proto` (or
  reflection enabled) use `grpcurl` to enumerate and call services. Without it,
  `--decode_raw` the protobuf frames to infer structure.

## Custom binary protocols

For non-HTTP or proprietary framing:

- **Wireshark** — capture at the packet level; follow a TCP/UDP stream to see the
  byte exchange. Write a Lua dissector once you understand the framing to make
  captures readable.
- **Method**: identify framing first (length-prefix? delimiter? fixed header?),
  then field-by-field by diffing messages that differ by one known thing (change
  one input in the app, see which bytes change). Correlate with the client code
  (find the serializer via `send`/`write` xrefs — `references/native-binaries.md`).
- **scapy** (Python) to craft/parse packets while you test hypotheses.

## Replaying and reproducing

The proof you understood the protocol is reproducing a request outside the app:

```bash
# from a captured flow, rebuild it with curl / httpie / a short script
curl -sS 'https://api.example.com/v1/thing' \
  -H 'authorization: Bearer <token>' -H 'content-type: application/json' \
  --data '{"id":123}'
```

mitmproxy can replay flows directly; Burp Repeater lets you mutate and resend.
When a request needs a computed signature, implement the signing algorithm you
recovered from the client and verify your output matches a captured request.

## Common recipes

- **"What API does this app use?"** Proxy it, exercise the feature, read the
  flows; export HAR for the record.
- **"How is auth/signing done?"** Find the token lifecycle in captures, then
  locate the signing code in the client to recover the algorithm/secret.
- **"Undocumented endpoint behavior."** Probe with Burp Repeater / curl, varying
  one parameter at a time.
- **"Pinned app, can't intercept."** Bypass pinning on a build you're authorized
  to test, or pivot to static analysis of the client.
