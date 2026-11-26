package de.androidcrypto.android_hce_emulate_a_creditcard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.junit.runner.RunWith;

/**
 * LoadedCard merged into the per-slot EDIT screen - a track 2 or a block
 * typed into TRACK 2 RAW HEX (etTrack2) becomes the selected card record,
 * the same fields and tile every hand-typed card uses. See
 * MainActivity.applyCardBlock()'s and LoadedCard's class docs; this
 * replaces what used to be a separate "Load card" dialog/store.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityCardLoadTest {

    private MainActivity activity;
    private EditText etTrack2, etPan, etExpiry, etService, etDiscretionary,
            etAid, etAip, etAfl, etCid, etAtc, etMsd;
    private View rowMsdMode;
    private TextView tvMsdModeName, txtMsdLabel;

    @Before
    public void setUp() {
        Prefs.reset(RuntimeEnvironment.getApplication());
        activity = Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
        shadowOf(Looper.getMainLooper()).idle();

        etTrack2 = activity.findViewById(R.id.etTrack2);
        etPan = activity.findViewById(R.id.etPan);
        etExpiry = activity.findViewById(R.id.etExpiry);
        etService = activity.findViewById(R.id.etService);
        etDiscretionary = activity.findViewById(R.id.etDiscretionary);
        etAid = activity.findViewById(R.id.etAid);
        etAip = activity.findViewById(R.id.etAip);
        etAfl = activity.findViewById(R.id.etAfl);
        etCid = activity.findViewById(R.id.etCid);
        etAtc = activity.findViewById(R.id.etAtc);
        etMsd = activity.findViewById(R.id.etMsd);
        rowMsdMode = activity.findViewById(R.id.rowMsdMode);
        tvMsdModeName = activity.findViewById(R.id.tvMsdModeName);
        txtMsdLabel = activity.findViewById(R.id.txtMsdLabel);
    }

    /**
     * MainActivity now debounces the track2/PAN filler 250ms (see
     * FILLER_TOKEN) - idle() alone only runs what is already due at the
     * current virtual time, so it never fires a postDelayed(..., 250).
     * idleFor() advances the clock past the debounce window first.
     */
    private void type(EditText field, String text) {
        field.setText(text);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
    }

    /** The task's own test: pasting a track 2 into the editor fills PAN/expiry/SC/discretionary. */
    @Test
    public void bareTrack2_fillsPanExpiryServiceCodeAndDiscretionary() {
        type(etTrack2, "5555555555554444=29122015678");

        assertEquals("5555555555554444", etPan.getText().toString());
        assertEquals("2912", etExpiry.getText().toString());
        assertEquals("201", etService.getText().toString());
        assertEquals("5678", etDiscretionary.getText().toString());
    }

    /**
     * A block's own AID/AIP/AFL/CID/ATC win over applyProfile()'s hand-typed
     * network defaults (Mastercard's own default AIP is 1880, not this
     * block's 5800) - "1880/auto is the hand-typed default, not the card's."
     */
    @Test
    public void block_aidAipAflCidAtcComeFromTheCardNotTheDefaults() {
        String block = "5A=5555555555554444\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=08010100\n"
                + "9F27=80\n"
                + "9F36=0005\n";
        type(etTrack2, block);

        assertEquals("5555555555554444", etPan.getText().toString());
        assertEquals("2912", etExpiry.getText().toString());
        assertEquals("A0000000049999", etAid.getText().toString().toUpperCase());
        assertEquals("5800", etAip.getText().toString().toUpperCase());
        assertEquals("08010100", etAfl.getText().toString().toUpperCase());
        assertEquals("80", etCid.getText().toString().toUpperCase());
        assertEquals("0005", etAtc.getText().toString().toUpperCase());

        // No chain, no ICC key in this block - the tile must say so.
        CardAdapter.CardModel c = activity.cardAt(activity.currentSlot());
        assertTrue("certWarning must name the missing chain: " + c.certWarning,
                c.certWarning.contains("9F46") && c.certWarning.contains("ICCKEY"));
    }

    /**
     * The other half of the guard the test above does not touch: once the
     * user has hand-edited etAfl, a SECOND card block (same PAN/network,
     * different 94) must not clobber that typed value. Only applyCardBlock()
     * reads aflDirty/aipDirty (MainActivity.java ~1178) - this is the one
     * test that actually exercises the guard, not just its absence.
     */
    @Test
    public void typedAflSurvives_aSecondCardBlockOnTheSamePan() {
        String block1 = "5A=5555555555554444\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=08010100\n"
                + "9F27=80\n"
                + "9F36=0005\n";
        type(etTrack2, block1);
        assertEquals("08010100", etAfl.getText().toString().toUpperCase());

        type(etAfl, "DEADBEEF");

        String block2 = "5A=5555555555554444\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=10020200\n"
                + "9F27=80\n"
                + "9F36=0006\n";
        type(etTrack2, block2);

        assertEquals("the user's typed AFL must survive the second block",
                "DEADBEEF", etAfl.getText().toString().toUpperCase());
    }

    /**
     * A card block with a DIFFERENT PAN is a different card - the typed AFL
     * must NOT survive this time, unlike the same-PAN case above. The PAN
     * compare runs before etPan is overwritten (MainActivity.java, right
     * before applyCardBlock()'s own binding=true block).
     */
    @Test
    public void typedAflIsResetByACardBlockOnADifferentPan() {
        String block1 = "5A=5555555555554444\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=08010100\n"
                + "9F27=80\n"
                + "9F36=0005\n";
        type(etTrack2, block1);
        assertEquals("08010100", etAfl.getText().toString().toUpperCase());

        type(etAfl, "DEADBEEF");

        String block2 = "5A=5555555555557777\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=10020200\n"
                + "9F27=80\n"
                + "9F36=0006\n";
        type(etTrack2, block2);

        assertEquals("a different PAN's own AFL must win - the old typed value is stale",
                "10020200", etAfl.getText().toString().toUpperCase());
    }

    /**
     * applyDump()'s bare-track2 path (MainActivity.java ~1075) now skips its
     * own etAfl clear entirely when aflDirty is already true, instead of
     * clearing the box visually and only guarding the flag. Same PAN here,
     * so the flag survives (same PAN-change test applyCardBlock() uses) and
     * the typed value must still be sitting in the box afterward, untouched
     * by either the dump or a follow-up card block on that same PAN.
     */
    @Test
    public void aflDirtySurvivesABareTrack2OnTheSamePan() {
        String block = "5A=5555555555554444\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=08010100\n"
                + "9F27=80\n"
                + "9F36=0005\n";
        type(etTrack2, block);

        type(etAfl, "DEADBEEF");
        assertEquals("DEADBEEF", etAfl.getText().toString().toUpperCase());

        type(etTrack2, "5555555555554444=29122015678");
        assertEquals("aflDirty survived the same-PAN bare track2 - the clear must be skipped",
                "DEADBEEF", etAfl.getText().toString().toUpperCase());

        String block2 = "5A=5555555555554444\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=10020200\n"
                + "9F27=80\n"
                + "9F36=0006\n";
        type(etTrack2, block2);

        assertEquals("aflDirty must have survived the same-PAN bare track2 too - "
                        + "the typed value stays, not the new block's own 94",
                "DEADBEEF", etAfl.getText().toString().toUpperCase());
    }

    /**
     * The other half: a bare track2 on a DIFFERENT PAN resets aflDirty (same
     * PAN-change test as above, this time actually tripping it), so a
     * follow-up card block on THAT new PAN is free to fill its own 94 again.
     */
    @Test
    public void aBareTrack2OnADifferentPanResetsAflDirty() {
        String block = "5A=5555555555554444\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=08010100\n"
                + "9F27=80\n"
                + "9F36=0005\n";
        type(etTrack2, block);

        type(etAfl, "DEADBEEF");
        assertEquals("DEADBEEF", etAfl.getText().toString().toUpperCase());

        type(etTrack2, "5555555555557777=29122015678");
        assertEquals("", etAfl.getText().toString());

        String block2 = "5A=5555555555557777\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=10020200\n"
                + "9F27=80\n"
                + "9F36=0006\n";
        type(etTrack2, block2);

        assertEquals("aflDirty must have been reset by the different-PAN bare track2",
                "10020200", etAfl.getText().toString().toUpperCase());
    }

    /** The task's own test: the tile shows the card after save. */
    @Test
    public void tileShowsTheCardAfterSave() {
        String block = "5A=5555555555554444\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=08010100\n"
                + "9F27=80\n"
                + "9F36=0005\n";
        type(etTrack2, block);

        activity.findViewById(R.id.btnSave).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        CardAdapter.CardModel c = activity.cardAt(activity.currentSlot());
        assertEquals("5555555555554444", c.pan);
        assertEquals("A0000000049999", c.aid.toUpperCase());
        assertTrue(c.certWarning.contains("9F46"));
    }

    /** The task's own test: taps serve it. */
    @Test
    public void tapsServeTheSavedCard() {
        String block = "5A=5555555555554444\n"
                + "5F24=291231\n"
                + "84=A0000000049999\n"
                + "82=5800\n"
                + "94=08010100\n"
                + "9F27=80\n"
                + "9F36=0005\n";
        type(etTrack2, block);
        activity.findViewById(R.id.btnSave).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        ShadowApduService svc = Robolectric.buildService(ShadowApduService.class).create().get();

        String ppse = Hex.toHex(svc.processCommandApdu(
                Hex.hex("00A404000E325041592E5359532E4444463031"), null));
        assertTrue("PPSE must list the saved AID: " + ppse, ppse.contains("4F07A0000000049999"));
        assertTrue(ppse.endsWith("9000"));

        String fci = Hex.toHex(svc.processCommandApdu(Hex.hex("00A4040007A0000000049999"), null));
        assertFalse("SELECT must not 6A82 the saved AID: " + fci, fci.endsWith("6A82"));
        assertTrue(fci.endsWith("9000"));

        String gpo = Hex.toHex(svc.processCommandApdu(Hex.hex("80A8000002830000"), null));
        assertFalse("GPO must not refuse: " + gpo, gpo.endsWith("6985"));
        assertTrue(gpo.endsWith("9000"));
        // The saved AIP was 5800 - SDA (0x40) set. This engine has no cert
        // chain and no CA key (that is AssetCardEngine/DumpChain's job, a
        // separate engine this one never touches), so F01's fix clears the
        // ODA bits here same as the captured-replay branch already did:
        // 0x58 & ~0x65 = 0x18, then F02 ORs in Emv.AIP_CDCVM (0x02) -> 0x1A.
        // Still distinct from the profile default 1880 in both bytes, so
        // this still proves the saved AIP (minus the claims this engine
        // cannot back), not a fallback to the default.
        assertTrue("GPO must answer with the saved AIP minus its ODA bits, not the profile default 1880: " + gpo,
                gpo.contains("82021A00"));
    }

    /**
     * FIX: onSave() called syncFieldsFromTrack2() unconditionally, which
     * reads expiry/service/discretionary back OUT of the track 2 box - and
     * the track 2 box still held whatever was last pasted into it (here,
     * discretionary 14129745), not the hand edit just typed directly into
     * etDiscretionary. Only service had a capture-before/reassert-after
     * fix; discretionary (and expiry) had the identical, unfixed bug - a
     * typed discretionary value reverted to the old track2 tail the
     * moment Save ran, before collect() ever persisted anything.
     *
     * Reproduces exactly: paste a track2 with a real discretionary tail,
     * hand-edit the box to 13 zeros, Save, confirm the edit survived in
     * memory, then rebuild the Activity from the same (unreset) prefs -
     * simulating closing and reopening - and confirm the RELOAD shows the
     * edit too, not the old track2 tail. bindCard() itself was never the
     * problem (it always reads live prefs); onSave() was overwriting the
     * box before the save ever reached prefs.
     */
    @Test
    public void onSave_discretionaryEditSurvives_notRevertedByStaleTrack2() {
        type(etTrack2, "5555555555554444=291220114129745");
        assertEquals("14129745", etDiscretionary.getText().toString());

        type(etDiscretionary, "0000000000000");

        activity.findViewById(R.id.btnSave).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("the saved edit must not be reverted by syncFieldsFromTrack2()'s stale track2 read",
                "0000000000000", etDiscretionary.getText().toString());

        MainActivity reopened =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
        shadowOf(Looper.getMainLooper()).idle();
        EditText reopenedDisc = reopened.findViewById(R.id.etDiscretionary);
        assertEquals("reopening must reload what was actually saved, not the old track2 tail",
                "0000000000000", reopenedDisc.getText().toString());
    }

    /**
     * FIX: applyProfile()'s known-table block (KNOWN[0], PAN
     * 5287250568943496) used to write expiry/discretionary/track2
     * unconditionally every time it ran for this PAN - and before this
     * commit, onSave() called maybeProfile() -> applyProfile() on every
     * save. A discretionary set away from the known value (14129745)
     * never had a chance to survive a save for this specific PAN.
     *
     * Covers the save itself, a fresh-Activity reopen (same prefs), a
     * same-slot rebind, and switching to a second card and back - all on
     * the one PAN this repo's own KNOWN table makes the bug concrete and
     * reproducible with, not a PAN picked to avoid it.
     */
    @Test
    public void knownPanDiscretionaryEdit_survivesSaveAndEveryReloadPath() throws Exception {
        type(etPan, "5287250568943496");
        assertEquals("14129745", etDiscretionary.getText().toString());

        type(etDiscretionary, "0000000000000");
        activity.findViewById(R.id.btnSave).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("immediately after Save",
                "0000000000000", etDiscretionary.getText().toString());

        MainActivity reopened =
                Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("after reopening (fresh Activity instance, same prefs)",
                "0000000000000",
                ((EditText) reopened.findViewById(R.id.etDiscretionary)).getText().toString());

        java.lang.reflect.Method bindCard =
                MainActivity.class.getDeclaredMethod("bindCard", int.class);
        bindCard.setAccessible(true);
        bindCard.invoke(activity, 0);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("after re-binding the same slot",
                "0000000000000", etDiscretionary.getText().toString());

        activity.findViewById(R.id.btnAdd).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        bindCard.invoke(activity, 0);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("after adding a second card and switching back",
                "0000000000000", etDiscretionary.getText().toString());
    }

    /**
     * FIX: onSave()'s own reassert block gated discretionary on the SAME
     * "track2 is long enough for a full exp+svc tail" condition it used
     * for exp/svc (sep > 0 && t2.length() >= sep + 8). But
     * syncFieldsFromTrack2() does not apply that threshold to
     * discretionary - "disc = tail.length() > 7 ? ... : \"\"" always
     * computes something and "if (!disc.equals(t(etDiscretionary)))
     * etDiscretionary.setText(disc)" always applies it, tail length
     * irrelevant. A short/partial track2 (a 'D' with only a couple of
     * tail characters - never pasted a full dump) wiped discretionary to
     * "" and the length-gated reassert never fired to undo it. exp/svc
     * do not share this exposure - sync's own guard for them
     * (!exp.isEmpty()) already skips a too-short tail.
     */
    @Test
    public void discretionaryEdit_survivesSave_evenWithAShortMalformedTrack2() {
        type(etTrack2, "4111111111111111D29");
        type(etDiscretionary, "0000000000000");

        activity.findViewById(R.id.btnSave).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("a 'D' with only 2 tail characters must not wipe discretionary to empty",
                "0000000000000", etDiscretionary.getText().toString());
    }

    /**
     * FIX: armMsdCard() used to seed etAtc straight from the DD's own
     * embedded ATC, unconditionally - a re-arm (paste/pick/seed) running
     * after a real tap had already advanced the service's committed
     * counter past that DD value rewound it right back, undoing a live
     * advance with no guard at all (the profile-picker path re-arms this
     * way routinely). Floored at atccommitted + 1, same Math.max
     * reasoning nextAtc() already applies to its own "atcfloor" box.
     *
     * card0_atccommitted = 9800 (a prior real tap already served that
     * ATC); the pasted dump's own discretionary embeds CSC 1412 + ATC
     * 9746, which unguarded would seed the box to 9745 - below what the
     * service already committed. Floored, it seeds to 9800 instead
     * (atcVal = max(0x9746, 0x9800+1) = 0x9801, stored as atcVal-1).
     */
    @Test
    public void armMsdCard_atcSeed_isFlooredAtAlreadyCommittedValue() {
        Prefs.get(RuntimeEnvironment.getApplication()).edit()
                .putString("card0_atccommitted", "9800").commit();

        type(etTrack2, "5555555555554444=291220114129746");

        assertEquals("floored seed must not fall below what the service already committed",
                "9800", etAtc.getText().toString());
    }

    /**
     * "A card that answers whatever the terminal does, with no mode or
     * CID box to set" - an UNPINNED dump paste overrides applyProfile()'s
     * own msdDefault(net) with dual mode (2) and clears the CID box, so
     * mastercardGenac()'s P1-driven ladder decides instead of a typed-in
     * override. See MainActivity.applyDump()'s own doc for this block.
     *
     * Asserts the PERSISTED "card0_msd" pref, not just etMsd's own text -
     * a real tap proved the two can disagree: collect() used to collapse
     * the box's "2" to "0" on every call (no case for dual mode at all),
     * and applyDump()'s own dual-mode block never called collect() itself
     * either, so the pref stayed whatever armMsdCard()'s EARLIER collect()
     * had already written (msdDefault(net), i.e. "0") regardless of what
     * the box went on to show. The service reads the pref, not the box -
     * a real terminal ran mode 0 while the UI said dual.
     */
    @Test
    public void applyDump_unpinnedCard_setsDualModeAndClearsCid() {
        type(etTrack2, "5555555555554444=29122015678");

        assertEquals("2", etMsd.getText().toString());
        assertEquals("", etCid.getText().toString());
        assertEquals("the service reads this pref, not the box",
                "2", Prefs.get(RuntimeEnvironment.getApplication())
                        .getString("card0_msd", "<none>"));
    }

    /**
     * The other half: a PINNED card keeps whatever MSD/CID the user
     * already chose - a dump paste must not clobber a hand-set value
     * the way applyProfile()'s own shape-guarded fields already don't.
     */
    @Test
    public void applyDump_pinnedCard_keepsItsOwnMsdAndCid() {
        Prefs.get(RuntimeEnvironment.getApplication()).edit()
                .putBoolean("card0_profilepin", true).commit();
        type(etMsd, "1");
        type(etCid, "80");

        type(etTrack2, "5555555555554444=29122015678");

        assertEquals("1", etMsd.getText().toString());
        assertEquals("80", etCid.getText().toString());
    }

    /**
     * Root cause of "armMsdCard() is not reaching etAtc.setText": it
     * WAS reaching it (box correctly went to 9745 - Math.max(0x9746,
     * 0x9744+1) = 0x9746, served ATC minus one), but left "atccommitted"
     * at the stale 9744 the arm had just floored against. onResume()
     * reads that stale value, finds it differs from the box, and
     * rewrites etAtc back to 9744 - see onResume()'s own doc for why it
     * does that in general (the service's commit must survive onPause()'s
     * re-stamp). From the user's side this looks exactly like the arm
     * never took effect. Fixed by clearing "atccommitted" in the same
     * block that writes etAtc, so the next resume has nothing stale to
     * revert to.
     */
    @Test
    public void armMsdCard_clearsAtccommitted_soAResumeDoesNotRewindTheSeed() {
        Prefs.get(RuntimeEnvironment.getApplication()).edit()
                .putString("card0_atccommitted", "9744").commit();

        type(etTrack2, "5555555555554444=291220114129746");

        assertEquals("9745", etAtc.getText().toString());

        activity.onPause();
        shadowOf(Looper.getMainLooper()).idle();
        activity.onResume();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("seed must survive a resume - atccommitted must not still be 9744",
                "9745", etAtc.getText().toString());
    }

    /**
     * The direct save path, not just applyDump()'s own dual-mode-default
     * block: type MSD 2 into the box by hand, Save, and confirm BOTH the
     * persisted pref and the SERVICE's own resolved mode are dual - not
     * just etMsd's text, which collect()'s bug left disagreeing with the
     * pref (see the previous commit). 0 and 1 go through the exact same
     * collect() write and must stay exactly what they always were.
     */
    @Test
    public void collect_msdBoxPreserves0_1_2_inThePrefAndTheResolvedMode() {
        type(etPan, "5555555555554444");

        type(etMsd, "2");
        activity.findViewById(R.id.btnSave).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("2", Prefs.get(RuntimeEnvironment.getApplication())
                .getString("card0_msd", "<none>"));
        assertEquals(2, activity.cardAt(activity.currentSlot()).msd);
        {
            ShadowApduService svc = Robolectric.buildService(ShadowApduService.class).create().get();
            svc.processCommandApdu(Hex.hex("00A404000E325041592E5359532E4444463031"), null);
            svc.processCommandApdu(Hex.hex("00A4040007A0000000041010"), null);
            String gpo = Hex.toHex(svc.processCommandApdu(Hex.hex("80A8000002830000"), null));
            assertTrue("resolved mode must be dual - the two-entry AFL: " + gpo,
                    gpo.contains("0801010010010101"));
        }

        type(etMsd, "1");
        activity.findViewById(R.id.btnSave).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("1", Prefs.get(RuntimeEnvironment.getApplication())
                .getString("card0_msd", "<none>"));
        assertEquals(1, activity.cardAt(activity.currentSlot()).msd);
        {
            ShadowApduService svc = Robolectric.buildService(ShadowApduService.class).create().get();
            svc.processCommandApdu(Hex.hex("00A404000E325041592E5359532E4444463031"), null);
            svc.processCommandApdu(Hex.hex("00A4040007A0000000041010"), null);
            String gpo = Hex.toHex(svc.processCommandApdu(Hex.hex("80A8000002830000"), null));
            assertTrue("resolved mode must be single-mode MSD - AIP 5840, one-entry AFL: " + gpo,
                    gpo.startsWith("770A82025840940408010100"));
        }

        type(etMsd, "0");
        activity.findViewById(R.id.btnSave).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("0", Prefs.get(RuntimeEnvironment.getApplication())
                .getString("card0_msd", "<none>"));
        assertEquals(0, activity.cardAt(activity.currentSlot()).msd);
        {
            ShadowApduService svc = Robolectric.buildService(ShadowApduService.class).create().get();
            svc.processCommandApdu(Hex.hex("00A404000E325041592E5359532E4444463031"), null);
            svc.processCommandApdu(Hex.hex("00A4040007A0000000041010"), null);
            String gpo = Hex.toHex(svc.processCommandApdu(Hex.hex("80A8000002830000"), null));
            assertFalse("resolved mode must NOT be dual or single-mode MSD: " + gpo,
                    gpo.contains("0801010010010101") || gpo.startsWith("770A82025840"));
            assertTrue(gpo.endsWith("9000"));
        }
    }

    /**
     * MSD mode used to be a free-text box, so "2" (dual) was reachable
     * only by knowing to type it - rowMsdMode replaces that with a
     * proper 3-way picker (EMV/MSD/Dual). This drives the picker itself
     * (tap the row, pick an item from the dialog it shows), not
     * type(etMsd, ...) - a test that bypasses the picker could not have
     * caught a bug in the picker's own wiring.
     *
     * Checks CardModel.msd rather than rendering a tile through
     * CardAdapter - the chip text is CardAdapter's own one-line
     * c.msd==2?"DUAL":c.msd==1?"MSD":"EMV" (read directly, confirmed
     * correct), so a round-tripped c.msd is what the chip actually
     * shows.
     */
    @Test
    public void msdModePicker_allThreeRoundTripToPrefAndTileChip() {
        String[] names = {"EMV", "MSD", "DUAL"};
        for (int which = 0; which <= 2; which++) {
            rowMsdMode.performClick();
            AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull("tapping the row must show a picker dialog", dialog);
            shadowOf(dialog).clickOnItem(which);
            shadowOf(Looper.getMainLooper()).idle();

            assertEquals("pref must round-trip for " + names[which],
                    String.valueOf(which), Prefs.get(RuntimeEnvironment.getApplication())
                            .getString("card0_msd", "<none>"));
            assertEquals("CardModel.msd must round-trip for " + names[which],
                    which, activity.cardAt(activity.currentSlot()).msd);
            assertEquals("the row's own label must show the picked mode",
                    names[which], tvMsdModeName.getText().toString());
        }
    }

    /**
     * txtMsdLabel (@string/msd_mode_label) must name all three values,
     * not just 0/1 - the same gap that made dual mode "unreachable from
     * the UI" before rowMsdMode existed. msdPrefFromBox() (now package-
     * visible) must resolve each of 0/1/2 for the matching box value -
     * this is the SAME method collect() and previewText() both now
     * share, so one passing test here backs every caller.
     */
    @Test
    public void msdModeLabel_namesAllThreeValues_andMsdPrefFromBoxResolvesEach() {
        String label = txtMsdLabel.getText().toString();
        assertTrue("label must name 0: " + label, label.contains("0"));
        assertTrue("label must name 1: " + label, label.contains("1"));
        assertTrue("label must name 2: " + label, label.contains("2"));

        type(etMsd, "0");
        assertEquals("0", activity.msdPrefFromBox());

        type(etMsd, "1");
        assertEquals("1", activity.msdPrefFromBox());

        type(etMsd, "2");
        assertEquals("2", activity.msdPrefFromBox());
    }
}
