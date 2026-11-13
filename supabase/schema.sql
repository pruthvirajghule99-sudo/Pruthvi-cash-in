-- Run in Supabase SQL Editor as a project administrator.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  role text not null default 'user' check (role in ('user', 'admin')),
  created_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, email, role)
  values (new.id, new.email, 'user')
  on conflict (id) do update set email = excluded.email;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
for each row execute procedure public.handle_new_user();

create table if not exists public.loan_applications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  amount integer not null check (amount between 5000 and 500000),
  tenure_months integer not null check (tenure_months between 3 and 36),
  purpose text not null check (char_length(purpose) between 1 and 500),
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.loan_applications enable row level security;

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.profiles p where p.id = (select auth.uid()) and p.role = 'admin');
$$;
revoke all on function public.is_admin() from public;
grant execute on function public.is_admin() to authenticated;

drop policy if exists "Users can read own profile" on public.profiles;
create policy "Users can read own profile" on public.profiles for select to authenticated using (id = (select auth.uid()) or public.is_admin());
drop policy if exists "Users can update own profile email" on public.profiles;
create policy "Users can update own profile email" on public.profiles for update to authenticated using (id = (select auth.uid())) with check (id = (select auth.uid()) and role = 'user');

drop policy if exists "Applicants and admins can read applications" on public.loan_applications;
create policy "Applicants and admins can read applications" on public.loan_applications for select to authenticated using (user_id = (select auth.uid()) or public.is_admin());
drop policy if exists "Applicants can create own pending applications" on public.loan_applications;
create policy "Applicants can create own pending applications" on public.loan_applications for insert to authenticated with check (user_id = (select auth.uid()) and status = 'pending');
drop policy if exists "Admins can update applications" on public.loan_applications;
create policy "Admins can update applications" on public.loan_applications for update to authenticated using (public.is_admin()) with check (public.is_admin());

grant usage on schema public to authenticated;
grant select, update on public.profiles to authenticated;
grant select, insert, update on public.loan_applications to authenticated;

-- Promote an admin by running this manually for a trusted, registered account:
-- update public.profiles set role = 'admin' where email = 'admin@example.com';
