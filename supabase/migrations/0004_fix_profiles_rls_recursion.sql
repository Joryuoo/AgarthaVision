-- Fix recursive RLS policies that query public.profiles from inside policies on
-- public.profiles. Apply in Supabase Dashboard -> SQL Editor after 0001-0003.

create or replace function public.is_admin(user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.profiles
        where id = user_id and role = 'admin'
    );
$$;

grant execute on function public.is_admin(uuid) to authenticated;

drop policy if exists "profiles_select_own" on public.profiles;
drop policy if exists "sessions_select_own" on public.sessions;
drop policy if exists "samples_select_own" on public.samples;
drop policy if exists "detections_select_via_sample" on public.detections;

create policy "profiles_select_own"
on public.profiles for select
using (
    auth.uid() = id
    or public.is_admin(auth.uid())
);

create policy "sessions_select_own"
on public.sessions for select
using (
    auth.uid() = user_id
    or public.is_admin(auth.uid())
);

create policy "samples_select_own"
on public.samples for select
using (
    auth.uid() = user_id
    or public.is_admin(auth.uid())
);

create policy "detections_select_via_sample"
on public.detections for select
using (
    exists (
        select 1 from public.samples s
        where s.id = sample_id
        and (
            s.user_id = auth.uid()
            or public.is_admin(auth.uid())
        )
    )
);
