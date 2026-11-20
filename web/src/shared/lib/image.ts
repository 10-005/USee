export const imageMaxBytes = 10 * 1024 * 1024;
export const supportedImageTypes = ['image/jpeg', 'image/png'];
export function isSupportedImage(file: File): boolean {
  return supportedImageTypes.includes(file.type) && file.size <= imageMaxBytes;
}
export function readImageDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error('이미지를 읽을 수 없습니다.'));
    reader.onabort = () => reject(new Error('이미지 읽기가 취소되었습니다.'));
    reader.readAsDataURL(file);
  });
}
