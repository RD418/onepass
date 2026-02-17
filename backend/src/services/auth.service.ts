// src/services/auth.service.ts
import * as admin from 'firebase-admin';

let firebaseApp: admin.app.App | null = null;

function initializeFirebase(): admin.app.App | null {
  if (firebaseApp) {
    return firebaseApp;
  }

  const projectId = process.env.FIREBASE_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  const privateKey = process.env.FIREBASE_PRIVATE_KEY;

  // Only initialize if credentials are provided and not placeholders
  if (!projectId || !clientEmail || !privateKey || 
      projectId === 'your-project-id' || 
      clientEmail.includes('your-service-account') ||
      clientEmail === 'your-service-account@your-project.iam.gserviceaccount.com') {
    console.warn('⚠️  Firebase Admin not configured. Authentication will be disabled.');
    return null;
  }

  try {
    firebaseApp = admin.initializeApp({
      credential: admin.credential.cert({
        projectId,
        clientEmail,
        privateKey: privateKey.replace(/\\n/g, '\n'),
      }),
    });
    return firebaseApp;
  } catch (error) {
    console.error('Failed to initialize Firebase Admin:', error);
    return null;
  }
}

export interface AuthUser {
  uid: string;
  email?: string;
}

export async function verifyFirebaseToken(token: string): Promise<AuthUser> {
  const app = initializeFirebase();
  
  if (!app) {
    throw new Error('Firebase Admin is not configured');
  }

  try {
    const decodedToken = await app.auth().verifyIdToken(token);
    return {
      uid: decodedToken.uid,
      email: decodedToken.email,
    };
  } catch (error) {
    throw new Error('Invalid authentication token');
  }
}

// Export a function to get Firebase Admin app, not the module itself
export function getFirebaseApp(): admin.app.App | null {
  return initializeFirebase();
}

// For backward compatibility, but don't export the raw admin module
// export { admin }; // Remove this line