create table source (name varchar primary key, title varchar, page varchar, period varchar, rows bigint, matches_lock boolean, problem varchar);
create or replace table list (code varchar primary key);
create or replace table segments (code varchar);
create or replace table stats (value varchar);
