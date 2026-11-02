import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import plus from '@/shared/assets/figma/upload-plus.svg';
import './shelter-ui.css';

function FilePreview({ file }: { file: File }) {
  const imageRef = useRef<HTMLImageElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(file);
    if (imageRef.current) imageRef.current.src = url;
    return () => URL.revokeObjectURL(url);
  }, [file]);
  return <img ref={imageRef} alt={file.name} />;
}

export function ImageUpload({
  files,
  children,
  existingCount = 0,
  requiredError,
  onChange,
  label = '동물 사진',
  hideLabel = false,
  maxFiles = 3,
  maxBytes = 10 * 1024 * 1024,
}: {
  files: File[];
  children?: ReactNode;
  existingCount?: number;
  requiredError?: string;
  onChange: (files: File[]) => void;
  label?: string;
  hideLabel?: boolean;
  maxFiles?: number;
  maxBytes?: number;
}) {
  const [error, setError] = useState('');
  const inputId = useId();
  const photoCount = existingCount + files.length;
  return (
    <div className="image-upload">
      <label className={hideLabel ? 'shelter-sr-only' : 'ui-field__label'} htmlFor={inputId}>
        {label}
      </label>
      <div className="image-upload__items">
        {children}
        {files.map((file, index) => (
          <div className="image-upload__preview" key={`${file.name}-${file.lastModified}-${index}`}>
            <FilePreview file={file} />
            {existingCount === 0 && index === 0 && <span className="animal-form__cover">대표</span>}
            <button
              type="button"
              aria-label={`${file.name} 삭제`}
              onClick={() => {
                onChange(files.filter((_, i) => i !== index));
                setError('');
              }}
            >
              삭제
            </button>
          </div>
        ))}
        <label className="image-upload__add">
          <img src={plus} alt="" />
          <span className="shelter-sr-only">사진 선택</span>
          <input
            id={inputId}
            type="file"
            multiple
            accept="image/jpeg,image/png"
            disabled={photoCount >= maxFiles}
            aria-invalid={Boolean(error || requiredError) || undefined}
            aria-describedby={`${inputId}-hint ${inputId}-error`}
            onChange={(event) => {
              const selected = Array.from(event.target.files ?? []);
              event.target.value = '';
              if (!selected.length) return;
              if (photoCount + selected.length > maxFiles) {
                setError(`최대 ${maxFiles}장까지 선택할 수 있습니다.`);
                return;
              }
              if (
                selected.some(
                  (file) =>
                    !['image/jpeg', 'image/png'].includes(file.type) || file.size > maxBytes,
                )
              ) {
                setError('파일 형식과 크기를 확인해 주세요.');
                return;
              }
              setError('');
              onChange([...files, ...selected]);
            }}
          />
        </label>
      </div>
      <p id={`${inputId}-hint`}>
        최대 {maxFiles}장 · JPG / JPEG / PNG · 파일당 최대 {Math.floor(maxBytes / 1024 / 1024)}MB
      </p>
      <span
        id={`${inputId}-error`}
        role="alert"
        className="ui-field__message ui-field__message--error"
      >
        {error || requiredError}
      </span>
    </div>
  );
}
