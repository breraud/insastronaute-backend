# Flow Documentation

Focused lifecycle and client integration docs:

- [Cards](cards.md): admin card setup, public/game/hidden category visibility, STS card images, ordering, collections, and user deck behavior.
- [Card category images](card-category-images.md): category artwork upload, generated thumbnails, public S3 URLs, and client consumption.
- [Card printing](card-printing.md): PDF print templates, slot calibration, preview, and physical instance generation.
- [Card admin frontend](card-admin-frontend.md): frontend implementation flow for card instances, physical batches, page stamp lookup, and admin screens.
- [Decks](decks.md): purchasable card decks, multi-card pulls, tier quotas, and weighted drop behavior.
- [Events](events.md): admin event creation, publication, registrations, and player waitlists.
- [Permanent QR link](qr-link.md): anonymous external redirection, admin challenge-mode activation, authentication, and permanent user scan state.
- [Automatic challenges](automatic-challenges.md): type metadata, scheduling, candidate evaluation, idempotent awards, and player submission rules.
- [Admin frontend update](admin-frontend-update.md): integration changes for admin events, published articles, and group challenge submissions.
- [Friends](friends.md): search, requests, notifications, and friendship lifecycle.
- [Game assets](game-assets.md): admin Unity build publication and public game asset URLs.
- [Game configs](game-configs.md): admin JSON config editing, current dev configs, versioned releases, and frontend flow.
- [Game frontend](game-frontend.md): user frontend loading strategy for the active Unity build.
- [Game playing](game-playing.md): authenticated STS run lifecycle, map progression, combat completion, events, and rewards.
- [Admin articles](admin-articles.md): WYSIWYG article drafts, media uploads, HTML publishing, and frontend integration.
- [Admin users frontend](admin-users-frontend.md): admin player search, profile corrections, available challenges, submissions, friendships, and cards.
- [Proof submission](proof-submission.md): direct uploads, batch uploads, text proofs, and final submission.
- [Proof drafts](proof-drafts.md): recover and delete draft proof files.
- [Submission moderation](submission-moderation.md): pending queue, review locks, proof read URLs, and decisions.
- [Submission discussion](submission-discussion.md): player/moderator message lifecycle and permissions.
- [Proof rules](proof-rules.md): accepted proof types, file formats, limits, and file flow summary.
- [Chasse au trésor](treasure-hunt.md): admin path/checkpoint setup, player validation, vault codes, and final PDF unlock.
