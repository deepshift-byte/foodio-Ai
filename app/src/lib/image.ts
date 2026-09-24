import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';

export interface PickedImage {
  uri: string;
  base64: string;
  /** Small JPEG data URI kept in meal history. */
  thumbnail: string;
}

/** Take or choose a meal photo, downscaled to 1024 px JPEG for fast upload. */
export async function pickMealPhoto(source: 'camera' | 'library'): Promise<PickedImage | null> {
  if (source === 'camera') {
    const perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) throw new Error('Camera permission is needed to photograph your meal.');
  }
  const options: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], quality: 1 };
  const result =
    source === 'camera' ? await ImagePicker.launchCameraAsync(options) : await ImagePicker.launchImageLibraryAsync(options);
  if (result.canceled || !result.assets[0]) return null;

  const asset = result.assets[0];
  const longest = Math.max(asset.width ?? 0, asset.height ?? 0);
  const ctx = ImageManipulator.manipulate(asset.uri);
  if (longest > 1024) {
    ctx.resize(asset.width >= asset.height ? { width: 1024 } : { height: 1024 });
  }
  const rendered = await ctx.renderAsync();
  const saved = await rendered.saveAsync({ format: SaveFormat.JPEG, compress: 0.75, base64: true });
  if (!saved.base64) throw new Error('Could not read the photo.');
  // On web the base64 may arrive as a data URI.
  const base64 = stripDataUri(saved.base64);

  const thumbCtx = ImageManipulator.manipulate(asset.uri);
  thumbCtx.resize(asset.width >= asset.height ? { width: 240 } : { height: 240 });
  const thumb = await (await thumbCtx.renderAsync()).saveAsync({ format: SaveFormat.JPEG, compress: 0.6, base64: true });

  return { uri: saved.uri, base64, thumbnail: `data:image/jpeg;base64,${stripDataUri(thumb.base64 ?? '')}` };
}

function stripDataUri(b64: string) {
  return b64.replace(/^data:image\/\w+;base64,/, '');
}
