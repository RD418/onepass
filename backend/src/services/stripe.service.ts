import Stripe from 'stripe';

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY || '', {
  apiVersion: '2023-10-16',
});

export { stripe };

/**
 * Creates a payment intent for ticket purchase
 */
export async function createPaymentIntent(
  amount: number,
  currency: string,
  customerId?: string,
  metadata?: Record<string, string>
): Promise<Stripe.PaymentIntent> {
  return await stripe.paymentIntents.create({
    amount: Math.round(amount * 100), // Convert to cents
    currency: currency.toLowerCase(),
    customer: customerId,
    metadata,
  });
}

/**
 * Creates a payment intent for marketplace transactions
 */
export async function createMarketplacePaymentIntent(
  amount: number,
  currency: string,
  connectedAccountId: string,
  applicationFeeAmount: number,
  metadata?: Record<string, string>
): Promise<Stripe.PaymentIntent> {
  return await stripe.paymentIntents.create({
    amount: Math.round(amount * 100),
    currency: currency.toLowerCase(),
    application_fee_amount: Math.round(applicationFeeAmount * 100),
    transfer_data: {
      destination: connectedAccountId,
    },
    metadata,
  });
}

