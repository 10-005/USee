import { useEffect, useId, useRef, useState } from 'react';
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
  onChange,
  label = '동물 사진',
  hideLabel = false,
  maxFiles = 3,
  maxBytes = 10 * 1024 * 1024,
}: {
  files: File[];
  onChange: (files: File[]) => void;
  label?: string;
  hideLabel?: boolean;
  maxFiles?: number;
  maxBytes?: number;
}) {
  const [error, setError] = useState('');
  const inputId = useId();
  return (
    <div className="image-upload">
      <label className={hideLabel ? 'shelter-sr-only' : 'ui-field__label'} htmlFor={inputId}>
        {label}
      </label>
      <p id={`${inputId}-hint`}>
        최대 {maxFiles}장 · JPG / JPEG / PNG · 파일당 최대 {Math.floor(maxBytes / 1024 / 1024)}MB
      </p>
      <div className="image-upload__items">
        {files.map((file, index) => (
          <div className="image-upload__preview" key={`${file.name}-${file.lastModified}-${index}`}>
            <FilePreview file={file} />
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
            disabled={files.length >= maxFiles}
            aria-describedby={`${inputId}-hint ${inputId}-error`}
            onChange={(event) => {
              const selected = Array.from(event.target.files ?? []);
              event.target.value = '';
              if (!selected.length) return;
              if (files.length + selected.length > maxFiles) {
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
      <span
        id={`${inputId}-error`}
        role="alert"
        className="ui-field__message ui-field__message--error"
      >
        {error}
      </span>
    </div>
  );
}
