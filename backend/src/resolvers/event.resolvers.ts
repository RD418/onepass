import { GraphQLContext } from '../types/context';
import { NotFoundError, ValidationError, AuthorizationError } from '../middleware/error.middleware';
import { requireDbUser } from '../utils/auth-user';

export const eventResolvers = {
  Query: {
    events: async (_: any, { filters, first = 20, after }: any, context: GraphQLContext) => {
      const where: any = {};
      
      if (filters) {
        if (filters.status) where.status = filters.status;
        if (filters.organizerId) where.organizerId = filters.organizerId;
        if (filters.searchQuery) {
          where.title = { contains: filters.searchQuery, mode: 'insensitive' };
        }
        if (filters.startTimeFrom) {
          where.startTime = { ...where.startTime, gte: new Date(filters.startTimeFrom) };
        }
        if (filters.startTimeTo) {
          where.startTime = { ...where.startTime, lte: new Date(filters.startTimeTo) };
        }
        if (filters.tags && filters.tags.length > 0) {
          where.tags = {
            some: {
              tag: { in: filters.tags },
            },
          };
        }
      }
      
      const events = await context.prisma.event.findMany({
        where,
        take: first + 1,
        skip: after ? 1 : 0,
        cursor: after ? { id: after } : undefined,
        orderBy: { startTime: 'asc' },
      });
      
      const hasNextPage = events.length > first;
      const edges = events.slice(0, first);
      
      return {
        edges: edges.map(event => ({
          node: event,
          cursor: event.id,
        })),
        pageInfo: {
          hasNextPage,
          hasPreviousPage: !!after,
          startCursor: edges[0]?.id,
          endCursor: edges[edges.length - 1]?.id,
        },
        totalCount: await context.prisma.event.count({ where }),
      };
    },
    
    event: async (_: any, { id }: { id: string }, context: GraphQLContext) => {
      const event = await context.prisma.event.findUnique({
        where: { id },
      });
      
      if (!event) {
        throw new NotFoundError('Event');
      }
      
      return event;
    },
    
    featuredEvents: async (_: any, __: any, context: GraphQLContext) => {
      return await context.prisma.event.findMany({
        where: {
          status: 'PUBLISHED',
          startTime: { gte: new Date() },
        },
        orderBy: { startTime: 'asc' },
        take: 3,
      });
    },
    
    eventsByOrganization: async (_: any, { organizationId }: { organizationId: string }, context: GraphQLContext) => {
      return await context.prisma.event.findMany({
        where: { organizerId: organizationId },
        orderBy: { startTime: 'asc' },
      });
    },
  },
  
  Mutation: {
    createEvent: async (_: any, { input }: any, context: GraphQLContext) => {
      const user = await requireDbUser(context);
      
      // Verify user has permission to create event for organization
      const membership = await context.prisma.membership.findFirst({
        where: {
          userId: user.uid,
          organizationId: input.organizerId,
          role: { in: ['OWNER', 'ADMIN', 'MEMBER'] },
          status: 'ACTIVE',
        },
      });
      
      if (!membership) {
        throw new AuthorizationError('You do not have permission to create events for this organization');
      }
      
      const org = await context.prisma.organization.findUnique({
        where: { id: input.organizerId },
      });
      
      if (!org) {
        throw new NotFoundError('Organization');
      }
      
      // Create event
      const event = await context.prisma.event.create({
        data: {
          title: input.title,
          description: input.description,
          organizerId: input.organizerId,
          organizerName: org.name,
          startTime: new Date(input.startTime),
          endTime: new Date(input.endTime),
          capacity: input.capacity,
          ticketsRemaining: input.capacity,
          currency: input.currency || 'CHF',
          locationName: input.location?.name,
          locationPoint: input.location ? 
            `SRID=4326;POINT(${input.location.longitude} ${input.location.latitude})` : 
            null,
        },
      });
      
      // Create pricing tiers
      if (input.pricingTiers && input.pricingTiers.length > 0) {
        await context.prisma.eventPricingTier.createMany({
          data: input.pricingTiers.map((tier: any) => ({
            eventId: event.id,
            name: tier.name,
            price: tier.price,
            quantity: tier.quantity,
            remaining: tier.quantity,
          })),
        });
      }
      
      // Create images
      if (input.images && input.images.length > 0) {
        await context.prisma.eventImage.createMany({
          data: input.images.map((url: string, index: number) => ({
            eventId: event.id,
            imageUrl: url,
            displayOrder: index,
          })),
        });
      }
      
      // Create tags
      if (input.tags && input.tags.length > 0) {
        await context.prisma.eventTag.createMany({
          data: input.tags.map((tag: string) => ({
            eventId: event.id,
            tag,
          })),
        });
      }
      
      return event;
    },
    
    updateEvent: async (_: any, { id, input }: any, context: GraphQLContext) => {
      const user = await requireDbUser(context);
      
      const event = await context.prisma.event.findUnique({
        where: { id },
      });
      
      if (!event) {
        throw new NotFoundError('Event');
      }
      
      // Verify permission
      const membership = await context.prisma.membership.findFirst({
        where: {
          userId: user.uid,
          organizationId: event.organizerId,
          role: { in: ['OWNER', 'ADMIN', 'MEMBER'] },
          status: 'ACTIVE',
        },
      });
      
      if (!membership) {
        throw new AuthorizationError('You do not have permission to update this event');
      }
      
      const updateData: any = {};
      if (input.title) updateData.title = input.title;
      if (input.description) updateData.description = input.description;
      if (input.status) updateData.status = input.status;
      if (input.startTime) updateData.startTime = new Date(input.startTime);
      if (input.endTime) updateData.endTime = new Date(input.endTime);
      if (input.capacity !== undefined) updateData.capacity = input.capacity;
      if (input.location) {
        updateData.locationName = input.location.name;
        updateData.locationPoint = `SRID=4326;POINT(${input.location.longitude} ${input.location.latitude})`;
      }
      
      return await context.prisma.event.update({
        where: { id },
        data: updateData,
      });
    },
    
    deleteEvent: async (_: any, { id }: { id: string }, context: GraphQLContext) => {
      const user = await requireDbUser(context);
      
      const event = await context.prisma.event.findUnique({
        where: { id },
      });
      
      if (!event) {
        throw new NotFoundError('Event');
      }
      
      // Verify permission
      const membership = await context.prisma.membership.findFirst({
        where: {
          userId: user.uid,
          organizationId: event.organizerId,
          role: { in: ['OWNER', 'ADMIN'] },
          status: 'ACTIVE',
        },
      });
      
      if (!membership) {
        throw new AuthorizationError('You do not have permission to delete this event');
      }
      
      await context.prisma.event.update({
        where: { id },
        data: { deletedAt: new Date() },
      });
      
      return true;
    },
    
    addEventImage: async (_: any, { eventId, imageUrl }: any, context: GraphQLContext) => {
      await requireDbUser(context);
      
      const event = await context.prisma.event.findUnique({
        where: { id: eventId },
      });
      
      if (!event) {
        throw new NotFoundError('Event');
      }
      
      // Get current max order
      const maxOrder = await context.prisma.eventImage.findFirst({
        where: { eventId },
        orderBy: { displayOrder: 'desc' },
      });
      
      await context.prisma.eventImage.create({
        data: {
          eventId,
          imageUrl,
          displayOrder: (maxOrder?.displayOrder || -1) + 1,
        },
      });
      
      return event;
    },
    
    removeEventImage: async (_: any, { eventId, imageUrl }: any, context: GraphQLContext) => {
      await requireDbUser(context);
      
      await context.prisma.eventImage.deleteMany({
        where: {
          eventId,
          imageUrl,
        },
      });
      
      return await context.prisma.event.findUnique({
        where: { id: eventId },
      });
    },
  },
  
  Event: {
    organizer: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.organization.findUnique({
        where: { id: parent.organizerId },
      });
    },
    
    pricingTiers: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.eventPricingTier.findMany({
        where: { eventId: parent.id },
      });
    },
    
    images: async (parent: any, _: any, context: GraphQLContext) => {
      const images = await context.prisma.eventImage.findMany({
        where: { eventId: parent.id },
        orderBy: { displayOrder: 'asc' },
      });
      
      return images.map(img => img.imageUrl);
    },
    
    tags: async (parent: any, _: any, context: GraphQLContext) => {
      const tags = await context.prisma.eventTag.findMany({
        where: { eventId: parent.id },
      });
      
      return tags.map(t => t.tag);
    },
    
    location: async (parent: any, _: any, context: GraphQLContext) => {
      if (!parent.locationPoint || !parent.locationName) {
        return null;
      }
      
      // Parse PostGIS POINT format: "SRID=4326;POINT(lng lat)"
      const match = parent.locationPoint.match(/POINT\(([^ ]+) ([^ ]+)\)/);
      if (!match) return null;
      
      return {
        name: parent.locationName,
        longitude: parseFloat(match[1]),
        latitude: parseFloat(match[2]),
      };
    },
    
    lowestPrice: async (parent: any, _: any, context: GraphQLContext) => {
      const minPriceTier = await context.prisma.eventPricingTier.findFirst({
        where: { eventId: parent.id },
        orderBy: { price: 'asc' },
      });
      
      return minPriceTier ? parseFloat(minPriceTier.price.toString()) : 0;
    },
    
    isSoldOut: (parent: any) => parent.ticketsRemaining <= 0,
    
    isPublished: (parent: any) => parent.status === 'PUBLISHED',
  },
};
