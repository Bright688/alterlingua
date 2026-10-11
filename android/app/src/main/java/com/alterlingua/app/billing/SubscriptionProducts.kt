package com.alterlingua.app.billing

import com.alterlingua.app.entitlements.Tier

/** How long a subscription offer renews for. */
enum class BillingPeriod { MONTHLY, ANNUAL }

/**
 * Standard and Premium are two separate, independently-billed Google Play subscription products — not one plan
 * with an add-on — each with its own monthly and annual offer (CLAUDE.md section 32).
 *
 * These product IDs are placeholders: nothing with these exact IDs exists in Google Play Console yet. Google Play
 * will report them as unavailable (not an error — see [BillingManager.offers]) until matching subscription products
 * are created there with these same IDs. No other code needs to change once that happens.
 */
object SubscriptionProducts {
    const val STANDARD_MONTHLY = "standard_monthly"
    const val STANDARD_ANNUAL = "standard_annual"
    const val PREMIUM_MONTHLY = "premium_monthly"
    const val PREMIUM_ANNUAL = "premium_annual"

    /** Every product ID this app knows how to sell, so a single query can ask Play Console for all of them at once. */
    val all: List<String> = listOf(STANDARD_MONTHLY, STANDARD_ANNUAL, PREMIUM_MONTHLY, PREMIUM_ANNUAL)

    /** Which tier owning [productId] grants, or null if it is not one of ours. */
    fun tierFor(productId: String): Tier? = when (productId) {
        STANDARD_MONTHLY, STANDARD_ANNUAL -> Tier.STANDARD
        PREMIUM_MONTHLY, PREMIUM_ANNUAL -> Tier.PREMIUM
        else -> null
    }

    /** This tier's own two product IDs (monthly, annual), or empty for FREE, which is never purchased. */
    fun idsFor(tier: Tier): List<String> = when (tier) {
        Tier.FREE -> emptyList()
        Tier.STANDARD -> listOf(STANDARD_MONTHLY, STANDARD_ANNUAL)
        Tier.PREMIUM -> listOf(PREMIUM_MONTHLY, PREMIUM_ANNUAL)
    }
}

/** One purchasable offer, as Google Play actually reports it (a real price, not a guess). */
data class SubscriptionOffer(
    val productId: String,
    val tier: Tier,
    val period: BillingPeriod,
    /** The price formatted the way Play Console configured it (currency, locale) — never hard-coded here. */
    val formattedPrice: String,
    /** Play's own token identifying this specific offer; required to actually launch a purchase for it. */
    val offerToken: String,
)

/** What a subscription tier's Google Play catalog looks like right now. */
data class TierOffers(val tier: Tier, val monthly: SubscriptionOffer?, val annual: SubscriptionOffer?) {
    val available: Boolean get() = monthly != null || annual != null
}
