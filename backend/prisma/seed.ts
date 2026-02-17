import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  console.log('🌱 Starting seed...');

  // Clean existing data (optional - comment out if you want to keep existing data)
  console.log('🧹 Cleaning existing data...');
  await prisma.deviceToken.deleteMany();
  await prisma.notification.deleteMany();
  await prisma.payment.deleteMany();
  await prisma.pass.deleteMany();
  await prisma.ticket.deleteMany();
  await prisma.eventImage.deleteMany();
  await prisma.eventTag.deleteMany();
  await prisma.eventPricingTier.deleteMany();
  await prisma.userFavoriteEvent.deleteMany();
  await prisma.event.deleteMany();
  await prisma.post.deleteMany();
  await prisma.organizationInvitation.deleteMany();
  await prisma.membership.deleteMany();
  await prisma.organization.deleteMany();
  await prisma.user.deleteMany();

  // Create Users
  console.log('👤 Creating users...');
  const user1 = await prisma.user.create({
    data: {
      uid: '550e8400-e29b-41d4-a716-446655440001',
      email: 'alice@example.com',
      displayName: 'Alice Johnson',
      bio: 'Event organizer and music enthusiast',
      avatarUrl: 'https://i.pravatar.cc/150?img=1',
      country: 'CH',
      status: 'ACTIVE',
      showEmail: false,
      analyticsEnabled: true,
    },
  });

  const user2 = await prisma.user.create({
    data: {
      uid: '550e8400-e29b-41d4-a716-446655440002',
      email: 'bob@example.com',
      displayName: 'Bob Smith',
      bio: 'Tech conference organizer',
      avatarUrl: 'https://i.pravatar.cc/150?img=2',
      country: 'CH',
      status: 'ACTIVE',
      showEmail: true,
      analyticsEnabled: true,
    },
  });

  const user3 = await prisma.user.create({
    data: {
      uid: '550e8400-e29b-41d4-a716-446655440003',
      email: 'charlie@example.com',
      displayName: 'Charlie Davis',
      bio: 'Art gallery curator',
      avatarUrl: 'https://i.pravatar.cc/150?img=3',
      country: 'FR',
      status: 'ACTIVE',
      showEmail: false,
      analyticsEnabled: true,
    },
  });

  const user4 = await prisma.user.create({
    data: {
      uid: '550e8400-e29b-41d4-a716-446655440004',
      email: 'diana@example.com',
      displayName: 'Diana Prince',
      bio: 'Event attendee and networking enthusiast',
      avatarUrl: 'https://i.pravatar.cc/150?img=4',
      country: 'CH',
      status: 'ACTIVE',
      showEmail: false,
      analyticsEnabled: true,
    },
  });

  console.log(`✅ Created ${4} users`);

  // Create Organizations
  console.log('🏢 Creating organizations...');
  const org1 = await prisma.organization.create({
    data: {
      name: 'Lausanne Music Festival',
      description: 'Annual music festival featuring local and international artists',
      ownerId: user1.uid,
      status: 'ACTIVE',
      verified: true,
      profileImageUrl: 'https://picsum.photos/seed/org1/400/400',
      coverImageUrl: 'https://picsum.photos/seed/org1cover/1200/400',
      website: 'https://lausannemusicfest.ch',
      instagram: '@lausannemusicfest',
      contactEmail: 'info@lausannemusicfest.ch',
      contactPhone: '+41123456789',
      phonePrefix: '+41',
      address: 'Place de la Navigation, 1006 Lausanne',
      followerCount: 2500,
      averageRating: 4.7,
    },
  });

  const org2 = await prisma.organization.create({
    data: {
      name: 'Tech Talks Lausanne',
      description: 'Monthly tech meetups and conferences for developers and entrepreneurs',
      ownerId: user2.uid,
      status: 'ACTIVE',
      verified: true,
      profileImageUrl: 'https://picsum.photos/seed/org2/400/400',
      coverImageUrl: 'https://picsum.photos/seed/org2cover/1200/400',
      website: 'https://techtalkslausanne.ch',
      instagram: '@techtalkslausanne',
      contactEmail: 'hello@techtalkslausanne.ch',
      followerCount: 1200,
      averageRating: 4.5,
    },
  });

  const org3 = await prisma.organization.create({
    data: {
      name: 'Geneva Art Gallery',
      description: 'Contemporary art exhibitions and cultural events',
      ownerId: user3.uid,
      status: 'ACTIVE',
      verified: false,
      profileImageUrl: 'https://picsum.photos/seed/org3/400/400',
      coverImageUrl: 'https://picsum.photos/seed/org3cover/1200/400',
      website: 'https://genevaartgallery.ch',
      instagram: '@genevaartgallery',
      contactEmail: 'contact@genevaartgallery.ch',
      address: 'Rue du Rhône 45, 1204 Genève',
      followerCount: 850,
      averageRating: 4.8,
    },
  });

  console.log(`✅ Created ${3} organizations`);

  // Create Memberships
  console.log('👥 Creating memberships...');
  await prisma.membership.create({
    data: {
      userId: user1.uid,
      organizationId: org1.id,
      role: 'OWNER',
      status: 'ACTIVE',
    },
  });

  await prisma.membership.create({
    data: {
      userId: user2.uid,
      organizationId: org2.id,
      role: 'OWNER',
      status: 'ACTIVE',
    },
  });

  await prisma.membership.create({
    data: {
      userId: user3.uid,
      organizationId: org3.id,
      role: 'OWNER',
      status: 'ACTIVE',
    },
  });

  // Add user4 as staff to org1
  await prisma.membership.create({
    data: {
      userId: user4.uid,
      organizationId: org1.id,
      role: 'STAFF',
      status: 'ACTIVE',
    },
  });

  console.log(`✅ Created memberships`);

  // Create Events
  console.log('🎉 Creating events...');
  
  // Event 1: Summer Music Festival (Future, Published)
  const event1 = await prisma.event.create({
    data: {
      title: 'Summer Music Festival 2026',
      description: 'Join us for three days of incredible music featuring over 50 artists from around the world. Experience multiple stages, food trucks, and camping options.',
      organizerId: org1.id,
      organizerName: org1.name,
      status: 'PUBLISHED',
      locationName: 'Parc de la Grange, Genève',
      locationPoint: 'SRID=4326;POINT(6.1624 46.1995)',
      startTime: new Date('2026-07-15T14:00:00Z'),
      endTime: new Date('2026-07-17T23:00:00Z'),
      capacity: 5000,
      ticketsRemaining: 3245,
      ticketsIssued: 1755,
      ticketsRedeemed: 0,
      currency: 'CHF',
    },
  });

  await prisma.eventPricingTier.createMany({
    data: [
      {
        eventId: event1.id,
        name: 'Early Bird',
        price: 89.00,
        quantity: 1000,
        remaining: 0, // Sold out
      },
      {
        eventId: event1.id,
        name: 'General Admission',
        price: 129.00,
        quantity: 3000,
        remaining: 2245,
      },
      {
        eventId: event1.id,
        name: 'VIP Pass',
        price: 299.00,
        quantity: 1000,
        remaining: 1000,
      },
    ],
  });

  await prisma.eventImage.createMany({
    data: [
      { eventId: event1.id, imageUrl: 'https://picsum.photos/seed/event1a/800/600', displayOrder: 0 },
      { eventId: event1.id, imageUrl: 'https://picsum.photos/seed/event1b/800/600', displayOrder: 1 },
      { eventId: event1.id, imageUrl: 'https://picsum.photos/seed/event1c/800/600', displayOrder: 2 },
    ],
  });

  await prisma.eventTag.createMany({
    data: [
      { eventId: event1.id, tag: 'music' },
      { eventId: event1.id, tag: 'festival' },
      { eventId: event1.id, tag: 'outdoor' },
      { eventId: event1.id, tag: 'summer' },
    ],
  });

  // Event 2: Tech Conference (Future, Published)
  const event2 = await prisma.event.create({
    data: {
      title: 'SwissTech Conference 2026',
      description: 'Premier technology conference featuring keynotes from industry leaders, workshops, and networking opportunities.',
      organizerId: org2.id,
      organizerName: org2.name,
      status: 'PUBLISHED',
      locationName: 'SwissTech Convention Center, Lausanne',
      locationPoint: 'SRID=4326;POINT(6.5657 46.5197)',
      startTime: new Date('2026-09-20T09:00:00Z'),
      endTime: new Date('2026-09-22T18:00:00Z'),
      capacity: 800,
      ticketsRemaining: 320,
      ticketsIssued: 480,
      ticketsRedeemed: 0,
      currency: 'CHF',
    },
  });

  await prisma.eventPricingTier.createMany({
    data: [
      {
        eventId: event2.id,
        name: 'Student Ticket',
        price: 49.00,
        quantity: 200,
        remaining: 120,
      },
      {
        eventId: event2.id,
        name: 'Standard Pass',
        price: 249.00,
        quantity: 500,
        remaining: 200,
      },
      {
        eventId: event2.id,
        name: 'Premium Pass',
        price: 499.00,
        quantity: 100,
        remaining: 0, // Sold out
      },
    ],
  });

  await prisma.eventImage.createMany({
    data: [
      { eventId: event2.id, imageUrl: 'https://picsum.photos/seed/event2a/800/600', displayOrder: 0 },
      { eventId: event2.id, imageUrl: 'https://picsum.photos/seed/event2b/800/600', displayOrder: 1 },
    ],
  });

  await prisma.eventTag.createMany({
    data: [
      { eventId: event2.id, tag: 'technology' },
      { eventId: event2.id, tag: 'conference' },
      { eventId: event2.id, tag: 'networking' },
      { eventId: event2.id, tag: 'innovation' },
    ],
  });

  // Event 3: Art Exhibition (Future, Published)
  const event3 = await prisma.event.create({
    data: {
      title: 'Contemporary Art Expo 2026',
      description: 'Discover the latest works from emerging and established artists. Gallery tours, artist talks, and wine reception included.',
      organizerId: org3.id,
      organizerName: org3.name,
      status: 'PUBLISHED',
      locationName: 'Geneva Art Gallery',
      locationPoint: 'SRID=4326;POINT(6.1432 46.2044)',
      startTime: new Date('2026-06-10T18:00:00Z'),
      endTime: new Date('2026-06-10T22:00:00Z'),
      capacity: 200,
      ticketsRemaining: 85,
      ticketsIssued: 115,
      ticketsRedeemed: 0,
      currency: 'CHF',
    },
  });

  await prisma.eventPricingTier.createMany({
    data: [
      {
        eventId: event3.id,
        name: 'General Entry',
        price: 25.00,
        quantity: 150,
        remaining: 75,
      },
      {
        eventId: event3.id,
        name: 'VIP Experience',
        price: 75.00,
        quantity: 50,
        remaining: 10,
      },
    ],
  });

  await prisma.eventImage.createMany({
    data: [
      { eventId: event3.id, imageUrl: 'https://picsum.photos/seed/event3a/800/600', displayOrder: 0 },
    ],
  });

  await prisma.eventTag.createMany({
    data: [
      { eventId: event3.id, tag: 'art' },
      { eventId: event3.id, tag: 'exhibition' },
      { eventId: event3.id, tag: 'gallery' },
      { eventId: event3.id, tag: 'culture' },
    ],
  });

  // Event 4: Draft Event (Not yet published)
  const event4 = await prisma.event.create({
    data: {
      title: 'Winter Jazz Night',
      description: 'Intimate jazz performance featuring local musicians. Draft event - details being finalized.',
      organizerId: org1.id,
      organizerName: org1.name,
      status: 'DRAFT',
      locationName: 'Lausanne Jazz Club',
      locationPoint: 'SRID=4326;POINT(6.6323 46.5197)',
      startTime: new Date('2026-12-15T20:00:00Z'),
      endTime: new Date('2026-12-15T23:30:00Z'),
      capacity: 150,
      ticketsRemaining: 150,
      ticketsIssued: 0,
      ticketsRedeemed: 0,
      currency: 'CHF',
    },
  });

  await prisma.eventPricingTier.create({
    data: {
      eventId: event4.id,
      name: 'Standard',
      price: 35.00,
      quantity: 150,
      remaining: 150,
    },
  });

  await prisma.eventTag.createMany({
    data: [
      { eventId: event4.id, tag: 'music' },
      { eventId: event4.id, tag: 'jazz' },
      { eventId: event4.id, tag: 'indoor' },
    ],
  });

  console.log(`✅ Created ${4} events with pricing tiers, images, and tags`);

  // Create some test tickets for user4
  console.log('🎫 Creating test tickets...');
  const tier1 = await prisma.eventPricingTier.findFirst({
    where: { eventId: event1.id, name: 'General Admission' },
  });

  const tier2 = await prisma.eventPricingTier.findFirst({
    where: { eventId: event2.id, name: 'Standard Pass' },
  });

  if (tier1) {
    await prisma.ticket.create({
      data: {
        eventId: event1.id,
        ownerId: user4.uid,
        tierId: tier1.id,
        purchasePrice: tier1.price,
        currency: 'CHF',
        state: 'ISSUED',
      },
    });
  }

  if (tier2) {
    await prisma.ticket.create({
      data: {
        eventId: event2.id,
        ownerId: user4.uid,
        tierId: tier2.id,
        purchasePrice: tier2.price,
        currency: 'CHF',
        state: 'ISSUED',
      },
    });
  }

  console.log(`✅ Created test tickets`);

  // Create favorite events
  console.log('⭐ Creating favorite events...');
  await prisma.userFavoriteEvent.createMany({
    data: [
      { userId: user4.uid, eventId: event1.id },
      { userId: user4.uid, eventId: event3.id },
      { userId: user1.uid, eventId: event2.id },
    ],
  });

  console.log(`✅ Created favorite events`);

  // Create organization posts
  console.log('📝 Creating organization posts...');
  await prisma.post.createMany({
    data: [
      {
        organizationId: org1.id,
        title: 'Lineup Announcement!',
        content: 'We are thrilled to announce the first wave of artists for Summer Music Festival 2026! Stay tuned for more announcements.',
        imageUrl: 'https://picsum.photos/seed/post1/800/400',
      },
      {
        organizationId: org1.id,
        title: 'Early Bird Tickets Sold Out!',
        content: 'Thank you for the amazing support! Early bird tickets are sold out. General admission tickets are still available.',
      },
      {
        organizationId: org2.id,
        title: 'Speaker Spotlight: Dr. Emma Wilson',
        content: 'Meet our keynote speaker! Dr. Emma Wilson will be discussing the future of AI in healthcare.',
        imageUrl: 'https://picsum.photos/seed/post2/800/400',
      },
      {
        organizationId: org3.id,
        title: 'New Exhibition Opens June 10',
        content: 'Join us for the opening night of our Contemporary Art Expo featuring 25 talented artists.',
        imageUrl: 'https://picsum.photos/seed/post3/800/400',
      },
    ],
  });

  console.log(`✅ Created organization posts`);

  // Create notifications
  console.log('🔔 Creating notifications...');
  await prisma.notification.createMany({
    data: [
      {
        userId: user4.uid,
        type: 'TICKET_PURCHASE',
        title: 'Ticket Purchase Confirmed',
        message: 'Your ticket for Summer Music Festival 2026 has been confirmed!',
        data: { eventId: event1.id, ticketId: 'ticket-id-placeholder' },
        read: false,
      },
      {
        userId: user4.uid,
        type: 'EVENT_REMINDER',
        title: 'Event Coming Up!',
        message: 'Summer Music Festival 2026 is coming up in 30 days!',
        data: { eventId: event1.id },
        read: false,
      },
      {
        userId: user1.uid,
        type: 'EVENT_UPDATE',
        title: 'New Artist Added',
        message: 'A new artist has been added to the lineup for Summer Music Festival 2026',
        data: { eventId: event1.id },
        read: true,
      },
    ],
  });

  console.log(`✅ Created notifications`);

  // Create organization invitation
  console.log('✉️ Creating organization invitation...');
  await prisma.organizationInvitation.create({
    data: {
      orgId: org1.id,
      inviteeEmail: user3.email,
      role: 'MEMBER',
      invitedBy: user1.uid,
      status: 'PENDING',
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days from now
    },
  });

  console.log(`✅ Created organization invitation`);

  console.log('');
  console.log('🎉 Seed completed successfully!');
  console.log('');
  console.log('📊 Summary:');
  console.log('  • 4 users created');
  console.log('  • 3 organizations created');
  console.log('  • 4 events created (3 published, 1 draft)');
  console.log('  • Multiple pricing tiers, images, and tags');
  console.log('  • 2 test tickets for user4');
  console.log('  • Favorite events, posts, notifications, and invitations');
  console.log('');
  console.log('🔑 Test User Credentials (UIDs):');
  console.log(`  Alice (Org Owner):   ${user1.uid}`);
  console.log(`  Bob (Org Owner):     ${user2.uid}`);
  console.log(`  Charlie (Org Owner): ${user3.uid}`);
  console.log(`  Diana (Attendee):    ${user4.uid}`);
  console.log('');
  console.log('💡 Next steps:');
  console.log('  1. Start the dev server: npm run dev');
  console.log('  2. Open Apollo Studio: http://localhost:4000/graphql');
  console.log('  3. Try queries like: featuredEvents, events, searchOrganizations');
  console.log('  4. Use Prisma Studio to view data: npm run prisma:studio');
}

main()
  .catch((e) => {
    console.error('❌ Error during seed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
