/**
 * Collects early-access sign-ups from the Vaathi ride-uhh! page into the spreadsheet this script
 * belongs to. Paste it into the sheet's own script editor (Extensions > Apps Script) and deploy
 * it as a web app; web/early-access/README.md has the steps.
 *
 * The page posts {"email": "..."} and gets back {"ok": true or false, "result": "..."}.
 * Each new address becomes one row: when it arrived, and the address. Nothing else is kept.
 */

const SHEET_NAME = "Sign-ups";

// The web app's address is public, so anyone can post to it. This stops a flood from filling
// the spreadsheet
const MAX_SIGNUPS = 20000;

const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const MAX_EMAIL_LENGTH = 254;

// A cell that starts with one of these is read by Sheets as a formula, not as text
const FORMULA_START = /^[=+\-@]/;

function doPost(event) {
  let email;
  try {
    email = String(JSON.parse(event.postData.contents).email || "").trim().toLowerCase();
  } catch (error) {
    return reply(false, "unreadable");
  }
  if (email.length > MAX_EMAIL_LENGTH || !EMAIL.test(email) || FORMULA_START.test(email)) {
    return reply(false, "invalid");
  }

  // One sign-up at a time, so two arriving together can't both pass the check for a duplicate
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const sheet = signupSheet();
    const lastRow = sheet.getLastRow();
    if (lastRow > 1) {
      const known = sheet.getRange(2, 2, lastRow - 1, 1).getValues().some((row) => row[0] === email);
      // Signing up twice is answered like the first time: the page has nothing different to say
      if (known) return reply(true, "already");
    }
    if (lastRow > MAX_SIGNUPS) return reply(false, "full");
    sheet.appendRow([new Date(), email]);
    return reply(true, "added");
  } finally {
    lock.releaseLock();
  }
}

/** The sheet the sign-ups go in, made with its heading row the first time it is needed. */
function signupSheet() {
  const book = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = book.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = book.insertSheet(SHEET_NAME);
    sheet.appendRow(["When", "Email"]);
    sheet.setFrozenRows(1);
    // Plain text, so that no address is ever taken for a number or a formula
    sheet.getRange("B:B").setNumberFormat("@");
  }
  return sheet;
}

function reply(ok, result) {
  return ContentService.createTextOutput(JSON.stringify({ ok: ok, result: result }))
    .setMimeType(ContentService.MimeType.JSON);
}
