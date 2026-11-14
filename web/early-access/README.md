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

## Preview it

```bash
python3 -m http.server 4173 --directory web/early-access
```

Then open `http://localhost:4173`. Add `?demo` to the address to see what a successful sign-up
looks like without sending anything.

## Before it goes online

1. **Connect the form.** Set `FORM_ENDPOINT` at the top of `script.js` to the address of a form
   service that accepts a JSON POST of `{"email": "..."}`. Until then the form sends nothing
   and tells the visitor so.
2. **Add a contact address.** Replace `CONTACT_EMAIL` in `privacy.html`, in both places.
3. **Check the two open questions** in the project README under "Releasing on Google Play":
   the character is styled on a real actor, and the clips were made on Kling's free plan. A
   public page raises both as much as the app does.

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
