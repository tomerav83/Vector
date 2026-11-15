select error('needs list') where not exists (select * from list);
insert into segments select code from read_csv({file}, all_varchar = true);
