-- Background processing of uploads (HEIC conversion, thumbnail): PROCESSING, READY or FAILED
ALTER TABLE photos ADD COLUMN processing_status VARCHAR(20) DEFAULT 'READY' NOT NULL;
