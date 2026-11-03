import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ShelterLayout } from '@/shared/ui/ShelterLayout';
import { ShelterHeader } from '@/shared/ui/ShelterHeader';
import { ChatSidebar } from '@/shared/ui/ChatSidebar';
import { AnimalCard } from '@/shared/ui/AnimalCard';
import type { AnimalCardProps } from '@/shared/ui/AnimalCard';
import { FilterSelect, ListPanel } from '@/shared/ui/ListPanel';
import { FormSection } from '@/shared/ui/FormSection';
import { TextField } from '@/shared/ui/TextField';
import { SelectField } from '@/shared/ui/SelectField';
import { TextAreaField } from '@/shared/ui/TextAreaField';
import { ChoiceGroup } from '@/shared/ui/ChoiceGroup';
import { ImageUpload } from '@/shared/ui/ImageUpload';
import { Button } from '@/shared/ui/Button';
import shelterAvatar from '@/shared/assets/figma/shelter-avatar.png';
import chatAvatar from '@/shared/assets/figma/chat-avatar.png';
import animalImage from '@/shared/assets/figma/animal-example.png';

const animals: { id: string; status: AnimalCardProps['status']; adoptable: boolean }[] = [
  { id: '1', status: 'protected', adoptable: true },
  { id: '2', status: 'closed', adoptable: false },
  { id: '3', status: 'returned', adoptable: false },
  { id: '4', status: 'adopted', adoptable: false },
];
const conversations = [
  {
    id: '1',
    name: '시샤',
    avatarSrc: chatAvatar,
    preview: '아이 몇 개월인지 알 수 있을까요?',
    timeLabel: '1시간 전',
    unreadCount: 1,
  },
  {
    id: '2',
    name: '시샤',
    avatarSrc: chatAvatar,
    preview: '확인 감사합니다.',
    timeLabel: '2시간 전',
    unreadCount: 0,
  },
];

export function ShelterPreviewPage() {
  const [params] = useSearchParams();
  const formMode = params.get('view') === 'form';
  const [chatOpen, setChatOpen] = useState(false);
  const [selectedChat, setSelectedChat] = useState<string>();
  const [status, setStatus] = useState('');
  const [adoptable, setAdoptable] = useState('');
  const [sort, setSort] = useState('latest');
  const [files, setFiles] = useState<File[]>([]);
  const [neutered, setNeutered] = useState('');
  const [message, setMessage] = useState('');
  const visible = animals.filter(
    (item) =>
      (!status || item.status === status) && (!adoptable || String(item.adoptable) === adoptable),
  );
  const sorted = sort === 'oldest' ? [...visible].reverse() : visible;
  return (
    <ShelterLayout
      header={
        <ShelterHeader
          homeHref="/dev/ui"
          navigation={
            <>
              <Link to="/dev/shelter" aria-current={!formMode ? 'page' : undefined}>
                보호동물
              </Link>
              <Link to="/dev/shelter?view=form" aria-current={formMode ? 'page' : undefined}>
                등록 폼
              </Link>
              <Link to="/dev/ui">기본 UI</Link>
            </>
          }
          profile={
            <>
              <img src={shelterAvatar} alt="" width="36" height="36" />
              <span>박범준동물보호센터</span>
            </>
          }
        />
      }
      sidebar={
        !formMode && (
          <ChatSidebar
            open={chatOpen}
            onOpenChange={setChatOpen}
            conversations={conversations}
            selectedId={selectedChat}
            onSelect={(id) => {
              setSelectedChat(id);
              setMessage(`예시 대화 ${id} 선택. 실제 채팅 연결 전입니다.`);
            }}
          />
        )
      }
    >
      <p className="shelter-preview-notice">
        공통 컴포넌트 미리보기 · 예시 데이터이며 저장하거나 전송하지 않습니다.
      </p>
      {formMode ? (
        <form
          className="shelter-form"
          onSubmit={(event) => {
            event.preventDefault();
            setMessage(
              files.length
                ? '폼 입력 검증 완료. 서버로 전송하지 않았습니다.'
                : '동물 사진을 1장 이상 선택해 주세요.',
            );
          }}
        >
          <FormSection title="동물 사진">
            <ImageUpload files={files} onChange={setFiles} hideLabel />
          </FormSection>
          <FormSection title="기본정보">
            <TextField
              label="동물등록번호"
              name="registrationNumber"
              placeholder="동물등록번호를 입력해 주세요."
            />
            <TextField
              label="마이크로칩번호"
              name="microchip"
              placeholder="마이크로칩 번호를 입력해 주세요."
            />
            <SelectField label="동물 종류" name="species" required defaultValue="">
              <option value="" disabled>
                선택
              </option>
              <option value="dog">개</option>
              <option value="cat">고양이</option>
            </SelectField>
            <TextField label="품종" name="breed" required placeholder="품종을 입력해 주세요." />
            <div className="shelter-form__row">
              <TextField label="나이" name="age" placeholder="예: 2살 또는 생후 3개월" />
              <TextField label="체중" name="weight" placeholder="예: 3kg" />
            </div>
            <SelectField label="성별" name="sex" required defaultValue="">
              <option value="" disabled>
                선택
              </option>
              <option value="female">암컷</option>
              <option value="male">수컷</option>
              <option value="unknown">모름</option>
            </SelectField>
            <ChoiceGroup
              label="중성화 여부"
              name="neutered"
              value={neutered}
              onChange={setNeutered}
              required
              options={[
                { value: 'yes', label: '완료' },
                { value: 'no', label: '미완료' },
                { value: 'unknown', label: '모름' },
              ]}
            />
          </FormSection>
          <FormSection title="추가정보">
            <TextAreaField
              label="특징"
              name="description"
              placeholder="예: 오른쪽 귀 끝이 갈색이며, 사람을 잘 따라요."
            />
            <TextField
              label="건강검진 특이사항"
              name="health"
              placeholder="특이사항을 입력해 주세요."
            />
            <TextField
              label="예방접종 상태"
              name="vaccination"
              placeholder="예: 종합백신 1차 완료"
            />
          </FormSection>
          <div className="shelter-form__actions">
            <Button
              type="reset"
              variant="secondary"
              onClick={() => {
                setFiles([]);
                setNeutered('');
                setMessage('입력 내용을 초기화했습니다.');
              }}
            >
              초기화
            </Button>
            <Button type="submit" variant="approval">
              입력 확인
            </Button>
          </div>
        </form>
      ) : (
        <ListPanel
          total={visible.length}
          unit="마리"
          filters={
            <>
              <FilterSelect
                label="상태"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                <option value="">상태 전체</option>
                <option value="protected">보호 중</option>
                <option value="closed">보호 종료</option>
                <option value="returned">반환 완료</option>
                <option value="adopted">입양 완료</option>
              </FilterSelect>
              <FilterSelect
                label="입양 가능 여부"
                value={adoptable}
                onChange={(event) => setAdoptable(event.target.value)}
              >
                <option value="">입양 가능 여부 전체</option>
                <option value="true">입양 가능</option>
                <option value="false">입양 불가</option>
              </FilterSelect>
            </>
          }
          actions={
            <>
              <Button
                onClick={() => {
                  setStatus('');
                  setAdoptable('');
                  setSort('latest');
                }}
              >
                필터 초기화
              </Button>
              <Link
                to="/dev/shelter?view=form"
                className="ui-button ui-button--approval ui-button--large shelter-preview-link"
              >
                동물 등록하기
              </Link>
            </>
          }
          sort={
            <FilterSelect
              label="정렬"
              value={sort}
              onChange={(event) => setSort(event.target.value)}
            >
              <option value="latest">등록일 최신순</option>
              <option value="oldest">등록일 오래된순</option>
            </FilterSelect>
          }
        >
          {sorted.map((item) => (
            <AnimalCard
              key={item.id}
              name="한국고양이"
              imageSrc={animalImage}
              imageAlt="Figma 원본 예시 동물 사진"
              status={item.status}
              adoptable={item.adoptable}
              location="구미시 인동동 123-123"
              period="2026-10-19 ~ 2026-11-17"
              summary="0살 | 성별 | 털색 정보 | 몸무게"
              description="상세정보 한줄정도만 보이게끔"
              onClick={() => setMessage(`예시 동물 ${item.id} 선택. 상세 화면 연결 전입니다.`)}
            />
          ))}
        </ListPanel>
      )}
      <output className="shelter-preview-notice" aria-live="polite">
        {message}
      </output>
    </ShelterLayout>
  );
}
