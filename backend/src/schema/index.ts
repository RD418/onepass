import { readFileSync } from 'fs';
import { join } from 'path';

const scalarsTypeDefs = readFileSync(join(__dirname, 'typeDefs/scalars.graphql'), 'utf8');
const userTypeDefs = readFileSync(join(__dirname, 'typeDefs/user.graphql'), 'utf8');
const eventTypeDefs = readFileSync(join(__dirname, 'typeDefs/event.graphql'), 'utf8');
const ticketTypeDefs = readFileSync(join(__dirname, 'typeDefs/ticket.graphql'), 'utf8');
const orgTypeDefs = readFileSync(join(__dirname, 'typeDefs/organization.graphql'), 'utf8');
const notificationTypeDefs = readFileSync(join(__dirname, 'typeDefs/notification.graphql'), 'utf8');

export const typeDefs = `
  ${scalarsTypeDefs}
  
  type Query {
    _empty: String
  }
  
  type Mutation {
    _empty: String
  }
  
  ${userTypeDefs}
  ${eventTypeDefs}
  ${ticketTypeDefs}
  ${orgTypeDefs}
  ${notificationTypeDefs}
`;

