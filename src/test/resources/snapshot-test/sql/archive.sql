-- A source published as a zip: {file:stats.csv} is that entry of it.
insert into stats select value from read_csv({file:stats.csv}, all_varchar = true);
