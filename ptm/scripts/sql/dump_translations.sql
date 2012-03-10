--- 
--- This needs to be run as postgres.
---
--- CSV path needs to be fully qualified
--- Might to enable escaping
--- 
BEGIN;
COPY (SELECT * FROM tm_sourcetxt,tm_targettxt,tm_translationstats
WHERE tm_sourcetxt.id=tm_targettxt.src_id and tm_targettxt.id=tm_translationstats.tgt_id and tm_targettxt.is_machine=FALSE) TO '/tmp/djangodb_trans.csv' WITH CSV HEADER FORCE QUOTE *;
COMMIT;
