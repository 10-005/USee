import { copyFile, DocumentDirectoryPath, mkdir, unlink } from '@dr.pogodin/react-native-fs';

const directory = () => `${DocumentDirectoryPath}/chat-photos`;
export const chatPhotoUri = (filename: string) => `file://${directory()}/${filename}`;
export const removeChatPhoto = (filename: string) => unlink(`${directory()}/${filename}`);

export async function saveChatPhoto(uri: string, mimeType: string) {
  await mkdir(directory());
  const filename = `${Date.now()}-${Math.random().toString(36).slice(2)}.${
    mimeType === 'image/png' ? 'png' : 'jpg'
  }`;
  try {
    await copyFile(uri, `${directory()}/${filename}`);
    return filename;
  } catch (error) {
    await removeChatPhoto(filename).catch(() => {});
    throw error;
  }
}
