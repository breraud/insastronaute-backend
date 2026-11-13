-- Validating a challenge credited points but never tokens, so a player could finish
-- a challenge and see nothing spendable. Every challenge point is now worth one token.

-- Challenge points are recomputed from scratch on every validation, while tokens are a
-- balance the player spends on packs and STS runs. Mirroring the recomputed total into
-- the balance would erase those purchases, so this ledger records how much challenges
-- have already granted and only the difference is credited from now on.
ALTER TABLE users
    ADD COLUMN challenge_tokens_granted BIGINT NOT NULL DEFAULT 0;

-- Players who already earned points never received the matching tokens: pay that debt
-- once, and open the ledger at the total they have earned so far.
UPDATE users
SET tokens = tokens + challenge_points,
    challenge_tokens_granted = challenge_points
WHERE challenge_points > 0;
