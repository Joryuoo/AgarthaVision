-- AgarthaVision Phase 1 MVP — initial schema
-- Run via: Supabase dashboard → SQL Editor → paste → Run
-- See docs/04_CLOUD_BACKEND_PLAN.md §4 for schema rationale.

-- ── Extensions ───────────────────────────────────────────────────────────────
create extension if not exists "uuid-ossp";

-- ── profiles ─────────────────────────────────────────────────────────────────
-- One row per authenticated user. Linked to auth.users via id.
create table public.profiles (
    id         uuid primary key references auth.users(id) on delete cascade,
    full_name  text,
    role       text not null default 'medtech' check (role in ('medtech', 'admin')),
    created_at timestamptz not null default now()
);

-- Auto-create a profile row on first sign-in.
create or replace function public.handle_new_user()
returns trigger as $$
begin
    insert into public.profiles (id, role) values (new.id, 'medtech');
    return new;
end;
$$ language plpgsql security definer;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- ── sessions ─────────────────────────────────────────────────────────────────
-- A capture session = one run of the recording UI by one medtech.
create table public.sessions (
    id         uuid primary key default uuid_generate_v4(),
    user_id    uuid not null references public.profiles(id),
    device_id  text not null,                        -- Settings.Secure.ANDROID_ID
    started_at timestamptz not null default now(),
    ended_at   timestamptz,
    notes      text
);

-- ── samples ──────────────────────────────────────────────────────────────────
-- A sample = a flagged frame the user verified. Pre-verification frames live only on-device.
create table public.samples (
    id                    uuid primary key default uuid_generate_v4(),
    session_id            uuid not null references public.sessions(id) on delete cascade,
    user_id               uuid not null references public.profiles(id),
    captured_at           timestamptz not null,               -- when the frame was captured on-device
    verified_at           timestamptz not null default now(), -- when synced to cloud
    gps_latitude          double precision,
    gps_longitude         double precision,
    gps_accuracy          real,
    storage_path          text not null,                      -- Supabase Storage object key: {user_id}/{sample_id}.jpg
    roboflow_model_version text not null,
    user_note             text
);

-- ── detections ───────────────────────────────────────────────────────────────
-- One row per detected egg in a verified sample. A sample can have many.
create table public.detections (
    id               uuid primary key default uuid_generate_v4(),
    sample_id        uuid not null references public.samples(id) on delete cascade,
    class_label      text not null,                       -- e.g. 'ascaris_lumbricoides'
    confidence       real not null check (confidence between 0 and 1),
    bbox_x           real not null,                       -- normalized 0–1
    bbox_y           real not null,
    bbox_w           real not null,
    bbox_h           real not null,
    verified_by_user boolean not null default true        -- user accepted this detection
);

-- ── Indexes ──────────────────────────────────────────────────────────────────
create index samples_session_idx    on public.samples(session_id);
create index samples_user_idx       on public.samples(user_id);
create index detections_sample_idx  on public.detections(sample_id);
create index detections_class_idx   on public.detections(class_label);

-- ── Row Level Security ───────────────────────────────────────────────────────
alter table public.profiles   enable row level security;
alter table public.sessions   enable row level security;
alter table public.samples    enable row level security;
alter table public.detections enable row level security;

-- Profiles: each user reads their own row; admins can read everyone.
create policy "profiles_select_own"
on public.profiles for select
using (
    auth.uid() = id
    or (select role from public.profiles where id = auth.uid()) = 'admin'
);

-- Sessions: medtech reads + writes own; admin reads all.
create policy "sessions_select_own"
on public.sessions for select
using (
    auth.uid() = user_id
    or (select role from public.profiles where id = auth.uid()) = 'admin'
);

create policy "sessions_insert_own"
on public.sessions for insert
with check ( auth.uid() = user_id );

create policy "sessions_update_own"
on public.sessions for update
using ( auth.uid() = user_id );

-- Samples: same scoping pattern.
create policy "samples_select_own"
on public.samples for select
using (
    auth.uid() = user_id
    or (select role from public.profiles where id = auth.uid()) = 'admin'
);

create policy "samples_insert_own"
on public.samples for insert
with check ( auth.uid() = user_id );

-- Detections: scoped via the parent sample's user_id.
create policy "detections_select_via_sample"
on public.detections for select
using (
    exists (
        select 1 from public.samples s
        where s.id = sample_id
        and (
            s.user_id = auth.uid()
            or (select role from public.profiles where id = auth.uid()) = 'admin'
        )
    )
);

create policy "detections_insert_via_sample"
on public.detections for insert
with check (
    exists (
        select 1 from public.samples s
        where s.id = sample_id and s.user_id = auth.uid()
    )
);
