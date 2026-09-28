-- 0003_reports_bucket.sql
--
-- Report files move off device-local MediaStore URIs and into object storage, so a report
-- generated on one device opens on any device its owner signs in to. Before this there was no
-- bucket for report files at all: `pdf_file_path` held values like
-- `content://media/external_primary/file/1000929154`, whose ids are per-device and sequential.
-- The row synced; the document did not.
--
-- Object layout is `{user_id}/{report_id}.{pdf|csv}`, derived from the row rather than stored
-- on it, so there is no key column to drift out of step with its owner. The leading uid is what
-- the policies below match on.
--
-- Applied 2026-09-23. C6: this file records the schema, it does not re-apply it.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'reports',
    'reports',
    false,
    10485760, -- 10 MB; a session report is tens of KB, so this is a runaway guard, not a budget
    array['application/pdf', 'text/csv']
)
on conflict (id) do nothing;

-- Visibility mirrors public.reports.reports_select_own exactly — owner, or admin. Not
-- patient-linked: reports_select_own is `auth.uid() = user_id or is_admin(auth.uid())`, so a
-- colleague cannot select the row. A wider bucket policy would hand out a PDF whose record is
-- invisible to the reader.

create policy "reports: select own folder"
    on storage.objects for select
    using (
        bucket_id = 'reports'
        and (storage.foldername(name))[1] = (select (auth.uid())::text)
    );

create policy "reports: insert own folder"
    on storage.objects for insert
    with check (
        bucket_id = 'reports'
        and (storage.foldername(name))[1] = (select (auth.uid())::text)
    );

create policy "reports: update own folder"
    on storage.objects for update
    using (
        bucket_id = 'reports'
        and (storage.foldername(name))[1] = (select (auth.uid())::text)
    )
    with check (
        bucket_id = 'reports'
        and (storage.foldername(name))[1] = (select (auth.uid())::text)
    );

create policy "reports: admin read all"
    on storage.objects for select
    using (bucket_id = 'reports' and is_admin(auth.uid()));

-- No DELETE policy, deliberately. C8: nothing is deleted. The samples bucket has none either,
-- and adding one here would be the first available way to destroy a clinical record.
