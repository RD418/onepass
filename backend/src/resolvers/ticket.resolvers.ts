import { GraphQLContext } from '../types/context';
import { NotFoundError, ValidationError, AuthorizationError } from '../middleware/error.middleware';
import { requireAuth } from '../middleware/auth.middleware';
import { stripe } from '../services/stripe.service';

export const ticketResolvers = {
  Query: {
    ticket: async (_: any, { id }: { id: string }, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const ticket = await context.prisma.ticket.findUnique({
        where: { id },
      });
      
      if (!ticket) {
        throw new NotFoundError('Ticket');
      }
      
      // Only owner can view ticket details
      if (ticket.ownerId !== user.uid) {
        throw new AuthorizationError('You do not have permission to view this ticket');
      }
      
      return ticket;
    },
    
    myTickets: async (_: any, { filters }: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const where: any = {
        ownerId: user.uid,
      };
      
      if (filters) {
        if (filters.state) where.state = filters.state;
        if (filters.eventId) where.eventId = filters.eventId;
      }
      
      return await context.prisma.ticket.findMany({
        where,
        orderBy: { issuedAt: 'desc' },
      });
    },
    
    listedTickets: async (_: any, { eventId }: { eventId?: string }, context: GraphQLContext) => {
      const where: any = {
        state: 'LISTED',
      };
      
      if (eventId) {
        where.eventId = eventId;
      }
      
      return await context.prisma.ticket.findMany({
        where,
        orderBy: { listedAt: 'desc' },
      });
    },
  },
  
  Mutation: {
    purchaseTickets: async (_: any, { input }: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      // Get event and tier
      const event = await context.prisma.event.findUnique({
        where: { id: input.eventId },
      });
      
      if (!event) {
        throw new NotFoundError('Event');
      }
      
      if (event.status !== 'PUBLISHED') {
        throw new ValidationError('Event is not available for ticket purchase');
      }
      
      const tier = await context.prisma.eventPricingTier.findUnique({
        where: { id: input.tierId },
      });
      
      if (!tier) {
        throw new NotFoundError('Pricing tier');
      }
      
      if (tier.remaining < input.quantity) {
        throw new ValidationError('Not enough tickets available');
      }
      
      // Get user for Stripe customer ID
      const userRecord = await context.prisma.user.findUnique({
        where: { uid: user.uid },
      });
      
      // Calculate total amount
      const amount = parseFloat(tier.price.toString()) * input.quantity;
      
      // Create payment intent
      const paymentIntent = await stripe.paymentIntents.create({
        amount: Math.round(amount * 100),
        currency: event.currency.toLowerCase(),
        customer: userRecord?.stripeCustomerId || undefined,
        payment_method: input.paymentMethodId,
        confirm: true,
        automatic_payment_methods: {
          enabled: true,
          allow_redirects: 'never',
        },
        metadata: {
          eventId: event.id,
          tierId: tier.id,
          userId: user.uid,
          quantity: input.quantity.toString(),
        },
      });
      
      if (paymentIntent.status !== 'succeeded') {
        throw new ValidationError('Payment failed');
      }
      
      // Create tickets in a transaction
      const tickets = await context.prisma.$transaction(async (tx) => {
        // Update tier remaining
        await tx.eventPricingTier.update({
          where: { id: tier.id },
          data: { remaining: { decrement: input.quantity } },
        });
        
        // Update event tickets
        await tx.event.update({
          where: { id: event.id },
          data: {
            ticketsRemaining: { decrement: input.quantity },
            ticketsIssued: { increment: input.quantity },
          },
        });
        
        // Create tickets
        const createdTickets = [];
        for (let i = 0; i < input.quantity; i++) {
          const ticket = await tx.ticket.create({
            data: {
              eventId: event.id,
              ownerId: user.uid,
              tierId: tier.id,
              purchasePrice: tier.price,
              currency: event.currency,
              state: 'ISSUED',
            },
          });
          createdTickets.push(ticket);
        }
        
        // Create payment record
        await tx.payment.create({
          data: {
            userId: user.uid,
            eventId: event.id,
            amount,
            currency: event.currency,
            status: 'SUCCEEDED',
            stripePaymentIntentId: paymentIntent.id,
            stripeChargeId: paymentIntent.latest_charge as string,
          },
        });
        
        return createdTickets;
      });
      
      return tickets;
    },
    
    listTicket: async (_: any, { input }: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const ticket = await context.prisma.ticket.findUnique({
        where: { id: input.ticketId },
      });
      
      if (!ticket) {
        throw new NotFoundError('Ticket');
      }
      
      if (ticket.ownerId !== user.uid) {
        throw new AuthorizationError('You do not own this ticket');
      }
      
      if (ticket.state !== 'ISSUED') {
        throw new ValidationError('Only issued tickets can be listed');
      }
      
      if (ticket.transferLock) {
        throw new ValidationError('This ticket cannot be transferred');
      }
      
      return await context.prisma.ticket.update({
        where: { id: input.ticketId },
        data: {
          state: 'LISTED',
          listingPrice: input.listingPrice,
          listedAt: new Date(),
        },
      });
    },
    
    unlistTicket: async (_: any, { ticketId }: { ticketId: string }, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const ticket = await context.prisma.ticket.findUnique({
        where: { id: ticketId },
      });
      
      if (!ticket) {
        throw new NotFoundError('Ticket');
      }
      
      if (ticket.ownerId !== user.uid) {
        throw new AuthorizationError('You do not own this ticket');
      }
      
      return await context.prisma.ticket.update({
        where: { id: ticketId },
        data: {
          state: 'ISSUED',
          listingPrice: null,
          listedAt: null,
        },
      });
    },
    
    transferTicket: async (_: any, { input }: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const ticket = await context.prisma.ticket.findUnique({
        where: { id: input.ticketId },
      });
      
      if (!ticket) {
        throw new NotFoundError('Ticket');
      }
      
      if (ticket.ownerId !== user.uid) {
        throw new AuthorizationError('You do not own this ticket');
      }
      
      if (ticket.transferLock) {
        throw new ValidationError('This ticket cannot be transferred');
      }
      
      // Find recipient user by email
      const recipient = await context.prisma.user.findUnique({
        where: { email: input.recipientEmail },
      });
      
      if (!recipient) {
        throw new NotFoundError('Recipient user not found');
      }
      
      return await context.prisma.ticket.update({
        where: { id: input.ticketId },
        data: {
          ownerId: recipient.uid,
          state: 'TRANSFERRED',
          listingPrice: null,
          listedAt: null,
        },
      });
    },
    
    redeemTicket: async (_: any, { ticketId }: { ticketId: string }, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const ticket = await context.prisma.ticket.findUnique({
        where: { id: ticketId },
        include: { event: true },
      });
      
      if (!ticket) {
        throw new NotFoundError('Ticket');
      }
      
      // Verify user has permission (must be staff of the organization)
      const membership = await context.prisma.membership.findFirst({
        where: {
          userId: user.uid,
          organizationId: ticket.event.organizerId,
          role: { in: ['OWNER', 'ADMIN', 'STAFF'] },
          status: 'ACTIVE',
        },
      });
      
      if (!membership) {
        throw new AuthorizationError('You do not have permission to redeem tickets for this event');
      }
      
      if (ticket.state === 'REDEEMED') {
        throw new ValidationError('Ticket has already been redeemed');
      }
      
      if (ticket.state === 'REVOKED') {
        throw new ValidationError('Ticket has been revoked');
      }
      
      // Redeem ticket
      const updatedTicket = await context.prisma.$transaction(async (tx) => {
        const updated = await tx.ticket.update({
          where: { id: ticketId },
          data: { state: 'REDEEMED' },
        });
        
        await tx.event.update({
          where: { id: ticket.eventId },
          data: { ticketsRedeemed: { increment: 1 } },
        });
        
        return updated;
      });
      
      return updatedTicket;
    },
  },
  
  Ticket: {
    event: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.event.findUnique({
        where: { id: parent.eventId },
      });
    },
    
    owner: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.user.findUnique({
        where: { uid: parent.ownerId },
      });
    },
    
    tier: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.eventPricingTier.findUnique({
        where: { id: parent.tierId },
      });
    },
    
    isListed: (parent: any) => parent.state === 'LISTED' && parent.listingPrice !== null,
    
    canBeListed: (parent: any) => parent.state === 'ISSUED' && !parent.transferLock,
  },
};

