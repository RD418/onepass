import { User } from '@prisma/client';
import { requireAuth } from '../middleware/auth.middleware';
import { AuthenticationError } from '../middleware/error.middleware';
import { GraphQLContext } from '../types/context';

/**
 * Resolves the authenticated Firebase identity to a persisted DB user.
 * Creates the user on first login and keeps firebaseUid/email synchronized.
 */
export async function requireDbUser(context: GraphQLContext): Promise<User> {
  const authUser = requireAuth(context.user);
  const email = authUser.email?.trim().toLowerCase();

  let user =
    (await context.prisma.user.findUnique({
      where: { firebaseUid: authUser.uid },
    })) ??
    (email
      ? await context.prisma.user.findUnique({
          where: { email },
        })
      : null);

  if (!user) {
    if (!email) {
      throw new AuthenticationError('Authenticated user email is required');
    }

    user = await context.prisma.user.create({
      data: {
        firebaseUid: authUser.uid,
        email,
        lastLoginAt: new Date(),
      },
    });
    return user;
  }

  const shouldSyncFirebaseUid = user.firebaseUid !== authUser.uid;
  const shouldSyncEmail = Boolean(email && user.email !== email);

  if (shouldSyncFirebaseUid || shouldSyncEmail) {
    user = await context.prisma.user.update({
      where: { uid: user.uid },
      data: {
        firebaseUid: authUser.uid,
        ...(email ? { email } : {}),
        lastLoginAt: new Date(),
      },
    });
  }

  return user;
}
