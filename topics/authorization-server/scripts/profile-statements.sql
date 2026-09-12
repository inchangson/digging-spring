select calls,
       round(total_exec_time::numeric, 3) as total_exec_ms,
       round(mean_exec_time::numeric, 3) as mean_exec_ms,
       rows,
       shared_blks_hit,
       shared_blks_read,
       temp_blks_read,
       temp_blks_written,
       regexp_replace(query, '[[:space:]]+', ' ', 'g') as query
from pg_stat_statements
where query ilike '%demo_policy%'
   or query ilike '%oauth2_registered_client%'
   or query ilike '%oauth2_authorization%'
order by total_exec_time desc;
