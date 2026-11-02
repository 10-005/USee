import { useState, type FormEvent } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { DemoPage, InfoRows, MissingRecord } from '@/features/shelter-demo/Page';
import { useShelterDemo, type DemoAnimal, type Rescue } from '@/features/shelter-demo/store';
import { FormSection } from '@/shared/ui/FormSection';
import { TextField } from '@/shared/ui/TextField';
import { TextAreaField } from '@/shared/ui/TextAreaField';
import { SelectField } from '@/shared/ui/SelectField';
import { CalendarField } from '@/shared/ui/CalendarField';
import { ChoiceGroup } from '@/shared/ui/ChoiceGroup';
import { ImageUpload } from '@/shared/ui/ImageUpload';
import { Button } from '@/shared/ui/Button';
import { Modal } from '@/shared/ui/Modal';
import { animalStatuses } from './animal-statuses';
import { breedColors } from './breed-options';
import pencil from '@/shared/assets/figma/edit-pencil.svg';

const readImage = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error('사진을 읽을 수 없습니다.'));
    reader.readAsDataURL(file);
  });
export function AnimalFormPage() {
  const { id } = useParams();
  const animal = useShelterDemo((s) => s.animals.find((a) => a.id === id));
  const [params] = useSearchParams();
  const rescueId = id ? animal?.rescueId : params.get('rescueId');
  const rescue = useShelterDemo((s) => s.rescues.find((r) => r.id === rescueId));
  if (id && !animal) return <MissingRecord back="/animals" />;
  if (!id && rescueId && rescue?.status !== '완료') return <MissingRecord back="/rescues" />;
  return <AnimalForm key={id ?? rescueId ?? 'new'} animal={animal} rescue={rescue} />;
}
function AnimalForm({ animal, rescue }: { animal?: DemoAnimal; rescue?: Rescue }) {
  const navigate = useNavigate();
  const back = animal ? `/animals/${animal.id}` : rescue ? `/rescues/${rescue.id}` : '/animals';
  const save = useShelterDemo((s) => s.saveAnimal);
  const [files, setFiles] = useState<File[]>([]);
  const [existing, setExisting] = useState<string[]>(
    animal ? (animal.images ?? [animal.imageSrc]) : [],
  );
  const [species, setSpecies] = useState<DemoAnimal['species'] | ''>(animal?.species ?? '');
  const [breed, setBreed] = useState(animal?.name ?? '');
  const [color, setColor] = useState(animal?.color ?? '');
  const breeds = species ? Object.keys(breedColors[species]) : [];
  if (animal?.species === species && !breeds.includes(animal.name)) breeds.push(animal.name);
  const colors =
    species && breed ? [...(breedColors[species][breed] ?? breedColors[species]['기타']!)] : [];
  if (
    animal?.species === species &&
    animal.name === breed &&
    animal.color &&
    !colors.includes(animal.color)
  )
    colors.push(animal.color);
  const [sex, setSex] = useState<DemoAnimal['sex'] | ''>(animal?.sex ?? '');
  const [neutered, setNeutered] = useState<DemoAnimal['neutered'] | ''>(animal?.neutered ?? '');
  const [status, setStatus] = useState<DemoAnimal['status'] | ''>(animal?.status ?? '');
  const [adoptable, setAdoptable] = useState(animal ? (animal.adoptable ? 'yes' : 'no') : '');
  const [start, setStart] = useState(animal?.period.split(' ~ ')[0] ?? '');
  const savedReason = animal?.status === 'closed' ? animal.closingReason : '';
  const [reasonType, setReasonType] = useState<DemoAnimal['closingReasonType']>(
    animal?.closingReasonType ??
      (savedReason === '자연사' || savedReason === '안락사'
        ? savedReason
        : savedReason
          ? '직접 입력'
          : undefined),
  );
  const [customReason, setCustomReason] = useState(savedReason ?? '');
  const [adoptionNote, setAdoptionNote] = useState(
    animal?.status === 'adopted' ? (animal.closingReason ?? '') : '',
  );
  const [guardianName, setGuardianName] = useState(animal?.guardianName ?? '');
  const [guardianPhone, setGuardianPhone] = useState(animal?.guardianPhone ?? '');
  const closed = !!status && status !== 'protected';
  const [submitted, setSubmitted] = useState(false);
  const errors = {
    photos: existing.length + files.length ? '' : '최소 1장의 사진을 첨부해 주세요.',
    species: species ? '' : '동물 종류를 선택해 주세요.',
    breed: breed ? '' : '품종을 선택해 주세요.',
    color: color ? '' : '색상을 선택해 주세요.',
    sex: sex ? '' : '성별을 선택해 주세요.',
    neutered: neutered ? '' : '중성화 여부를 선택해 주세요.',
    start: start ? '' : '공고 시작일을 선택해 주세요.',
    status: status ? '' : '상태를 선택해 주세요.',
    adoptable: closed || adoptable ? '' : '입양 가능 여부를 선택해 주세요.',
    reasonType: status === 'closed' && !reasonType ? '보호 종료 사유를 선택해 주세요.' : '',
    customReason:
      status === 'closed' && reasonType === '직접 입력' && !customReason.trim()
        ? '보호 종료 사유를 입력해 주세요.'
        : '',
    adoptionNote: status === 'adopted' && !adoptionNote.trim() ? '처리 사유를 입력해 주세요.' : '',
    guardianName:
      status === 'returned' && !guardianName.trim() ? '보호자 이름을 입력해 주세요.' : '',
    guardianPhone:
      status === 'returned' && !guardianPhone.trim() ? '보호자 연락처를 입력해 주세요.' : '',
  };
  const fieldError = (key: keyof typeof errors) => (submitted ? errors[key] : '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [cancel, setCancel] = useState(false);
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const value = (key: string) => String(data.get(key) ?? '').trim();
    setSubmitted(true);
    if (Object.values(errors).some(Boolean) || !species || !sex || !status || !neutered) {
      const form = e.currentTarget;
      requestAnimationFrame(() => {
        const invalid = form.querySelector<HTMLElement>('[aria-invalid="true"]');
        invalid?.scrollIntoView?.({ block: 'center' });
        invalid?.focus({ preventScroll: true });
      });
      return;
    }
    if (!e.currentTarget.reportValidity()) return;
    if (value('end') < value('start')) {
      setError('공고 종료일은 시작일 이후여야 합니다.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const images = [...existing, ...(await Promise.all(files.map(readImage)))];
      const next: DemoAnimal = {
        ...animal,
        rescueId: rescue?.id ?? animal?.rescueId,
        id: animal?.id ?? `animal-${crypto.randomUUID().slice(0, 8)}`,
        registeredAt: animal?.registeredAt ?? '2026-11-01',
        name: breed,
        species,
        sex,
        neutered,
        status,
        adoptable: status === 'protected' && adoptable === 'yes',
        imageSrc: images[0]!,
        images,
        imageAlt: '보호동물 예시 사진',
        registration: value('registration'),
        chip: value('chip'),
        color: value('color'),
        birth: value('birth'),
        weight: value('weight'),
        summary: `${value('birth') || '나이 미상'} | ${{ female: '암컷', male: '수컷', unknown: '모름' }[sex]} | ${value('color')} | ${value('weight') || '미상'}kg`,
        location: value('location') || animal?.location || '미등록',
        period: `${value('start')} ~ ${value('end')}`,
        description: value('description'),
        health: value('health'),
        vaccination: value('vaccination'),
        closingReasonType: status === 'closed' ? reasonType : undefined,
        closingReason:
          status === 'closed'
            ? reasonType === '직접 입력'
              ? customReason.trim()
              : reasonType
            : value('closingReason'),
        guardianName: status === 'returned' ? guardianName.trim() : undefined,
        guardianPhone: status === 'returned' ? guardianPhone.trim() : undefined,
      };
      save(next);
      navigate(`/animals/${next.id}`);
    } catch {
      setError('사진을 읽지 못했습니다. 다시 선택해 주세요.');
    } finally {
      setBusy(false);
    }
  };
  return (
    <DemoPage title={animal ? '보호동물 수정' : '보호동물 등록'} narrow back={back}>
      <form className="work-form animal-form" noValidate onSubmit={submit}>
        {rescue && (
          <FormSection title="연결된 구조 정보">
            <InfoRows
              rows={[
                ['구조번호', rescue.id],
                ['구조 장소', rescue.location],
                ['구조 일시', rescue.date.replace('T', ' ')],
              ]}
            />
          </FormSection>
        )}
        <FormSection
          title="동물 사진 *"
          description={`사진 ${existing.length + files.length}/3장 · 첫 번째 사진이 대표 사진이에요. 최소 1장이 필요해요.`}
        >
          <ImageUpload
            requiredError={fieldError('photos')}
            hideLabel
            files={files}
            onChange={setFiles}
            existingCount={existing.length}
          >
            {existing.map((src, i) => (
              <div className="image-upload__preview" key={src}>
                <img src={src} alt={`기존 사진 ${i + 1}`} />
                {i === 0 && <span className="animal-form__cover">대표</span>}
                <button
                  type="button"
                  aria-label={`기존 사진 ${i + 1} 삭제`}
                  onClick={() => setExisting((old) => old.filter((_, index) => index !== i))}
                >
                  삭제
                </button>
              </div>
            ))}
          </ImageUpload>
        </FormSection>
        <FormSection title="기본정보">
          <TextField
            label="동물등록번호"
            name="registration"
            defaultValue={animal?.registration}
            placeholder="동물등록번호를 입력해 주세요."
          />
          <TextField
            label="마이크로칩번호"
            name="chip"
            defaultValue={animal?.chip}
            placeholder="마이크로칩 번호를 입력해 주세요."
          />
          <SelectField
            className="shelter-filter--menu"
            label="동물 종류"
            error={fieldError('species')}
            required
            value={species}
            onChange={(e) => {
              setSpecies(e.target.value as DemoAnimal['species']);
              setBreed('');
              setColor('');
            }}
          >
            <option value="" disabled>
              종류 선택
            </option>
            <option value="dog">개</option>
            <option value="cat">고양이</option>
            <option value="other">기타</option>
          </SelectField>
          <SelectField
            className="shelter-filter--menu"
            label="품종"
            error={fieldError('breed')}
            required
            name="breed"
            disabled={!species}
            value={breed}
            onChange={(e) => {
              setBreed(e.target.value);
              setColor('');
            }}
          >
            <option value="" disabled>
              {species ? '품종 선택' : '동물 종류를 먼저 선택해 주세요'}
            </option>
            {breeds.map((name) => (
              <option key={name}>{name}</option>
            ))}
          </SelectField>
          <SelectField
            className="shelter-filter--menu"
            label="색상"
            error={fieldError('color')}
            required
            name="color"
            disabled={!breed}
            value={color}
            onChange={(e) => setColor(e.target.value)}
          >
            <option value="" disabled>
              {breed ? '색상 선택' : '품종을 먼저 선택해 주세요'}
            </option>
            {colors.map((v) => (
              <option key={v}>{v}</option>
            ))}
          </SelectField>
          <div className="form-columns">
            <TextField
              label="나이"
              name="birth"
              defaultValue={animal?.birth}
              placeholder="예: 2살 또는 생후 3개월"
            />
            <TextField
              label="체중 (kg)"
              name="weight"
              type="number"
              min="0.1"
              step="0.1"
              placeholder="예: 3kg"
              defaultValue={animal?.weight}
            />
          </div>
          <SelectField
            className="shelter-filter--menu"
            label="성별"
            error={fieldError('sex')}
            name="sex"
            required
            value={sex}
            onChange={(e) => setSex(e.target.value as DemoAnimal['sex'])}
          >
            <option value="" disabled>
              성별 선택
            </option>
            <option value="female">암컷</option>
            <option value="male">수컷</option>
            <option value="unknown">모름</option>
          </SelectField>
          <ChoiceGroup
            label="중성화 여부"
            error={fieldError('neutered')}
            required
            value={neutered}
            onChange={(v) => setNeutered(v as DemoAnimal['neutered'])}
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
            placeholder="예: 오른쪽 귀 끝이 갈색이며, 사람을 잘 따르고 큰 소리에 민감해요."
            defaultValue={animal?.description}
            maxLength={2000}
          />
          <TextField
            label="건강검진 특이사항"
            name="health"
            defaultValue={animal?.health}
            placeholder="예: 오른쪽 뒷다리에 상처가 있어 치료 중이며, 피부 상태 관찰이 필요해요."
          />
          <TextField
            label="예방접종 상태"
            name="vaccination"
            defaultValue={animal?.vaccination}
            placeholder="예: 종합백신 1차 완료(2026.10.20), 광견병 미접종"
          />
          <TextField
            label="발생장소"
            name="location"
            defaultValue={animal?.location ?? rescue?.location}
            placeholder="예: 구미시 인동동 송산도서관 인근"
          />
        </FormSection>
        <FormSection title="공고 및 입양 정보">
          <CalendarField
            label="공고기간"
            error={fieldError('start')}
            name="start"
            placeholder="공고 시작일을 선택해 주세요"
            initialValue={animal?.period.split(' ~ ')[0] ?? ''}
            onConfirm={setStart}
            range={{ days: 10, endName: 'end', initialEnd: animal?.period.split(' ~ ')[1] }}
          />
          <ChoiceGroup
            label="상태"
            error={fieldError('status')}
            required
            value={status}
            onChange={(v) => setStatus(v as DemoAnimal['status'])}
            options={Object.entries(animalStatuses).map(([value, label]) => ({ value, label }))}
          />
          {status === 'closed' && (
            <>
              <ChoiceGroup
                label="보호 종료 사유"
                required
                value={reasonType ?? ''}
                error={fieldError('reasonType')}
                onChange={(v) => setReasonType(v as DemoAnimal['closingReasonType'])}
                options={['자연사', '안락사', '직접 입력'].map((value) => ({
                  value,
                  label: value,
                }))}
              />
              {reasonType === '직접 입력' && (
                <TextAreaField
                  label="보호 종료 사유 직접 입력"
                  required
                  error={fieldError('customReason')}
                  placeholder="보호를 종료한 사유를 입력해 주세요."
                  value={customReason}
                  onChange={(e) => setCustomReason(e.target.value)}
                />
              )}
            </>
          )}
          {status === 'returned' && (
            <>
              <TextField
                label="보호자 이름"
                required
                error={fieldError('guardianName')}
                placeholder="보호자 이름을 입력해 주세요."
                value={guardianName}
                onChange={(e) => setGuardianName(e.target.value)}
              />
              <TextField
                label="보호자 연락처"
                required
                type="tel"
                error={fieldError('guardianPhone')}
                placeholder="예: 010-1234-5678"
                value={guardianPhone}
                onChange={(e) => setGuardianPhone(e.target.value)}
              />
              {animal?.status === 'returned' && animal.closingReason && (
                <TextAreaField
                  label="기존 처리 비고"
                  name="closingReason"
                  defaultValue={animal.closingReason}
                />
              )}
            </>
          )}
          {status === 'adopted' && (
            <TextAreaField
              label="처리 사유 및 비고"
              error={fieldError('adoptionNote')}
              name="closingReason"
              placeholder="상태를 변경한 사유와 참고할 내용을 입력해 주세요."
              required
              value={adoptionNote}
              onChange={(e) => setAdoptionNote(e.target.value)}
            />
          )}
          <ChoiceGroup
            label="입양 가능 여부"
            error={fieldError('adoptable')}
            required
            value={closed ? 'no' : adoptable}
            onChange={setAdoptable}
            disabled={closed}
            options={[
              { value: 'yes', label: '입양 가능' },
              { value: 'no', label: '입양 불가' },
            ]}
          />
        </FormSection>
        {error && (
          <p role="alert" className="form-error">
            {error}
          </p>
        )}
        <div className="page-actions">
          <Button variant="secondary" disabled={busy} onClick={() => setCancel(true)}>
            취소
          </Button>
          <Button type="submit" variant="approval" disabled={busy}>
            <img src={pencil} alt="" />
            {busy ? '저장 중…' : animal ? '수정하기' : '등록하기'}
          </Button>
        </div>
      </form>
      <Modal
        open={cancel}
        title="작성을 취소할까요?"
        description="저장하지 않은 내용은 사라집니다."
        onClose={() => setCancel(false)}
        actions={
          <>
            <Button variant="secondary" onClick={() => setCancel(false)}>
              계속 작성
            </Button>
            <Button onClick={() => navigate(back)}>작성 취소</Button>
          </>
        }
      />
    </DemoPage>
  );
}
