# Android HCE Emulate a Credit Card

An Android HCE (Host-based Card Emulation) credit card emulator that talks to
**real EMV contactless terminals** - package
`de.androidcrypto.android_hce_emulate_a_creditcard`. It is a fork of
AndroidCrypto's HCE tutorial app, extended to walk a full EMV contactless
protocol exchange end to end against a live reader. It has no issuer master
key and cannot produce a valid ARQC, so the normal, expected outcome of a tap
against a real terminal is a **receipted decline** - this app proves protocol
shape and sequencing, not a working payment.

## The three modes

| Mode | Name | What it's for |
|---|---|---|
| 0 | EMV | Full chip/contactless flow: AIP/AFL, CDOL-driven records, GENERATE AC. What a modern reader expects. |
| 1 | MSD (magstripe) | Legacy magnetic-stripe-equivalent data over contactless. Only answers terminals built before Oct 2019. |
| 2 | Dual | Answers whichever of the two a given reader asks for, routed by SFI after GPO, not decided up front. |

Mastercard mandated EMV-only contactless on new terminals from 18 Oct 2019,
Visa from 19 Oct 2019. Mode 1 only still gets a hearing on unattended kit
(vending, parking, older fuel pumps) installed before that cut-over - that is
the one place it survives in the field.

## What a tap does

A tap is a short APDU exchange. Each response below comes from one of three
places: the active profile's **script** (a canned line matched by prefix),
a **capture** (raw bytes recorded from a real card and replayed, when armed),
or a **builder** (assembled from the typed/loaded card fields).

1. **SELECT PPSE** (`2PAY.SYS.DDF01`) - script hit, a captured PPSE, or
   `buildPpse()` generates one from the active AID.
2. **SELECT AID** - script hit, a captured FCI (`rawfci`), or `select()`
   generates one; an AID none of the three profiles claims gets an honest
   `6A82`, not a fabricated FCI.
3. **GET PROCESSING OPTIONS** - script hit (Mastercard's dual/MSD/EMV shape
   gets its own mode-aware builder here, keyed off the stored mode, not
   replayed verbatim), a captured GPO (`rawgpo`) replayed with the AC/ATC
   patched to the live transaction, or `gpo()` builds one from scratch.
4. **READ RECORD**, once per AFL entry - always from the builder
   (`readRecord()`), driven by the card's own typed/loaded fields and mode.
5. **GENERATE AC** - `genac()` (or Mastercard's own `mastercardGenac()` in
   EMV/dual mode) computes the cryptogram and status per `Emv.decideCid()`.
6. **COMPUTE CRYPTOGRAPHIC CHECKSUM** - MSD/dual only, from `ccc()` or
   Mastercard's own `mastercardMsdCcc()`.

Most real taps stop well short of step 6, and many never get past step 2 -
see Honest limits below for why.

## Honest limits

- No issuer master key (IMK-AC) is configured by default, so the computed
  AC is eight zero bytes - a structurally valid, cryptographically
  meaningless cryptogram. A real key can be typed in for testing, but
  nothing here can derive or know a real issuer's key.
- Offline Data Authentication (ODA/SDA) is claimed in the AIP but not
  backed by real key material: the CA public key index, issuer certificate
  and SDA signature served are placeholders (`8F=FA`, filler cert bytes),
  shaped correctly but unverifiable.
- CVC3 is absent by design, not by oversight: the magstripe-equivalent
  bitmap tags (9F62/9F65) are zeroed, and the checksum command (CCC)
  returns an all-zero CVC3 rather than a computed one.
- No PIN support of any kind - the CVM list never offers one, online or
  offline.
- With no real key, offline TC (Transaction Certificate) is the only path
  that can end in an approval, and even that depends entirely on the
  terminal's own Terminal Action Codes (TAC) deciding not to force online
  or decline given the TVR this app's responses produce.
- ATC (Application Transaction Counter) monotonicity is not checked by
  most issuers in practice, so a replayed or non-incrementing ATC alone
  will often not trip a real decline - this app still advances it
  correctly, but should not be relied on as the only thing standing
  between a tap and a flagged transaction.

## Build and test

```
./gradlew testDebugUnitTest assembleDebug --rerun-tasks
```

40 tasks, and the unit test suite (`app/src/test`) is the gate: a change
without a green run through both tasks is not considered proven, and a
behavior change belongs in the same commit as the test that pins it.

## Field notes

- **One tap per machine, never retry.** A terminal that declines once and
  sees the same card seconds later behaves differently (and more
  suspiciously) than one that sees it once. Treat every physical
  register/terminal as a single, non-repeatable attempt.
- **Keep a burned-machine list.** Once a specific terminal has seen this
  app's card and declined it, assume staff or the acquirer's own fraud
  tooling may flag that terminal or its till - see `BURNED.md`.
- **Reinstalling does not restart the process.** `installDebug` replaces
  the APK but Android's NFC stack keeps the previously-registered
  `HostApduService` bound and running. After an install, explicitly
  `am start` the activity, then confirm with `adb shell pidof
  <package>` - don't assume a fresh install means a fresh process.

## Licence

MIT - see `LICENSE` ("Copyright (c) 2024 Michael Fehr (AndroidCrypto)").
This repo is [jeffrylogo465](https://github.com/jeffrylogo465)'s fork of
[AndroidCrypto](https://github.com/AndroidCrypto)'s (Michael Fehr) original
[Android_HCE_Emulate_A_CreditCard](https://github.com/AndroidCrypto/Android_HCE_Emulate_A_CreditCard),
itself built on these two tutorials:

- [How to use Host-based Card Emulation (HCE) in Android - a beginner tutorial (Java)](https://medium.com/@androidcrypto/how-to-use-host-based-card-emulation-hce-in-android-a-beginner-tutorial-java-32974dd89529)
- [How to emulate a credit card on Android with HCE, in Java](https://medium.com/@androidcrypto/how-to-emulate-a-credit-card-on-android-with-host-based-card-emulation-hce-in-java-0652342da0f1)
- [Talk to your Credit Card (the companion NFC reader app/tutorial)](https://medium.com/@androidcrypto/talk-to-your-credit-card-android-nfc-java-d782ff19fc4a)
