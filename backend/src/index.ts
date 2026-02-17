import { ApolloServer } from '@apollo/server';
import { expressMiddleware } from '@apollo/server/express4';
import { ApolloServerPluginDrainHttpServer } from '@apollo/server/plugin/drainHttpServer';
import express from 'express';
import http from 'http';
import cors from 'cors';
import { json } from 'body-parser';
import { PrismaClient } from '@prisma/client';
import { typeDefs } from './schema';
import { resolvers } from './resolvers';
import { getUserFromRequest } from './middleware/auth.middleware';
import { GraphQLContext } from './types/context';
import dotenv from 'dotenv';

dotenv.config();

const prisma = new PrismaClient({
  log: process.env.NODE_ENV === 'development' ? ['query', 'error', 'warn'] : ['error'],
});

const app = express();
const httpServer = http.createServer(app);

const server = new ApolloServer<GraphQLContext>({
  typeDefs,
  resolvers,
  plugins: [ApolloServerPluginDrainHttpServer({ httpServer })],
  introspection: process.env.NODE_ENV !== 'production',
  formatError: (formattedError, error) => {
    // Log errors in development
    if (process.env.NODE_ENV === 'development') {
      console.error('GraphQL Error:', formattedError);
    }
    return formattedError;
  },
});

async function startServer() {
  await server.start();
  
  // Apply CORS middleware
  app.use(
    '/graphql',
    cors<cors.CorsRequest>({
      origin: process.env.CORS_ORIGIN || '*',
      credentials: true,
    }),
    json(),
    expressMiddleware(server, {
      context: async ({ req }) => {
        const user = await getUserFromRequest(req);
        return {
          user,
          prisma,
        };
      },
    })
  );
  
  // Health check endpoint
  app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
  });
  
  const PORT = process.env.PORT || 4000;
  
  await new Promise<void>((resolve) => httpServer.listen({ port: PORT }, resolve));
  
  console.log(`🚀 Server ready at http://localhost:${PORT}/graphql`);
  console.log(`🏥 Health check at http://localhost:${PORT}/health`);
}

// Handle graceful shutdown
process.on('SIGTERM', async () => {
  console.log('SIGTERM signal received: closing HTTP server');
  await prisma.$disconnect();
  httpServer.close(() => {
    console.log('HTTP server closed');
  });
});

process.on('SIGINT', async () => {
  console.log('SIGINT signal received: closing HTTP server');
  await prisma.$disconnect();
  httpServer.close(() => {
    console.log('HTTP server closed');
  });
});

startServer().catch((error) => {
  console.error('Failed to start server:', error);
  process.exit(1);
});

