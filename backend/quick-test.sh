#!/bin/bash

# Quick test script for GraphQL API
# This script helps verify your setup step by step

echo "🚀 OnePass GraphQL API - Quick Test"
echo "===================================="
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "❌ .env file not found!"
    echo "   Please copy .env.example to .env and configure it"
    exit 1
fi

echo "✅ .env file found"
echo ""

# Check if node_modules exists
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
    if [ $? -ne 0 ]; then
        echo "❌ Failed to install dependencies"
        exit 1
    fi
    echo "✅ Dependencies installed"
else
    echo "✅ Dependencies already installed"
fi
echo ""

# Check if Prisma client is generated
if [ ! -d "node_modules/.prisma" ]; then
    echo "🔧 Generating Prisma client..."
    npm run prisma:generate
    if [ $? -ne 0 ]; then
        echo "❌ Failed to generate Prisma client"
        exit 1
    fi
    echo "✅ Prisma client generated"
else
    echo "✅ Prisma client ready"
fi
echo ""

# Check database connection
echo "🔍 Checking database connection..."
PGPASSWORD=onepass_password psql -h localhost -U onepass_user -d onepass -c "SELECT 1;" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Database connection successful"
else
    echo "⚠️  Database connection failed"
    echo "   Make sure Docker is running: docker-compose up -d"
    echo "   Or check your DATABASE_URL in .env"
fi
echo ""

# Check if migrations have been run
MIGRATION_COUNT=$(PGPASSWORD=onepass_password psql -h localhost -U onepass_user -d onepass -t -c "SELECT COUNT(*) FROM _prisma_migrations;" 2>/dev/null | xargs)
if [ "$MIGRATION_COUNT" = "" ] || [ "$MIGRATION_COUNT" = "0" ]; then
    echo "📊 Running database migrations..."
    npm run prisma:migrate
    if [ $? -ne 0 ]; then
        echo "❌ Migration failed"
        exit 1
    fi
    echo "✅ Migrations completed"
else
    echo "✅ Database migrations already applied ($MIGRATION_COUNT migrations)"
fi
echo ""

echo "===================================="
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "1. Start the server: npm run dev"
echo "2. Open http://localhost:4000/graphql in your browser"
echo "3. Try the test queries from TESTING.md"
echo ""

