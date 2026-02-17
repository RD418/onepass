import { Request } from 'express';
import { AuthUser, verifyFirebaseToken } from '../services/auth.service';

/**
 * Extracts and verifies the Firebase authentication token from the request.
 * @param req - Express request object
 * @returns AuthUser object if token is valid, null otherwise
 */
export async function getUserFromRequest(req: Request): Promise<AuthUser | null> {
  const authHeader = req.headers.authorization;
  
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return null;
  }
  
  const token = authHeader.substring(7);
  
  try {
    return await verifyFirebaseToken(token);
  } catch (error) {
    console.error('Token verification failed:', error);
    return null;
  }
}

/**
 * Middleware to require authentication for a resolver.
 * Throws an error if user is not authenticated.
 */
export function requireAuth(user: AuthUser | null): AuthUser {
  if (!user) {
    throw new Error('Authentication required');
  }
  return user;
}

