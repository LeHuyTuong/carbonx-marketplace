ALTER TABLE market_listings
    ADD COLUMN seller_user_id BIGINT NULL AFTER seller_company_id;
