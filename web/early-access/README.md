# Early-access page

A static page that introduces the app and collects email addresses for early access. There is
no build step: the folder is the website.

| File | What it is |
|---|---|
| `index.html` | The page |
| `styles.css` | Its look, one dark theme in the app's colours |
| `script.js` | The particles, the moving parts and the sign-up form |
| `privacy.html` | The privacy note the page links to |
| `assets/` | The character's clips and the phone captures |
| `../signup-sheet/Code.gs` | The script that writes sign-ups into a Google Sheet; not part of the published page |

## Preview it

```bash
python3 -m http.server 4173 --directory web/early-access
```

Then open `http://localhost:4173`. Add `?demo` to the address to see what a successful sign-up
looks like without sending anything.

## Before it goes online

The form is connected to its Google Sheet and `privacy.html` carries a contact address. One
thing is left:

1. **Check the two open questions** in the project README under "Releasing on Google Play":
   the character is styled on a real actor, and the clips were made on Kling's free plan. A
   public page raises both as much as the app does.

## Where sign-ups go

Sign-ups are kept in a Google Sheet, written by a small Google Apps Script that belongs to the
sheet: `web/signup-sheet/Code.gs`. It sits outside this folder so that it is not published
with the page.

1. Create a Google Sheet, in the Google account that should own the list.
2. In the sheet, open **Extensions > Apps Script**, delete what is in the editor and paste in
   `Code.gs`. Save.
3. Choose **Deploy > New deployment**, pick the type **Web app**, and set **Execute as** to
   **Me** and **Who has access** to **Anyone**. Deploy, and approve the access Google asks
   for: the script needs to write to this one spreadsheet.
4. Copy the **Web app URL**, which ends in `/exec`, into `FORM_ENDPOINT` at the top of
   `script.js`.

The first sign-up makes a tab called "Sign-ups" with two columns, when and the address. An
address that signs up twice is kept once. After changing `Code.gs`, deploy again with
**Deploy > Manage deployments > Edit > New version**, which keeps the same address.

The web app's address is public, like any form's: anyone who reads the page's source can post
to it. The script refuses anything that isn't an email address and stops at 20,000 rows.

## Put it online

Any static host will do: Cloudflare Pages, Netlify, Vercel or GitHub Pages. Point the host at
this folder with no build command. To serve it from a subdomain, add a CNAME record for the
subdomain that points at the address the host gives you; the host issues the certificate.

## Where the pictures come from

- `vaathi-wave.webp` and `vaathi-stop.webp` are the app's own clips from `app/src/main/res/raw`,
  and `vaathi-shades.webp` and `vaathi-head.webp` its stills. `art/make_clips.py` makes them.
- The `shot-*.jpg` files are captures from a phone running the app over Instagram Reels. In the
  two that show the count bubble, everything but the bubble and the status bar is blurred, so
  that no one else's reel appears on the page. Behind the nudge the reel is dimmed by the app
  itself and only faintly visible.

The page loads about 3 MB, most of it the two clips.
