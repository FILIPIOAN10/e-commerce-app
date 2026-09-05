-- ==========================================================
-- Promo campaigns: remember what to put back
-- ==========================================================
-- applyActiveCampaigns() wrote discount/special_price onto every product in a
-- running campaign, and nothing ever undid it. When a campaign's end_time
-- passed it simply stopped being selected, so the promotional price stayed on
-- the product forever — invisibly, because the campaign no longer showed as
-- active in the admin UI. Deleting a campaign had the same effect.
--
-- Two columns close that:
--   * promo_campaign_products.original_discount — what the product's discount
--     was before this campaign touched it, so the sweep can restore it.
--   * promo_campaigns.applied — whether this campaign's prices are currently
--     pushed onto products. It turns the sweep from "rewrite everything, every
--     minute" into "apply once when it starts, revert once when it ends", which
--     is also what stops the 60-second write storm.

ALTER TABLE promo_campaign_products
    ADD COLUMN IF NOT EXISTS original_discount NUMERIC(12,2);

ALTER TABLE promo_campaigns
    ADD COLUMN IF NOT EXISTS applied BOOLEAN NOT NULL DEFAULT FALSE;

-- Campaigns inside their window right now have already had their prices pushed
-- onto products by the old sweep. Mark them applied so the revert path takes
-- ownership of them when they end, rather than leaving them stuck on forever.
--
-- Their original_discount stays NULL: the pre-campaign value was overwritten
-- before this column existed and is not recoverable. The service treats NULL as
-- "restore to no discount", which errs toward charging full price rather than
-- leaving an expired promotion running.
UPDATE promo_campaigns
   SET applied = TRUE
 WHERE active = TRUE
   AND start_time <= CURRENT_TIMESTAMP
   AND end_time   >  CURRENT_TIMESTAMP;

-- Both sweep queries filter on applied; campaigns are few but this keeps the
-- once-a-minute pass off a sequential scan as the table accumulates history.
CREATE INDEX IF NOT EXISTS idx_promo_campaigns_applied ON promo_campaigns (applied);
