import { getFirebaseApp } from './auth.service';

function getBucket() {
  const app = getFirebaseApp();
  if (!app) {
    throw new Error('Firebase Admin is not configured');
  }
  return app.storage().bucket();
}

/**
 * Generates a signed URL for uploading a file to Firebase Storage
 * @param filePath - The path where the file will be stored
 * @param contentType - The MIME type of the file
 * @returns A signed upload URL
 */
export async function generateUploadUrl(
  filePath: string,
  contentType: string
): Promise<string> {
  const bucket = getBucket();
  const file = bucket.file(filePath);
  
  const [url] = await file.getSignedUrl({
    version: 'v4',
    action: 'write',
    expires: Date.now() + 15 * 60 * 1000, // 15 minutes
    contentType,
  });
  
  return url;
}

/**
 * Generates a signed URL for downloading a file from Firebase Storage
 * @param filePath - The path to the file
 * @returns A signed download URL
 */
export async function generateDownloadUrl(filePath: string): Promise<string> {
  const bucket = getBucket();
  const file = bucket.file(filePath);
  
  const [url] = await file.getSignedUrl({
    version: 'v4',
    action: 'read',
    expires: Date.now() + 60 * 60 * 1000, // 1 hour
  });
  
  return url;
}

/**
 * Deletes a file from Firebase Storage
 * @param filePath - The path to the file to delete
 */
export async function deleteFile(filePath: string): Promise<void> {
  const bucket = getBucket();
  const file = bucket.file(filePath);
  await file.delete();
}

