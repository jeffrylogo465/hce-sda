# Burned machines

One tap per machine, never retried (see README's Field notes). This logs
every real terminal this app's card has been tapped against, so none of
them gets a second attempt by mistake.

TVR (Terminal Verification Results) is printed on the terminal's own
receipt, not something this app's log captures - record it from the
receipt when one is available; "not captured" means no receipt was kept.

| Machine description | Date | TVR | Verdict | What was learned |
|---|---|---|---|---|
| Not recorded - no terminal/register identity was noted this session | 2026-11-26 | Not captured - that machine printed no receipt | 10 taps: 4 reached READ RECORD (SFI 1 + SFI 2) then link-loss before GENERATE AC; 1 reached SFI 1 only; 5 dropped the field right after reading the PPSE directory, before SELECT AID | Confirmed the dual-mode SFI 2 record now serves the byte-exact `syntheticRecord()` shape (`770E820258C0940808010100100101019000` at GPO) on real hardware, not just under Robolectric. No tap this session reached GENERATE AC - the field drop after both records happens before the cryptogram step on every successful tap, which looks like reader-side timing rather than anything wrong with the bytes served. |
