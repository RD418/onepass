/**
 * Simple GraphQL test client
 * Usage: npx ts-node test-graphql.ts
 * Requires Node.js 18+ for built-in fetch API
 */

/// <reference types="node" />

const GRAPHQL_ENDPOINT = (process.env.GRAPHQL_URL || 'http://localhost:4000/graphql') as string;

interface GraphQLRequest {
  query: string;
  variables?: Record<string, any>;
}

interface GraphQLResponse {
  data?: any;
  errors?: Array<{
    message: string;
    locations?: Array<{ line: number; column: number }>;
    path?: Array<string | number>;
  }>;
}

async function graphqlRequest(
  query: string,
  variables?: Record<string, any>,
  token?: string
): Promise<GraphQLResponse> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(GRAPHQL_ENDPOINT, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      query,
      variables,
    }),
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  return await response.json();
}

// Example queries
async function testQueries() {
  console.log('🧪 Testing GraphQL API...\n');

  try {
    // Test 1: Simple query (no auth required)
    console.log('1️⃣ Testing: Get Featured Events');
    const featuredEventsQuery = `
      query GetFeaturedEvents {
        featuredEvents {
          id
          title
          description
          organizerName
          startTime
          ticketsRemaining
          lowestPrice
          isSoldOut
          isPublished
        }
      }
    `;

    const result1 = await graphqlRequest(featuredEventsQuery);
    if (result1.errors) {
      console.error('❌ Errors:', JSON.stringify(result1.errors, null, 2));
    } else {
      console.log('✅ Success:', JSON.stringify(result1.data, null, 2));
    }

    console.log('\n' + '='.repeat(50) + '\n');

    // Test 2: Get events
    console.log('2️⃣ Testing: Get Events (paginated)');
    const eventsQuery = `
      query GetEvents {
        events(first: 5) {
          edges {
            node {
              id
              title
              description
              status
              organizerName
              startTime
              ticketsRemaining
            }
          }
          pageInfo {
            hasNextPage
            hasPreviousPage
          }
          totalCount
        }
      }
    `;

    const result2 = await graphqlRequest(eventsQuery);
    if (result2.errors) {
      console.error('❌ Errors:', JSON.stringify(result2.errors, null, 2));
    } else {
      console.log('✅ Success:', JSON.stringify(result2.data, null, 2));
    }

    console.log('\n' + '='.repeat(50) + '\n');

    // Test 3: Authenticated query (if you have a token)
    const token = process.env.AUTH_TOKEN;
    if (token) {
      console.log('3️⃣ Testing: Get Current User (Authenticated)');
      const meQuery = `
        query GetMe {
          me {
            uid
            email
            displayName
            bio
            avatarUrl
            status
            createdAt
          }
        }
      `;

      const result3 = await graphqlRequest(meQuery, undefined, token);
      if (result3.errors) {
        console.error('❌ Errors:', JSON.stringify(result3.errors, null, 2));
      } else {
        console.log('✅ Success:', JSON.stringify(result3.data, null, 2));
      }
    } else {
      console.log('3️⃣ Skipping authenticated query (no AUTH_TOKEN provided)');
      console.log('   Set AUTH_TOKEN environment variable to test authenticated queries');
    }

  } catch (error) {
    console.error('❌ Request failed:', error);
  }
}

// Run if called directly
if (typeof require !== 'undefined' && require.main === module) {
  testQueries().catch(console.error);
}

export { graphqlRequest };
