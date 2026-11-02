import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { DemoPage, InfoRows } from '@/features/shelter-demo/Page';
import { useShelterDemo, type ShelterProfile } from '@/features/shelter-demo/store';
import { Button } from '@/shared/ui/Button';
import { TextField } from '@/shared/ui/TextField';
import { TextAreaField } from '@/shared/ui/TextAreaField';
import banner from '@/shared/assets/figma/shelter-banner.png';
import avatar from '@/shared/assets/figma/shelter-avatar.png';

export function ProfilePage() {
  const profile = useShelterDemo((s) => s.profile);
  const save = useShelterDemo((s) => s.saveProfile);
  const [editing, setEditing] = useState(false);
  const [saved, setSaved] = useState(false);
  const fields: [keyof ShelterProfile, string][] = [
    ['name', '보호소 이름'],
    ['address', '주소'],
    ['phone', '전화번호'],
    ['holiday', '휴무일'],
    ['hours', '운영시간'],
  ];
  const submit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const next = { ...profile };
    for (const key of Object.keys(profile) as (keyof ShelterProfile)[])
      next[key] = String(data.get(key) ?? '').trim();
    if (!next.name || !next.address || !next.phone) return;
    save(next);
    setEditing(false);
    setSaved(true);
  };
  return (
    <DemoPage title="마이페이지" narrow>
      <p className="form-feedback">등록된 보호센터 정보를 확인하고 수정할 수 있습니다.</p>
      <section className="detail-box">
        <h2>보호센터 프로필</h2>
        <img className="profile-banner" src={banner} alt="보호센터 예시 전경" />
        <div className="profile-identity">
          <img src={avatar} alt="" />
          <strong>{profile.name}</strong>
        </div>
      </section>
      {editing ? (
        <form className="work-form" onSubmit={submit}>
          <section className="detail-box">
            <h2>기본정보 수정</h2>
            <div className="work-form">
              {fields.map(([name, label]) => (
                <TextField
                  key={name}
                  name={name}
                  label={label}
                  required={['name', 'address', 'phone'].includes(name)}
                  defaultValue={profile[name]}
                  type={name === 'phone' ? 'tel' : 'text'}
                  pattern={name === 'name' || name === 'address' ? '.*\\S.*' : undefined}
                />
              ))}
              <TextAreaField
                label="보호센터 소개"
                name="introduction"
                defaultValue={profile.introduction}
                maxLength={2000}
              />
            </div>
          </section>
          <div className="page-actions">
            <Button variant="secondary" onClick={() => setEditing(false)}>
              취소
            </Button>
            <Button type="submit">저장하기</Button>
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
                  setEditing(true);
                  setSaved(false);
                }}
              >
                정보 수정
              </Button>
            </div>
            <InfoRows rows={fields.map(([name, label]) => [label, profile[name]])} />
          </section>
          <section className="detail-box">
            <h2>보호센터 소개</h2>
            <p>{profile.introduction || '등록된 소개가 없습니다.'}</p>
          </section>
        </>
      )}
      {saved && (
        <p role="status" className="form-feedback">
          예시 보호센터 정보를 저장했습니다.
        </p>
      )}
      {!editing && (
        <div className="page-actions">
          <Link className="profile-logout" to="/login">
            <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10 17v2H5V5h5v2M14 8l4 4-4 4M8 12h10" />
            </svg>
            로그아웃하기
          </Link>
        </div>
      )}
    </DemoPage>
  );
}
