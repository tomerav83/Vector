-- What readers query on top of the tables, as sql/views.sql: a view over the published snapshot, and a macro.
create or replace view stats_count as select count(*) as stats from stats;
create or replace macro shout(text) as upper(text);
