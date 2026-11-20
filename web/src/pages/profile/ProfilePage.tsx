import { useEffect, useRef, useState, type FormEvent } from 'react';
import { logout } from '@/shared/api/session';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { profileOptions, updateProfile, type ProfileFiles } from '@/entities/shelter/data/profile';
import { createImageUploader } from '@/shared/api/image-upload';
import { LoadStateMessage } from '@/shared/ui/LoadState';
import { demoMode } from '@/shared/config/demo';
import { Link } from 'react-router';
import { WorkPage, InfoRows } from '@/shared/ui/WorkPage';
import { useProfile, useSaveProfile } from '@/entities/shelter/data';
import type { ShelterProfile } from '@/entities/shelter/model';
import { Button } from '@/shared/ui/Button';
import { TextField } from '@/shared/ui/TextField';
import { TextAreaField } from '@/shared/ui/TextAreaField';
import { ShelterPhoto } from '@/shared/ui/ShelterPhoto';
import { isSupportedImage } from '@/shared/lib/image';
import './profile.css';

type ImageKind = 'bannerImage' | 'profileImage';

function ProfileImage({
  kind,
  src,
  editing,
  onChange,
  onBusyChange,
}: {
  kind: ImageKind;
  src?: string;
  editing: boolean;
  onChange: (value: string, file: File) => void;
  onBusyChange: (busy: boolean) => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const readerRef = useRef<FileReader | null>(null);
  useEffect(
    () => () => {
      const reader = readerRef.current;
      if (reader) {
        reader.onload = null;
        reader.onerror = null;
        reader.onloadend = null;
        if (reader.readyState === FileReader.LOADING) reader.abort();
      }
    },
    [],
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const label = kind === 'bannerImage' ? '배경이미지' : '프로필이미지';
  return (
    <div className={`profile-photo profile-photo--${kind}`}>
      <ShelterPhoto
        kind={kind}
        className={kind === 'bannerImage' ? 'profile-banner' : undefined}
        src={src}
        alt={label}
      />
      {editing && (
        <>
          <button
            type="button"
            className="profile-photo__edit"
            aria-label={`${label} 변경`}
            aria-busy={busy}
            disabled={busy}
            onClick={() => input.current?.click()}
          >
            <svg
              aria-hidden="true"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M14.5 4h-5L7.5 7H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-2.5z" />
              <circle cx="12" cy="13" r="3.5" />
            </svg>
            <span className="profile-photo__tooltip" aria-hidden="true">
              {busy
                ? '불러오는 중…'
                : kind === 'bannerImage'
                  ? '배경 사진 변경'
                  : '프로필 사진 변경'}
            </span>
          </button>
          <input
            ref={input}
            type="file"
            hidden
            aria-label={`${label} 선택`}
            accept="image/jpeg,image/png"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = '';
              if (!file) return;
              if (!isSupportedImage(file)) {
                setError('10MB 이하의 JPG 또는 PNG 이미지를 선택해 주세요.');
                return;
              }
              setError('');
              setBusy(true);
              onBusyChange(true);
              const reader = new FileReader();
              readerRef.current = reader;
              reader.onload = () => onChange(String(reader.result), file);
              reader.onerror = () => setError('이미지를 읽을 수 없습니다. 다시 선택해 주세요.');
              reader.onloadend = () => {
                setBusy(false);
                onBusyChange(false);
              };
              reader.readAsDataURL(file);
            }}
          />
        </>
      )}
      {editing && error && (
        <p className="profile-photo__error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

export function ProfilePage() {
  return demoMode ? <DemoProfile /> : <LiveProfile />;
}
function DemoProfile() {
  const profile = useProfile();
  const save = useSaveProfile();
  return <ProfileView profile={profile} save={save} />;
}
function LiveProfile() {
  const query = useQuery(profileOptions);
  const cache = useQueryClient();
  const [uploader] = useState(createImageUploader);
  const profile = query.data;
  if (!profile)
    return (
      <WorkPage title="마이페이지" narrow>
        <LoadStateMessage
          state={
            query.error
              ? { status: 'error', message: query.error.message, retry: () => void query.refetch() }
              : { status: 'loading' }
          }
        >
          {null}
        </LoadStateMessage>
        <Link to="/login" replace onClick={() => void logout().catch(() => {})}>
          로그아웃하기
        </Link>
      </WorkPage>
    );
  return (
    <ProfileView
      profile={profile}
      save={async (next, files) => {
        const updated = await updateProfile(profile, next, files, uploader.confirm);
        await cache.cancelQueries({ queryKey: profileOptions.queryKey });
        cache.setQueryData(profileOptions.queryKey, updated);
      }}
    />
  );
}
function ProfileView({
  profile,
  save,
}: {
  profile: ShelterProfile;
  save: (profile: ShelterProfile, files: ProfileFiles) => Promise<unknown> | void;
}) {
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [images, setImages] = useState<Partial<Record<ImageKind, string>>>({});
  const [files, setFiles] = useState<ProfileFiles>({});
  const [loading, setLoading] = useState<Partial<Record<ImageKind, boolean>>>({});
  const imageBusy = Object.values(loading).some(Boolean);
  const fields: [Exclude<keyof ShelterProfile, 'bannerImage' | 'profileImage'>, string][] = [
    ['name', '보호소 이름'],
    ['address', '주소'],
    ['phone', '전화번호'],
    ['holiday', '휴무일'],
    ['hours', '운영시간'],
  ];
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (imageBusy || saving) return;
    const data = new FormData(e.currentTarget);
    const next = { ...profile, ...images };
    for (const key of ['holiday', 'hours', 'introduction'] as const)
      next[key] = String(data.get(key) ?? '').trim();
    setSaving(true);
    setSaveError('');
    try {
      await save(next, files);
      setEditing(false);
    } catch {
      setSaveError('저장하지 못했습니다. 다시 시도해 주세요.');
    } finally {
      setSaving(false);
    }
  };
  return (
    <WorkPage title="마이페이지" narrow>
      <section className="detail-box">
        <h2>보호소 프로필</h2>
        <ProfileImage
          key={`banner-${editing}`}
          kind="bannerImage"
          editing={editing && !saving}
          src={(editing ? images.bannerImage : undefined) ?? profile.bannerImage}
          onChange={(value, file) => {
            setImages((current) => ({ ...current, bannerImage: value }));
            setFiles((current) => ({ ...current, bannerImage: file }));
          }}
          onBusyChange={(value) => setLoading((current) => ({ ...current, bannerImage: value }))}
        />
        <div className="profile-identity">
          <ProfileImage
            key={`profile-${editing}`}
            kind="profileImage"
            editing={editing && !saving}
            src={(editing ? images.profileImage : undefined) ?? profile.profileImage}
            onChange={(value, file) => {
              setImages((current) => ({ ...current, profileImage: value }));
              setFiles((current) => ({ ...current, profileImage: file }));
            }}
            onBusyChange={(value) => setLoading((current) => ({ ...current, profileImage: value }))}
          />
          <strong>{profile.name}</strong>
        </div>
        {editing && (
          <p className="profile-photo__hint">
            JPG, PNG · 최대 10MB · 저장하기를 누르면 반영됩니다.
          </p>
        )}
      </section>
      {editing ? (
        <form className="work-form profile-form" onSubmit={submit}>
          <section className="detail-box">
            <h2>기본정보 수정</h2>
            <div className="work-form">
              {fields.map(([name, label]) => (
                <TextField
                  key={name}
                  name={name}
                  label={label}
                  disabled={['name', 'address', 'phone'].includes(name)}
                  defaultValue={profile[name]}
                  type={name === 'phone' ? 'tel' : 'text'}
                  pattern={name === 'name' || name === 'address' ? '.*\\S.*' : undefined}
                />
              ))}
              <TextAreaField
                label="보호소 소개"
                name="introduction"
                defaultValue={profile.introduction}
                maxLength={2000}
              />
            </div>
          </section>
          {saveError && (
            <p role="alert" className="form-error">
              {saveError}
            </p>
          )}
          <div className="page-actions">
            <Button variant="secondary" disabled={saving} onClick={() => setEditing(false)}>
              취소
            </Button>
            <Button type="submit" disabled={imageBusy || saving}>
              {saving ? '저장 중…' : '저장하기'}
            </Button>
          </div>
        </form>
      ) : (
        <>
          <section className="detail-box">
            <div className="detail-box__heading">
              <h2>기본정보</h2>
              <Button
                variant="secondary"
                size="small"
                onClick={() => {
                  setImages({});
                  setFiles({});
                  setLoading({});
                  setEditing(true);
                }}
              >
                정보 수정
              </Button>
            </div>
            <InfoRows rows={fields.map(([name, label]) => [label, profile[name]])} />
          </section>
          <section className="detail-box">
            <h2>보호소 소개</h2>
            <p>{profile.introduction || '등록된 소개가 없습니다.'}</p>
          </section>
        </>
      )}
      {!editing && (
        <div className="page-actions">
          <Link
            className="profile-logout"
            to="/login"
            replace
            onClick={() => {
              if (demoMode) sessionStorage.removeItem('usee-demo-login');
              else void logout().catch(() => {});
            }}
          >
            <svg
              aria-hidden="true"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M10 17v2H5V5h5v2M14 8l4 4-4 4M8 12h10" />
            </svg>
            로그아웃하기
          </Link>
        </div>
      )}
    </WorkPage>
  );
}
