--- 
--- Dump the relevant fields of the tmapp database.
---
--- This needs to be run as postgres.
---
--- CSV path needs to be fully qualified
--- 
BEGIN;
--- Languages
COPY (SELECT lang.id,lang.code,lang.name FROM tm_languagespec AS lang ORDER BY lang.id) TO '/tmp/djangodb_lang.csv' WITH CSV HEADER FORCE QUOTE *;
--- Users
COPY (SELECT usr.user_id,usr.lang_other_id,usr.has_trained,usr.birth_country_id,usr.home_country_id,usr.hours_per_week,usr.is_pro_translator,auth.username,auth.first_name,auth.last_name FROM tm_userconf AS usr,auth_user AS auth WHERE usr.user_id = auth.id ORDER BY auth.id) TO '/tmp/djangodb_user.csv' WITH CSV HEADER FORCE QUOTE *;
--- Source text
COPY (SELECT * from tm_sourcetxt AS src WHERE src.doc != 'training') TO '/tmp/djangodb_src.csv' WITH CSV HEADER FORCE QUOTE *;
--- Target text
COPY (SELECT tgt.id,tgt.src_id,tgt.lang_id,tgt.is_machine,tgt.date,tgt.txt,meta.ui_id,meta.user_id,meta.action_log,meta.is_valid FROM tm_targettxt AS tgt INNER JOIN tm_translationstats AS meta ON tgt.id = meta.tgt_id ORDER BY meta.user_id,tgt.src_id) TO '/tmp/djangodb_tgt.csv' WITH CSV HEADER FORCE QUOTE *;
COMMIT;
