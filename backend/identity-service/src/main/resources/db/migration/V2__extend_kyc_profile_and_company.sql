ALTER TABLE kyc_profiles ADD COLUMN full_name VARCHAR(191);
ALTER TABLE kyc_profiles ADD COLUMN country VARCHAR(128);
ALTER TABLE kyc_profiles ADD COLUMN address VARCHAR(500);
ALTER TABLE kyc_profiles ADD COLUMN birth_date VARCHAR(32);
ALTER TABLE kyc_profiles ADD COLUMN gender VARCHAR(16);

ALTER TABLE companies ADD COLUMN business_license VARCHAR(128);
