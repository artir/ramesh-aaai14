create table user500 as select user_id, "1.0" as prediction,  count(*) as count from forum_posts p JOIN hash_mapping h ON p.forum_user_id = h.forum_user_id group by user_id order by count desc limit 500; 

select u.user_id, prediction INTO OUTFILE '/tmp/top500predictionpos.txt' FIELDS TERMINATED BY '\t' LINES TERMINATED BY '\n' from user500 u JOIN hash_mapping h ON u.user_id=h.user_id JOIN course_grades c ON c.anon_user_id = h.anon_user_id WHERE normal_grade > 0;


select h.user_id, "0.0" INTO OUTFILE '/tmp/top500predictionneg.txt' FIELDS TERMINATED BY '\t' LINES TERMINATED BY '\n' from user500 u RIGHT JOIN hash_mapping h ON u.user_id=h.user_id JOIN course_grades c ON c.anon_user_id = h.anon_user_id WHERE normal_grade > 0 AND u.user_id IS NULL; 


select user_id, "1.0" as truth INTO OUTFILE '/tmp/performanceTrue500.txt' FIELDS TERMINATED BY '\t' LINES TERMINATED BY '\n' from course_grades cg JOIN hash_mapping hm ON cg.anon_user_id = hm.anon_user_id where normal_grade > (select avg(normal_grade) from course_grades where normal_grade != 0);

select user_id, "0.0" as truth INTO OUTFILE '/tmp/performanceFalsetop500.txt' FIELDS TERMINATED BY '\t' LINES TERMINATED BY '\n' from course_grades cg JOIN hash_mapping hm ON cg.anon_user_id = hm.anon_user_id where normal_grade < (select avg(normal_grade) from course_grades where normal_grade != 0) AND normal_grade > 0;

