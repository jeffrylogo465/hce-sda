# State

Mastercard post-GPO stall is BROKEN. Sequence completes: SELECT PPSE,
SELECT AID, GPO, READ RECORD, GENERATE AC, three taps, identical. Terminal
answers "card not supported" after the GENERATE AC.

Base 01f01c6. Branch 8dde832 (reader-genac2-probe, draft PR) adds a second
GEN AC with P1 40 to ReaderActivity. Unverified - no run has landed on it.

# First task

Install 8dde832 on BOTH devices and run it phone-to-phone, ReaderActivity on
the second device. Report p1, cid and sw for AE#1 and AE#2.

# The open question

9F6B in the GPO. Leading suspect for the completion, not established: the
PAN also changed and the profile was pinned in the same build. The method's
own doc comment says 9F6B is not sent and it is. If the probe stalls at GPO,
revert the line and fix the comment - the comment was right.

# Pre-decided branches

- Stalls at GPO -> revert 9F6B, re-run.
- Both AEs 9000, still "card not supported" -> record chain: 8F/90/92/9F46/
  9F47 into syntheticRecord(), non-zero 4th AFL byte, and the AIP CAM bit.
- Decline or online error -> data is acceptable, rejection was policy. Next
  is force-offline on the register.

# Inert, do not mix in

selectInner()'s 9F2A=03 literal, and 0x88 returning 6D00. Both are real bugs,
both change nothing on this path, and mixing them in makes a red gate
ambiguous.

# Environment traps

- origin points at a repo with no ac2-cdol2. Push to `mine`. Rename
  origin -> upstream, pending.
- Fresh install leaves the app stopped; launch it before tapping or the tap
  goes nowhere. This ate a night.
- term_card0_log.txt is only written in onDeactivated and its write path
  swallows exceptions. Logcat is the record now - tap() mirrors to Log.w.
- The on-screen verdict is a stale label. Only the log counts.
- The tap config is MK-AC dummy 0123456789ABCDEFFEDCBA9876543210, CID box
  80, FORCE OFFLINE TC off. All three needed: gpoCidFor() returns TC on
  !haveKey regardless of the flag, and the box overrides the ladder.

# Scope

The register run is the target. Don't re-litigate whether it's in scope.
