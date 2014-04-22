select user_id, DATE(FROM_UNIXTIME(post_time)) as next_post_date, postdate from forum_posts fp JOIN (select forum_user_id, DATE(FROM_UNIXTIME(post_time)) as postdate from forum_posts) a ON fp.forum_user_id = a.forum_user_id WHERE next_post_date > postdate limit 1;

select p1.user_id, p1.postdate, p2.postdate as nextdate, DATEDIFF(p2.postdate, p1.postdate) as diff from postdates p1 JOIN postdates p2 ON p1.user_id = p2.user_id WHERE p2.postdate > p1.postdate group by p1.user_id, p1.postdate order by p1.user_id, p1.postdate, diff;

create table postdates as select DISTINCT user_id, DATE(FROM_UNIXTIME(post_time)) as postdate from forum_posts p JOIN hash_mapping h ON p.forum_user_id = h.forum_user_id;



