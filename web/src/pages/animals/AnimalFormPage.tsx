import { DeleteButton } from '@/shared/ui/DeleteButton';
import { AnimalPhoto } from '@/shared/ui/AnimalPhoto';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { demoMode } from '@/shared/config/demo';
import {
  animalDetailOptions,
  detailRecord,
  combinationsOptions,
  linkedRescueOptions,
  removeAnimal,
  type AnimalDetail,
  type Combination,
} from '@/entities/shelter/data/animals';
import { createImageUploader } from '@/shared/api/image-upload';
import { saveLiveAnimal } from '@/entities/shelter/data/animal-save';
import { LoadStateMessage } from '@/shared/ui/LoadState';
import { useRef, useState, type FormEvent } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { WorkPage, MissingRecord } from '@/shared/ui/WorkPage';
import { useAnimal, useRescue, useSaveAnimal, useDeleteAnimal } from '@/entities/shelter/data';
import type { ShelterAnimalRecord, Rescue } from '@/entities/shelter/model';
import { FormSection } from '@/shared/ui/FormSection';
import { TextField } from '@/shared/ui/TextField';
import { TextAreaField } from '@/shared/ui/TextAreaField';
import { SelectField } from '@/shared/ui/SelectField';
import { CalendarField } from '@/shared/ui/CalendarField';
import { ChoiceGroup } from '@/shared/ui/ChoiceGroup';
import { ImageUpload } from '@/shared/ui/ImageUpload';
import { Button } from '@/shared/ui/Button';
import { LinkedRescueCard } from '@/pages/animals/LinkedRescueCard';
import { Modal } from '@/shared/ui/Modal';
import { animalStatuses } from './animal-statuses';
import { breedsFor, catalogCombinations } from './breed-options';
import { localDate } from '@/shared/lib/date';
import { readImageDataUrl } from '@/shared/lib/image';
import pencil from '@/shared/assets/icons/edit-pencil.svg';

function optionsWithOtherFirst(names: string[]) {
  const options = new Set(names);
  if (options.delete('기타')) return ['기타', ...options];
  return [...options];
}

const koreanOrder = new Intl.Collator('ko');

export function AnimalFormPage() {
  const { id } = useParams();
  return demoMode ? <DemoForm id={id} /> : <LiveForm key={id ?? 'new'} id={id} />;
}
function DemoForm({ id }: { id?: string }) {
  const animal = useAnimal(id);
  const [params] = useSearchParams();
  const rescueId = id ? animal?.rescueId : params.get('rescueId');
  const rescue = useRescue(rescueId ?? undefined);
  if (id && !animal) return <MissingRecord back="/animals" />;
  if (!id && rescueId && rescue?.status !== '완료') return <MissingRecord back="/rescues" />;
  return <AnimalForm key={id ?? rescueId ?? 'new'} animal={animal} rescue={rescue} />;
}
function LiveForm({ id }: { id?: string }) {
  const [params] = useSearchParams();
  const rescueId = id ? '' : (params.get('rescueId') ?? '');
  const catalog = useQuery(combinationsOptions);
  const detail = useQuery({ ...animalDetailOptions(id ?? ''), enabled: !!id });
  const rescue = useQuery({ ...linkedRescueOptions(rescueId), enabled: !!rescueId });
  const error = catalog.error || detail.error || rescue.error;
  if (error || !catalog.data || (id && !detail.data) || (rescueId && !rescue.data))
    return (
      <WorkPage title={id ? '보호동물 수정' : '보호동물 등록'} back="/animals">
        <LoadStateMessage
          state={
            error
              ? {
                  status: 'error',
                  message: error.message,
                  retry: () => {
                    void catalog.refetch();
                    if (id) void detail.refetch();
                    if (rescueId) void rescue.refetch();
                  },
                }
              : { status: 'loading' }
          }
        >
          {null}
        </LoadStateMessage>
      </WorkPage>
    );
  if (rescue.data && rescue.data.status !== 'DONE')
    return (
      <WorkPage title="보호동물 등록" back="/animals">
        <p>구조가 완료된 기록만 연결할 수 있습니다.</p>
      </WorkPage>
    );
  return (
    <LiveEditor
      key={id ?? rescueId}
      detail={detail.data}
      catalog={catalog.data}
      rescueId={rescueId}
      rescueAddress={rescue.data?.rescueAddress}
    />
  );
}
function LiveEditor({
  detail,
  catalog,
  rescueId,
  rescueAddress,
}: {
  detail?: AnimalDetail;
  catalog: Combination[];
  rescueId: string;
  rescueAddress?: string;
}) {
  const cache = useQueryClient();
  const [initial] = useState(detail);
  const baseline = useRef(initial);
  const [photos] = useState(createImageUploader);
  async function invalidate() {
    await Promise.all([
      cache.invalidateQueries({ queryKey: ['animals'], refetchType: 'none' }),
      cache.invalidateQueries({ queryKey: ['rescues'] }),
      cache.invalidateQueries({ queryKey: ['adoptions'], refetchType: 'none' }),
    ]);
  }
  return (
    <AnimalForm
      animal={initial ? detailRecord(initial) : undefined}
      live={{
        catalog,
        classification: initial?.classification,
        rescueId,
        rescueAddress,
        photos,
        save: async (next, combination, existing, files) => {
          const retained = existing.map((url) => {
            const image = initial?.images.find((i) => i.imageUrl === url);
            if (!image) throw new Error('기존 사진을 찾을 수 없습니다.');
            return image.imageSeq;
          });
          const ids = [...retained];
          for (const file of files) ids.push(await photos.confirm(file));
          const previous = baseline.current;
          try {
            return await saveLiveAnimal(
              next,
              combination,
              ids,
              previous ? { animal: detailRecord(previous), detail: previous } : undefined,
              (updated) => {
                baseline.current = updated;
              },
            );
          } finally {
            await invalidate();
          }
        },
        remove: async (id) => {
          await removeAnimal(id);
          await cache.invalidateQueries({
            queryKey: ['animals', 'detail', id],
            refetchType: 'none',
          });
          await Promise.all([
            cache.invalidateQueries({ queryKey: ['animals', 'list'] }),
            cache.invalidateQueries({ queryKey: ['rescues'] }),
            cache.invalidateQueries({ queryKey: ['adoptions'] }),
          ]);
        },
      }}
    />
  );
}
type LiveFormData = {
  catalog: Combination[];
  classification?: Combination | null;
  rescueId: string;
  rescueAddress?: string;
  photos: ReturnType<typeof createImageUploader>;
  save: (
    animal: ShelterAnimalRecord,
    combination: number,
    existing: string[],
    files: File[],
  ) => Promise<string>;
  remove: (id: string) => Promise<void>;
};
function AnimalForm({
  animal,
  rescue,
  live,
}: {
  animal?: ShelterAnimalRecord;
  rescue?: Rescue;
  live?: LiveFormData;
}) {
  const navigate = useNavigate();
  const back = animal ? `/animals/${animal.id}` : rescue ? `/rescues/${rescue.id}` : '/animals';
  const save = useSaveAnimal();
  const deleteAnimal = useDeleteAnimal();
  const [files, setFiles] = useState<File[]>([]);
  const [existing, setExisting] = useState<string[]>(
    animal ? (animal.images ?? [animal.imageSrc]) : [],
  );
  const [species, setSpecies] = useState<ShelterAnimalRecord['species'] | ''>(
    animal?.species ?? '',
  );
  const breeds = breedsFor(species);
  const liveOptions =
    live && catalogCombinations(species, live.catalog, live.classification?.combinationSeq);
  const [breed, setBreed] = useState(() => {
    if (live)
      return (
        liveOptions?.find((row) => row.combinationSeq === live.classification?.combinationSeq)
          ?.label ?? ''
      );
    if (!animal) return '';
    return (
      breeds.find(
        (item) =>
          item.name === animal.name ||
          item.originalBreedNames?.includes(animal.name) ||
          (animal.breedCode && item.originalBreedCodes?.includes(animal.breedCode)),
      )?.name ?? animal.name
    );
  });
  const [color, setColor] = useState(animal?.color ?? '');
  const sourceBreed = breeds.find((item) => item.name === breed);
  const colorNames = sourceBreed?.colors.map((c) => c.name) ?? [];
  if (color && animal?.color === color && !colorNames.includes(color)) colorNames.unshift(color);
  const breedOptions = new Set(breeds.map((item) => item.name));
  if (breed) breedOptions.add(breed);
  const sortedBreeds = [...breedOptions].sort((a, b) =>
    a === b ? 0 : a === '기타' ? -1 : b === '기타' ? 1 : koreanOrder.compare(a, b),
  );
  const availableBreeds = new Set(
    liveOptions
      ? liveOptions.map((row) => row.label)
      : breeds.filter((item) => item.colors.length).map((item) => item.name),
  );
  const liveColors = liveOptions?.filter((c) => c.label === breed);
  const colors = optionsWithOtherFirst(
    liveColors ? liveColors.map((c) => c.colorName) : colorNames,
  );
  const combination =
    liveColors?.find(
      (c) => c.colorName === color && c.combinationSeq === live?.classification?.combinationSeq,
    ) ?? liveColors?.find((c) => c.colorName === color);
  const [sex, setSex] = useState<ShelterAnimalRecord['sex'] | ''>(animal?.sex ?? '');
  const [neutered, setNeutered] = useState<ShelterAnimalRecord['neutered'] | ''>(
    animal?.neutered ?? '',
  );
  const [status, setStatus] = useState<ShelterAnimalRecord['status']>(
    animal?.status ?? 'protected',
  );
  const [adoptable, setAdoptable] = useState(animal ? (animal.adoptable ? 'yes' : 'no') : '');
  const [start, setStart] = useState(animal?.period.split(' ~ ')[0] ?? '');
  const savedReason = animal?.status === 'closed' ? animal.closingReason : '';
  const [reasonType, setReasonType] = useState<ShelterAnimalRecord['closingReasonType']>(
    (live && savedReason === '기타' ? '직접 입력' : animal?.closingReasonType) ??
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
      !live && status === 'closed' && reasonType === '직접 입력' && !customReason.trim()
        ? '보호 종료 사유를 입력해 주세요.'
        : '',
    adoptionNote:
      status === 'adopted' && (!live || animal?.status !== 'adopted') && !adoptionNote.trim()
        ? '처리 사유를 입력해 주세요.'
        : '',
    guardianName:
      status === 'returned' && !guardianName.trim() ? '보호자 이름을 입력해 주세요.' : '',
    guardianPhone:
      status === 'returned' && !guardianPhone.trim() ? '보호자 연락처를 입력해 주세요.' : '',
  };
  const fieldError = (key: keyof typeof errors) => (submitted ? errors[key] : '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [cancel, setCancel] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (busy) return;
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
      const images = live
        ? existing
        : [...existing, ...(await Promise.all(files.map(readImageDataUrl)))];
      const next: ShelterAnimalRecord = {
        ...animal,
        rescueId: rescue?.id ?? animal?.rescueId ?? (live?.rescueId || undefined),
        id: animal?.id ?? `animal-${crypto.randomUUID().slice(0, 8)}`,
        registeredAt: animal?.registeredAt ?? localDate(),
        name: breed,
        breedCode: sourceBreed?.code ?? undefined,
        species,
        sex,
        neutered,
        status,
        adoptable: status === 'protected' && adoptable === 'yes',
        imageSrc: images[0] ?? '',
        images,
        imageAlt: '보호동물 예시 사진',
        registration: value('registration'),
        chip: value('chip'),
        color: value('color'),
        birth: value('birth'),
        weight: value('weight'),
        summary: `${value('birth') || '나이 미상'} | ${{ female: '암컷', male: '수컷', unknown: '모름' }[sex]} | ${value('color')} | ${value('weight') || '미상'}kg`,
        location: animal?.location || rescue?.location || '미등록',
        period: `${value('start')} ~ ${value('end')}`,
        description: value('description'),
        health: value('health'),
        vaccination: value('vaccination'),
        closingReasonType: status === 'closed' ? reasonType : undefined,
        closingReason:
          status === 'closed'
            ? reasonType === '직접 입력'
              ? live
                ? '기타'
                : customReason.trim()
              : reasonType
            : value('closingReason'),
        guardianName: status === 'returned' ? guardianName.trim() : undefined,
        guardianPhone: status === 'returned' ? guardianPhone.trim() : undefined,
      };
      if (live && !combination) throw new Error('종·품종·색상 조합을 다시 선택해 주세요.');
      const savedId = live
        ? await live.save(next, combination!.combinationSeq, existing, files)
        : (await save(next)).id;
      navigate(`/animals/${savedId}`);
    } catch (cause) {
      setError(
        live && cause instanceof Error
          ? cause.message
          : '저장하지 못했습니다. 사진과 입력 내용을 확인한 후 다시 시도해 주세요.',
      );
    } finally {
      setBusy(false);
    }
  };
  return (
    <WorkPage title={animal ? '보호동물 수정' : '보호동물 등록'} narrow back={back}>
      <form className="work-form animal-form" noValidate onSubmit={submit}>
        {live?.rescueAddress && (
          <FormSection title="연결된 구조 신고">
            <p>{live.rescueAddress}</p>
          </FormSection>
        )}
        {rescue && (
          <FormSection title="연결된 구조 신고">
            <LinkedRescueCard rescue={rescue} />
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
            onChange={(selected) => {
              setFiles(selected);
              if (live)
                for (const file of selected.filter((f) => !files.includes(f)))
                  void live.photos.prepare(file).catch(() => {});
            }}
            existingCount={existing.length}
          >
            {existing.map((src, i) => (
              <div className="image-upload__preview" key={src}>
                <AnimalPhoto src={src} alt={`기존 사진 ${i + 1}`} />
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
            maxLength={live ? 20 : undefined}
            defaultValue={animal?.registration}
            placeholder="동물등록번호를 입력해 주세요."
          />
          <TextField
            label="마이크로칩번호"
            name="chip"
            maxLength={live ? 30 : undefined}
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
              setSpecies(e.target.value as ShelterAnimalRecord['species']);
              setBreed('');
              setColor('');
            }}
          >
            <option value="" disabled>
              종류 선택
            </option>
            <option value="other">기타</option>
            <option value="dog">개</option>
            <option value="cat">고양이</option>
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
            {sortedBreeds.map((name) => (
              <option
                key={name}
                value={name}
                disabled={!availableBreeds.has(name) && name !== breed}
              >
                {name}
              </option>
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
              maxLength={live ? 30 : undefined}
              defaultValue={animal?.birth}
              placeholder="예: 2살 또는 생후 3개월"
            />
            <TextField
              label={live ? '체중' : '체중 (kg)'}
              name="weight"
              maxLength={live ? 30 : undefined}
              type={live ? 'text' : 'number'}
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
            onChange={(e) => setSex(e.target.value as ShelterAnimalRecord['sex'])}
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
            onChange={(v) => setNeutered(v as ShelterAnimalRecord['neutered'])}
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
          {animal && (
            <ChoiceGroup
              label="상태"
              error={fieldError('status')}
              required
              value={status}
              onChange={(v) => setStatus(v as ShelterAnimalRecord['status'])}
              options={Object.entries(animalStatuses).map(([value, label]) => ({ value, label }))}
            />
          )}
          {status === 'closed' && (
            <>
              <ChoiceGroup
                label="보호 종료 사유"
                required
                value={reasonType ?? ''}
                error={fieldError('reasonType')}
                onChange={(v) => setReasonType(v as ShelterAnimalRecord['closingReasonType'])}
                options={['자연사', '안락사', '직접 입력'].map((value) => ({
                  value,
                  label: live && value === '직접 입력' ? '기타' : value,
                }))}
              />
              {!live && reasonType === '직접 입력' && (
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
              maxLength={live ? 200 : undefined}
              placeholder="상태를 변경한 사유와 참고할 내용을 입력해 주세요."
              required={!live || animal?.status !== 'adopted' || !!animal.closingReason}
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
          {animal && (
            <DeleteButton
              disabled={busy}
              onClick={() => {
                setDeleteError('');
                setConfirmDelete(true);
              }}
            />
          )}
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
        open={confirmDelete}
        title="보호동물을 삭제할까요?"
        description="삭제하면 보호동물 목록에서 더 이상 볼 수 없으며, 복구할 수 없습니다."
        onClose={() => {
          if (!busy) setConfirmDelete(false);
        }}
        actions={
          <>
            <Button variant="secondary" disabled={busy} onClick={() => setConfirmDelete(false)}>
              취소
            </Button>
            <Button
              className="work-delete-confirm"
              disabled={busy}
              onClick={async () => {
                if (!animal || busy) return;
                setBusy(true);
                setDeleteError('');
                try {
                  if (live) await live.remove(animal.id);
                  else await deleteAnimal(animal.id);
                  navigate('/animals', { replace: true });
                } catch (cause) {
                  setDeleteError(
                    live && cause instanceof Error
                      ? cause.message
                      : '삭제하지 못했습니다. 다시 시도해 주세요.',
                  );
                  setBusy(false);
                }
              }}
            >
              {busy ? '삭제 중…' : '삭제하기'}
            </Button>
          </>
        }
      >
        {deleteError && (
          <p role="alert" className="form-error">
            {deleteError}
          </p>
        )}
      </Modal>
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
    </WorkPage>
  );
}
