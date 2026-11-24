# State

GPO-stall question is CLOSED. Two register taps (bf0c4e1, 9F6B reverted)
completed the full sequence: SELECT PPSE, SELECT AID, GPO, READ RECORD,
GENERATE AC, both 9000 through GENERATE AC, deterministic. Receipt:
DECLINED. AID A0000000041010, Acct ************1071, TVR 8000000801, IAD
0110A00009240000010200000000000FF, TSI 2800.

9F6B is not required - at most PAN-dependent, and leaving it out is the
working choice either way (reverted build reached further than the
9F6B-present build ever did on this PAN).

TVR byte 1 80 = ODA not performed, because AIP 1800 claimed no SDA/DDA/CDA.
TVR byte 4 08 = merchant forced online. That combination means this
terminal forces online whenever ODA is not performed, so a CID 40 offline
TC cannot approve here regardless of other variables.

ReaderActivity's GEN AC #2 (P1 40) is CONFIRMED INERT on this path: a
register terminal decides for itself whether to ask for a second GEN AC,
and it never has. Do not chase it further.

# Current cell: ODA probe (SDA, revised against a real card dump)

First pass claimed DDA + CDA with the key chain crammed into the same
record the AFL flagged for ODA (9F4A = 95). Never tapped. Before tapping,
decompiled a QA/test-tool APK pulled off the register phone
(NovoTrack2.dll, ilspycmd) and read its bundled mastercard.json - a real
captured Mastercard M/Chip card's own GPO/record bytes. That changed the
design:

- AIP_MC -> 5880 (SDA claimed, not DDA/CDA - CDA needs a live
  per-transaction signature, 9F4B in GENERATE AC, this profile cannot
  produce; SDA is the one method filler data can structurally approximate).
  Byte 2 is 0x80, NOT 0x00: for Mastercard's own kernel that bit is
  "EMV mode supported" - byte2 clear drops to MSD regardless of byte 1,
  the exact 1A00 config that stalled four taps running before the
  register run that reached GENERATE AC. That run's one 1A80 receipt had
  the 0x80 from the typed "aip" box, never from this constant - the
  default base was never actually fixed, only masked by whatever box was
  set. Served AIP is now 5A80 (byte1 CV+TRM+SDA+CDCVM, byte2 EMV-mode).
- AFL is now TWO entries: 08010101 (SFI 1 rec 1, claimed for ODA - the
  ordinary card-data record) + 10010100 (SFI 2 rec 1, NOT claimed - the
  key-chain record). The real card keeps these separate; the first build
  did not.
- syntheticRecord()'s 9F4A is 82, not 95 (confirmed from the real card).
- keyChainRecord() (SFI 2 rec 1): 8F/9F32/92/90 (CA index, issuer PK
  exponent/remainder/cert) + 93 (Signed Static Application Data). No ICC
  key tags (9F46/9F47/9F48) - those back DDA/CDA, not SDA, and SDA doesn't
  need them. 8F is FA (the real card's own CA index, not an arbitrary
  placeholder - an index missing from the terminal's CA store can make it
  skip SDA/PKI entirely rather than attempt and fail it, a different,
  less useful outcome than the one this cell wants). 90/92/93 are now the
  real card's own lengths too (144/4/112 bytes) - a short cert or
  signature fails as structurally malformed before the kernel ever checks
  the key material, again not the outcome this cell wants. The bytes
  inside 90/92/93 are still just incrementing filler, not real key
  material.
- aflContains() was fixed: it used to tolerate one appended field after
  the first AFL entry (a probe artifact already removed) but did so
  unconditionally, breaking on any genuine second entry. Now a plain
  multi-entry 4-byte walk, verified against the existing AssetCardEngine
  multi-entry-AFL test (separate implementation, unaffected either way).

Build gate green (testDebugUnitTest + assembleDebug --rerun-tasks, all 40
tasks fresh). Installed nowhere yet - no tap has landed on this build.

Acceptance for this cell: TVR byte 1 clears on the next receipt (ODA
attempted, even failing on bad key material) - not an approval. A decline
is still expected.

Reference material pulled this session, kept outside the repo: the QA
APK's assets/mastercard.json (real GPO/AIP/AFL/record bytes with working
SDA+CDA, 9F4A=82, key chain split into non-ODA records) at
~/qa-assets/assets/mastercard.json, and its decompiled source at
~/qa-src/NovoTrack2.decompiled.cs. Worth re-reading before the next ODA
cell (real keys) - it also has the GENERATE AC response shape (9F27/9F36/
9F26/9F10) for a working SDA card, not yet compared against this
project's own.

# Next task

Install this build, repeat the go/no-go (pidof, dumpsys nfc preferred
service + routing), tap once at the same amount as the last comparable
run if the register allows it, pull the receipt. Report TVR byte 1 and the
verdict.

# Pre-decided branches

- TVR byte 1 clears (even if still declined) -> this cell answered what it
  was for. Real DDA/CDA key material (CaKeySet/DumpChain/OdaVerifier
  already exist for the asset-card dump path) is the next cell - wire a
  real CA-signed chain in, not more filler.
- TVR byte 1 still 80 -> the placeholder key-chain group isn't being read
  as ODA data, or this terminal doesn't attempt ODA at all for this
  AID/profile. Check the receipt's other TVR bytes and TSI before
  reaching for another variable - don't add a second change in the same
  build.
- Any other protocol break (stall before GENERATE AC, SW error) -> new
  variable introduced a new problem; isolate which of AIP_MC/AFL/the
  key-chain group caused it before touching the others.

# Inert, do not mix in

selectInner()'s 9F2A=03 literal, and 0x88 returning 6D00. Both are real
bugs, both change nothing on this path, and mixing them in makes a red
gate ambiguous.

ReaderActivity's GEN AC #2 (P1 40) - confirmed inert on a register path,
terminal-driven. Leave as is, do not remove or extend it on this branch.

# Environment traps

- origin points at a repo with no ac2-cdol2. Push to `mine`. Rename
  origin -> upstream, pending.
- Fresh install leaves the app stopped; launch it before tapping or the tap
  goes nowhere. This ate a night.
- term_card0_log.txt is only written in onDeactivated and its write path
  swallows exceptions. Logcat is the record now - tap() mirrors to Log.w.
- The on-screen verdict is a stale label. Only the log/receipt counts.
- The tap config is MK-AC dummy 0123456789ABCDEFFEDCBA9876543210, CID box
  80, FORCE OFFLINE TC off. All three needed: gpoCidFor() returns TC on
  !haveKey regardless of the flag, and the box overrides the ladder.
- The register sets the amount per sale - it is not a controlled variable.
  Don't read anything into an amount difference between taps unless the
  log's own GPO PDOL bytes (9F02) are checked and differ meaningfully.

# Scope

The register run is the target. Don't re-litigate whether it's in scope.
