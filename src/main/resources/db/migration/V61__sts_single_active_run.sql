with ranked_active_runs as (
    select id,
           row_number() over (
               partition by owner_id
               order by updated_at desc, created_at desc, id desc
           ) as active_rank
    from sts_runs
    where status = 'Active'
)
update sts_runs
set status = 'Abandoned',
    updated_at = current_timestamp
where id in (
    select id
    from ranked_active_runs
    where active_rank > 1
);

create unique index uq_sts_runs_one_active_per_owner
    on sts_runs(owner_id)
    where status = 'Active';
