-- RING-GO beta intake responses
-- Run once: sqlite3 ringgo.db < migrations/001_beta_intake_responses.sql

CREATE TABLE IF NOT EXISTS beta_intake_responses (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  submitted_at TEXT,                                -- ISO-8601 UTC, NULL while draft
  draft        INTEGER NOT NULL DEFAULT 1,          -- 1 = draft,  0 = submitted
  revision     INTEGER NOT NULL DEFAULT 1,          -- monotonically increasing per save
  response_json TEXT NOT NULL,                      -- full 10-category JSON blob
  created_at   TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
);

-- response_json top-level keys (카테고리별):
--   category_1_kpi            goal + kpi_weekly_active / kpi_send_rate / kpi_no_edit_rate /
--                              kpi_2week_retention / kpi_cta_rate / kpi_pay_intent / kpi_incident
--   category_2_schedule       phase_0_start / phase_0_end / phase_1_* / phase_2_* / phase_3_*
--   category_3_time           weekly_time / daily_min / channels[] / memo
--   category_4_resources      channels[]{enabled, memo} / target_count
--   category_5_targets        industries[] / regions[] / biz_size
--   category_6_exclusions     conditions[] / extra
--   category_7_pricing        price_type / monthly_price / discount_rate / pay_method / refund
--   category_8_privacy_matrix data_8_1~data_8_8c (A/B/C) + ok_8_1~ok_8_8c + memo
--   category_9_tone           samples{상황:수} / good_criteria / bad_criteria / forbidden_words
--   category_10_approval      l1[] / l2ops[] / l2tech[] / l3[] / memo

CREATE INDEX IF NOT EXISTS idx_bir_revision
  ON beta_intake_responses (revision DESC);
