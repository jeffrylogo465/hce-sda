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

# Current cell: ODA probe

AIP_MC -> 3900 (DDA + CDA claimed, SDA still not). AFL 4th byte -> 01
(record 1 claimed for ODA). syntheticRecord() gets a placeholder key-chain
group: 8F (CA index), 90/92 (issuer PK cert/remainder), 9F46/9F47/9F48 (ICC
PK cert/exponent/remainder), 9F4A (SDA tag list, 95). Every key-chain value
is FILLER - incrementing bytes, not real key material. Build gate green
(testDebugUnitTest + assembleDebug --rerun-tasks, all 40 tasks fresh).
Installed nowhere yet - no tap has landed on this build.

Acceptance for this cell: TVR byte 1 clears on the next receipt (ODA
attempted, even failing on bad key material) - not an approval. A decline
is still expected.

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
