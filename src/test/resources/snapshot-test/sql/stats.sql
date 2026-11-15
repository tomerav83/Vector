-- A value "wrong" fails the check, as a real source's SQL rejects a file it cannot trust.
select error('file holds the wrong value')
where exists (select * from read_csv({file}, all_varchar = true) where value = 'wrong');
insert into stats select value from read_csv({file}, all_varchar = true);
