# Voice & Tone (Layer 3 — stable across runs)

Source: CONTEXT.md §1 (Project Overview — Primary user / Voice) and §4 (Design principles).

## Voice

Clinical, restrained, scientifically precise. AgarthaVision is a diagnostic tool for medical
technologists working with patient stool smear samples — not a consumer app.

## Do

- Use exact units and measurements: EPG, eggs/gram, confidence %, ms, px.
- Italicize all binomial species names: *Ascaris lumbricoides*, *Trichuris trichiura*, *Hookworm*.
- Use tabular numerals for every data field — IDs, timestamps, GPS coordinates, EPG values, confidence.
- Keep UI copy short and instructional — the medtech is mid-procedure and needs to act fast.
- Use established clinical and laboratory terminology.
- Describe findings factually: "3 eggs confirmed", "EPG: 72", "Sync failed".

## Don't

- No playful or celebratory language. No emoji in clinical UI.
- No decorative ornament, heavy gradients, or shadow-heavy cards.
- No informality — the user is handling diagnostic data that informs patient treatment.
- Do not invent species names, abbreviations, or acronyms not already in the class labels.
- Do not soften or qualify error states — state them clearly so the medtech can act.

## Example (target register)

Good: "Session ended. 3 eggs confirmed across 2 samples. EPG: 72. Tap to generate report."
Bad:  "Great job! You found some eggs 🎉 — time to wrap up!"

Good: "Cloud connection lost. Recording paused. Reconnect to resume."
Bad:  "Oops! Looks like you're offline. We'll try again soon."
