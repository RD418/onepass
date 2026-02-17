import * as OneSignal from 'onesignal-node';

const client = new OneSignal.Client(
  process.env.ONESIGNAL_APP_ID || '',
  process.env.ONESIGNAL_REST_API_KEY || ''
);

/**
 * Sends a push notification to specific users
 */
export async function sendNotificationToUsers(
  userIds: string[],
  title: string,
  message: string,
  data?: Record<string, any>
) {
  const notification = {
    headings: { en: title },
    contents: { en: message },
    include_external_user_ids: userIds,
    data: data || {},
  };

  try {
    const response = await client.createNotification(notification);
    return response.body;
  } catch (error) {
    console.error('OneSignal notification error:', error);
    throw error;
  }
}

/**
 * Sends a push notification to all subscribers of a specific segment
 */
export async function sendNotificationToSegment(
  segment: string,
  title: string,
  message: string,
  data?: Record<string, any>
) {
  const notification = {
    headings: { en: title },
    contents: { en: message },
    included_segments: [segment],
    data: data || {},
  };

  try {
    const response = await client.createNotification(notification);
    return response.body;
  } catch (error) {
    console.error('OneSignal notification error:', error);
    throw error;
  }
}

