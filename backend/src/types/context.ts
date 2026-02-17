import { PrismaClient } from '@prisma/client';
import { AuthUser } from '../services/auth.service';

export interface GraphQLContext {
  user: AuthUser | null;
  prisma: PrismaClient;
}

