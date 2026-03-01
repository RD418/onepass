import { GraphQLContext } from '../types/context';
import { NotFoundError } from '../middleware/error.middleware';
import { requireAuth } from '../middleware/auth.middleware';
import { requireDbUser } from '../utils/auth-user';

export const userResolvers = {
  Query: {
    me: async (_: any, __: any, context: GraphQLContext) => {
      return await requireDbUser(context);
    },
    
    user: async (_: any, { uid }: { uid: string }, context: GraphQLContext) => {
      const user = await context.prisma.user.findUnique({
        where: { uid },
      });
      
      if (!user) {
        throw new NotFoundError('User');
      }
      
      return user;
    },
    
    searchUsers: async (_: any, { query, limit = 10 }: { query: string; limit?: number }, context: GraphQLContext) => {
      requireAuth(context.user);
      
      return await context.prisma.user.findMany({
        where: {
          OR: [
            { displayName: { contains: query, mode: 'insensitive' } },
            { email: { contains: query, mode: 'insensitive' } },
          ],
          status: 'ACTIVE',
        },
        take: limit,
      });
    },
  },
  
  Mutation: {
    updateUser: async (_: any, { input }: any, context: GraphQLContext) => {
      const user = await requireDbUser(context);
      
      return await context.prisma.user.update({
        where: { uid: user.uid },
        data: {
          displayName: input.displayName,
          bio: input.bio,
          avatarUrl: input.avatarUrl,
          coverUrl: input.coverUrl,
          phoneE164: input.phoneE164,
          country: input.country,
          showEmail: input.showEmail,
          analyticsEnabled: input.analyticsEnabled,
        },
      });
    },
    
    deleteUser: async (_: any, __: any, context: GraphQLContext) => {
      const user = await requireDbUser(context);
      
      await context.prisma.user.update({
        where: { uid: user.uid },
        data: { status: 'DELETED' },
      });
      
      return true;
    },
    
    addFavoriteEvent: async (_: any, { eventId }: { eventId: string }, context: GraphQLContext) => {
      const user = await requireDbUser(context);
      
      await context.prisma.userFavoriteEvent.create({
        data: {
          userId: user.uid,
          eventId,
        },
      });
      
      return await context.prisma.user.findUnique({
        where: { uid: user.uid },
      });
    },
    
    removeFavoriteEvent: async (_: any, { eventId }: { eventId: string }, context: GraphQLContext) => {
      const user = await requireDbUser(context);
      
      await context.prisma.userFavoriteEvent.delete({
        where: {
          userId_eventId: {
            userId: user.uid,
            eventId,
          },
        },
      });
      
      return await context.prisma.user.findUnique({
        where: { uid: user.uid },
      });
    },
  },
  
  User: {
    organizations: async (parent: any, _: any, context: GraphQLContext) => {
      const memberships = await context.prisma.membership.findMany({
        where: { userId: parent.uid },
        include: { organization: true },
      });
      
      return memberships.map(m => m.organization);
    },
    
    tickets: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.ticket.findMany({
        where: { ownerId: parent.uid },
      });
    },
    
    favoriteEvents: async (parent: any, _: any, context: GraphQLContext) => {
      const favorites = await context.prisma.userFavoriteEvent.findMany({
        where: { userId: parent.uid },
        include: { event: true },
      });
      
      return favorites.map(f => f.event);
    },
  },
};
