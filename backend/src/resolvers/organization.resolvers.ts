import { GraphQLContext } from '../types/context';
import { NotFoundError, ValidationError, AuthorizationError } from '../middleware/error.middleware';
import { requireAuth } from '../middleware/auth.middleware';

export const organizationResolvers = {
  Query: {
    organization: async (_: any, { id }: { id: string }, context: GraphQLContext) => {
      const org = await context.prisma.organization.findUnique({
        where: { id },
      });
      
      if (!org) {
        throw new NotFoundError('Organization');
      }
      
      return org;
    },
    
    myOrganizations: async (_: any, __: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const memberships = await context.prisma.membership.findMany({
        where: {
          userId: user.uid,
          status: 'ACTIVE',
        },
        include: { organization: true },
      });
      
      return memberships.map(m => m.organization);
    },
    
    searchOrganizations: async (_: any, { query, limit = 10 }: { query: string; limit?: number }, context: GraphQLContext) => {
      return await context.prisma.organization.findMany({
        where: {
          name: { contains: query, mode: 'insensitive' },
          status: 'ACTIVE',
        },
        take: limit,
      });
    },
    
    organizationInvitations: async (_: any, { organizationId }: { organizationId?: string }, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const where: any = {};
      
      if (organizationId) {
        // Verify user has permission to view org invitations
        const membership = await context.prisma.membership.findFirst({
          where: {
            userId: user.uid,
            organizationId,
            role: { in: ['OWNER', 'ADMIN'] },
            status: 'ACTIVE',
          },
        });
        
        if (!membership) {
          throw new AuthorizationError('You do not have permission to view organization invitations');
        }
        
        where.orgId = organizationId;
      }
      
      return await context.prisma.organizationInvitation.findMany({
        where,
        orderBy: { createdAt: 'desc' },
      });
    },
    
    myInvitations: async (_: any, __: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const userRecord = await context.prisma.user.findUnique({
        where: { uid: user.uid },
      });
      
      if (!userRecord) {
        throw new NotFoundError('User');
      }
      
      return await context.prisma.organizationInvitation.findMany({
        where: {
          inviteeEmail: userRecord.email,
          status: 'PENDING',
        },
        orderBy: { createdAt: 'desc' },
      });
    },
  },
  
  Mutation: {
    createOrganization: async (_: any, { input }: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      // Create organization
      const org = await context.prisma.organization.create({
        data: {
          name: input.name,
          description: input.description,
          ownerId: user.uid,
          profileImageUrl: input.profileImageUrl,
          coverImageUrl: input.coverImageUrl,
          website: input.website,
          instagram: input.instagram,
          tiktok: input.tiktok,
          facebook: input.facebook,
          contactEmail: input.contactEmail,
          contactPhone: input.contactPhone,
          phonePrefix: input.phonePrefix,
          address: input.address,
          status: 'PENDING',
        },
      });
      
      // Create owner membership
      await context.prisma.membership.create({
        data: {
          userId: user.uid,
          organizationId: org.id,
          role: 'OWNER',
          status: 'ACTIVE',
        },
      });
      
      return org;
    },
    
    updateOrganization: async (_: any, { id, input }: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      // Verify permission
      const membership = await context.prisma.membership.findFirst({
        where: {
          userId: user.uid,
          organizationId: id,
          role: { in: ['OWNER', 'ADMIN'] },
          status: 'ACTIVE',
        },
      });
      
      if (!membership) {
        throw new AuthorizationError('You do not have permission to update this organization');
      }
      
      return await context.prisma.organization.update({
        where: { id },
        data: {
          name: input.name,
          description: input.description,
          profileImageUrl: input.profileImageUrl,
          coverImageUrl: input.coverImageUrl,
          website: input.website,
          instagram: input.instagram,
          tiktok: input.tiktok,
          facebook: input.facebook,
          contactEmail: input.contactEmail,
          contactPhone: input.contactPhone,
          phonePrefix: input.phonePrefix,
          address: input.address,
        },
      });
    },
    
    deleteOrganization: async (_: any, { id }: { id: string }, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const org = await context.prisma.organization.findUnique({
        where: { id },
      });
      
      if (!org) {
        throw new NotFoundError('Organization');
      }
      
      if (org.ownerId !== user.uid) {
        throw new AuthorizationError('Only the owner can delete the organization');
      }
      
      await context.prisma.organization.update({
        where: { id },
        data: { status: 'ARCHIVED' },
      });
      
      return true;
    },
    
    inviteMember: async (_: any, { input }: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      // Verify permission
      const membership = await context.prisma.membership.findFirst({
        where: {
          userId: user.uid,
          organizationId: input.organizationId,
          role: { in: ['OWNER', 'ADMIN'] },
          status: 'ACTIVE',
        },
      });
      
      if (!membership) {
        throw new AuthorizationError('You do not have permission to invite members');
      }
      
      // Check if user already has a membership
      const existingUser = await context.prisma.user.findUnique({
        where: { email: input.inviteeEmail },
      });
      
      if (existingUser) {
        const existingMembership = await context.prisma.membership.findFirst({
          where: {
            userId: existingUser.uid,
            organizationId: input.organizationId,
          },
        });
        
        if (existingMembership) {
          throw new ValidationError('User is already a member of this organization');
        }
      }
      
      // Check for existing pending invitation
      const existingInvitation = await context.prisma.organizationInvitation.findFirst({
        where: {
          orgId: input.organizationId,
          inviteeEmail: input.inviteeEmail,
          status: 'PENDING',
        },
      });
      
      if (existingInvitation) {
        throw new ValidationError('An invitation is already pending for this email');
      }
      
      // Create invitation
      const invitation = await context.prisma.organizationInvitation.create({
        data: {
          orgId: input.organizationId,
          inviteeEmail: input.inviteeEmail,
          role: input.role,
          invitedBy: user.uid,
          expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days
        },
      });
      
      return invitation;
    },
    
    acceptInvitation: async (_: any, { invitationId }: { invitationId: string }, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const userRecord = await context.prisma.user.findUnique({
        where: { uid: user.uid },
      });
      
      if (!userRecord) {
        throw new NotFoundError('User');
      }
      
      const invitation = await context.prisma.organizationInvitation.findUnique({
        where: { id: invitationId },
      });
      
      if (!invitation) {
        throw new NotFoundError('Invitation');
      }
      
      if (invitation.inviteeEmail !== userRecord.email) {
        throw new AuthorizationError('This invitation is not for you');
      }
      
      if (invitation.status !== 'PENDING') {
        throw new ValidationError('This invitation is no longer valid');
      }
      
      if (invitation.expiresAt && invitation.expiresAt < new Date()) {
        await context.prisma.organizationInvitation.update({
          where: { id: invitationId },
          data: { status: 'EXPIRED' },
        });
        throw new ValidationError('This invitation has expired');
      }
      
      // Create membership
      const membership = await context.prisma.$transaction(async (tx) => {
        const newMembership = await tx.membership.create({
          data: {
            userId: user.uid,
            organizationId: invitation.orgId,
            role: invitation.role,
            status: 'ACTIVE',
          },
        });
        
        await tx.organizationInvitation.update({
          where: { id: invitationId },
          data: { status: 'ACCEPTED' },
        });
        
        return newMembership;
      });
      
      return membership;
    },
    
    rejectInvitation: async (_: any, { invitationId }: { invitationId: string }, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const userRecord = await context.prisma.user.findUnique({
        where: { uid: user.uid },
      });
      
      if (!userRecord) {
        throw new NotFoundError('User');
      }
      
      const invitation = await context.prisma.organizationInvitation.findUnique({
        where: { id: invitationId },
      });
      
      if (!invitation) {
        throw new NotFoundError('Invitation');
      }
      
      if (invitation.inviteeEmail !== userRecord.email) {
        throw new AuthorizationError('This invitation is not for you');
      }
      
      await context.prisma.organizationInvitation.update({
        where: { id: invitationId },
        data: { status: 'REJECTED' },
      });
      
      return true;
    },
    
    updateMemberRole: async (_: any, { input }: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const membership = await context.prisma.membership.findUnique({
        where: { id: input.membershipId },
      });
      
      if (!membership) {
        throw new NotFoundError('Membership');
      }
      
      // Verify permission
      const requesterMembership = await context.prisma.membership.findFirst({
        where: {
          userId: user.uid,
          organizationId: membership.organizationId,
          role: { in: ['OWNER', 'ADMIN'] },
          status: 'ACTIVE',
        },
      });
      
      if (!requesterMembership) {
        throw new AuthorizationError('You do not have permission to update member roles');
      }
      
      // Only owner can change owner role
      if (membership.role === 'OWNER' && requesterMembership.role !== 'OWNER') {
        throw new AuthorizationError('Only the owner can modify the owner role');
      }
      
      return await context.prisma.membership.update({
        where: { id: input.membershipId },
        data: { role: input.role },
      });
    },
    
    removeMember: async (_: any, { membershipId }: { membershipId: string }, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      const membership = await context.prisma.membership.findUnique({
        where: { id: membershipId },
      });
      
      if (!membership) {
        throw new NotFoundError('Membership');
      }
      
      // Verify permission
      const requesterMembership = await context.prisma.membership.findFirst({
        where: {
          userId: user.uid,
          organizationId: membership.organizationId,
          role: { in: ['OWNER', 'ADMIN'] },
          status: 'ACTIVE',
        },
      });
      
      if (!requesterMembership) {
        throw new AuthorizationError('You do not have permission to remove members');
      }
      
      // Cannot remove owner
      if (membership.role === 'OWNER') {
        throw new AuthorizationError('Cannot remove the organization owner');
      }
      
      await context.prisma.membership.delete({
        where: { id: membershipId },
      });
      
      return true;
    },
    
    createPost: async (_: any, { organizationId, title, content, imageUrl }: any, context: GraphQLContext) => {
      const user = requireAuth(context.user);
      
      // Verify permission
      const membership = await context.prisma.membership.findFirst({
        where: {
          userId: user.uid,
          organizationId,
          role: { in: ['OWNER', 'ADMIN', 'MEMBER'] },
          status: 'ACTIVE',
        },
      });
      
      if (!membership) {
        throw new AuthorizationError('You do not have permission to create posts for this organization');
      }
      
      return await context.prisma.post.create({
        data: {
          organizationId,
          title,
          content,
          imageUrl,
        },
      });
    },
  },
  
  Organization: {
    owner: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.user.findUnique({
        where: { uid: parent.ownerId },
      });
    },
    
    events: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.event.findMany({
        where: { organizerId: parent.id },
        orderBy: { startTime: 'asc' },
      });
    },
    
    memberships: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.membership.findMany({
        where: { organizationId: parent.id },
      });
    },
    
    posts: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.post.findMany({
        where: { organizationId: parent.id },
        orderBy: { createdAt: 'desc' },
      });
    },
  },
  
  Membership: {
    user: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.user.findUnique({
        where: { uid: parent.userId },
      });
    },
    
    organization: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.organization.findUnique({
        where: { id: parent.organizationId },
      });
    },
  },
  
  OrganizationInvitation: {
    organization: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.organization.findUnique({
        where: { id: parent.orgId },
      });
    },
  },
  
  Post: {
    organization: async (parent: any, _: any, context: GraphQLContext) => {
      return await context.prisma.organization.findUnique({
        where: { id: parent.organizationId },
      });
    },
  },
};

