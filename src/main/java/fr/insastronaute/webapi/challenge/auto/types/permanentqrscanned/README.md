# `PERMANENT_QR_SCANNED`

Awards the challenge to players whose permanent `users.scanned` flag is true.
Scanning the configured permanent QR link sets this flag; the automatic engine
then awards matching active challenges during its next scheduled evaluation.

## Configuration

The type has no configurable business parameter. Its JSON value must still be
exactly an empty object so persisted configuration remains typed and explicit:

```json
{
  "autoChallengeType": "PERMANENT_QR_SCANNED",
  "autoChallengeParameters": {}
}
```

`PermanentQrScannedParameters` owns this structural validation. The challenge
type does not override `validate`, because it has no additional business
constraint.

## Candidate selection

`PermanentQrScannedChallengeType` delegates to
`UserRepository.findScannedUserIdsAfter`. Candidates are ordered by user UUID,
read in bounded `limit + 1` pages, and exposed with a strictly advancing cursor.
The partial PostgreSQL index `idx_users_scanned_id` supports this predicate and
ordering for rows where `scanned = true`.

The condition is intentionally retroactive: a player who scanned before a
challenge was created remains a candidate. The shared award engine still checks
challenge activity, targeting, prerequisites, and previous blocking submissions
before creating a validated submission.
