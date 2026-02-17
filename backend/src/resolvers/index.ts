import { GraphQLScalarType, Kind } from 'graphql';
import { eventResolvers } from './event.resolvers';
import { userResolvers } from './user.resolvers';
import { ticketResolvers } from './ticket.resolvers';
import { organizationResolvers } from './organization.resolvers';

// Custom scalar for DateTime
const dateTimeScalar = new GraphQLScalarType({
  name: 'DateTime',
  description: 'DateTime custom scalar type',
  serialize(value: any) {
    if (value instanceof Date) {
      return value.toISOString();
    }
    return value;
  },
  parseValue(value: any) {
    return new Date(value);
  },
  parseLiteral(ast) {
    if (ast.kind === Kind.STRING) {
      return new Date(ast.value);
    }
    return null;
  },
});

// Custom scalar for JSON
const jsonScalar = new GraphQLScalarType({
  name: 'JSON',
  description: 'JSON custom scalar type',
  serialize(value: any) {
    return value;
  },
  parseValue(value: any) {
    return value;
  },
  parseLiteral(ast) {
    if (ast.kind === Kind.OBJECT) {
      return ast;
    }
    return null;
  },
});

export const resolvers = {
  DateTime: dateTimeScalar,
  JSON: jsonScalar,
  
  Query: {
    ...eventResolvers.Query,
    ...userResolvers.Query,
    ...ticketResolvers.Query,
    ...organizationResolvers.Query,
  },
  
  Mutation: {
    ...eventResolvers.Mutation,
    ...userResolvers.Mutation,
    ...ticketResolvers.Mutation,
    ...organizationResolvers.Mutation,
  },
  
  Event: eventResolvers.Event,
  User: userResolvers.User,
  Ticket: ticketResolvers.Ticket,
  Organization: organizationResolvers.Organization,
  Membership: organizationResolvers.Membership,
  OrganizationInvitation: organizationResolvers.OrganizationInvitation,
  Post: organizationResolvers.Post,
};

