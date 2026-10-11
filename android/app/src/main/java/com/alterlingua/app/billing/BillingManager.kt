package com.alterlingua.app.billing

import android.app.Activity
import android.content.Context
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.storage.UserSettingsRepository
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Buys and tracks the Standard and Premium subscriptions (CLAUDE.md section 32) — two separate Google Play
 * products, each with its own monthly/annual offer, never one plan with an add-on. Scaffolding until real Play
 * Console products exist with the IDs in [SubscriptionProducts]: Google Play will simply report nothing for them
 * (an empty [offers] list, not an error) until then.
 */
interface BillingManager {
    /** What Google Play's catalog looks like right now for Standard and Premium. Empty until products exist there. */
    val offers: StateFlow<List<TierOffers>>

    /** True once the first connection attempt has finished (connected or not) — lets the UI stop showing "loading". */
    val ready: StateFlow<Boolean>

    /** Starts Google Play's own purchase sheet for this offer. No-op if billing is not actually connected. */
    fun purchase(activity: Activity, offer: SubscriptionOffer)
}

class PlayBillingManager(
    context: Context,
    private val settings: UserSettingsRepository,
    private val scope: CoroutineScope,
) : BillingManager {

    private val offersState = MutableStateFlow<List<TierOffers>>(emptyList())
    override val offers: StateFlow<List<TierOffers>> = offersState

    private val readyState = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = readyState

    /** The real Play objects behind each offer, kept only because launchBillingFlow needs the ProductDetails itself. */
    private val rawDetails = mutableMapOf<String, ProductDetails>()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach(::handle)
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(listener)
        .enableAutoServiceReconnection()
        // Required by BillingClient.Builder.build() itself (throws IllegalArgumentException without it), even
        // though AlterLingua only sells subscriptions: Play Billing 9.x mandates declaring pending-purchases
        // support for one-time products regardless of whether the app currently offers any.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                readyState.value = true
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryOffers()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                readyState.value = false
            }
        })
    }

    override fun purchase(activity: Activity, offer: SubscriptionOffer) {
        val details = rawDetails[offer.productId] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun queryOffers() {
        val products = SubscriptionProducts.all.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val found = queryResult.productDetailsList.orEmpty().flatMap { details ->
                rawDetails[details.productId] = details
                offersFrom(details)
            }
            offersState.value = listOf(Tier.STANDARD, Tier.PREMIUM).map { tier ->
                TierOffers(
                    tier = tier,
                    monthly = found.firstOrNull { it.tier == tier && it.period == BillingPeriod.MONTHLY },
                    annual = found.firstOrNull { it.tier == tier && it.period == BillingPeriod.ANNUAL },
                )
            }
        }
    }

    private fun offersFrom(details: ProductDetails): List<SubscriptionOffer> {
        val tier = SubscriptionProducts.tierFor(details.productId) ?: return emptyList()
        return details.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
            val period = when (phase.billingPeriod) {
                "P1M" -> BillingPeriod.MONTHLY
                "P1Y" -> BillingPeriod.ANNUAL
                else -> return@mapNotNull null
            }
            SubscriptionOffer(details.productId, tier, period, phase.formattedPrice, offer.offerToken)
        }
    }

    /** Reconciles the saved tier with Play's own purchase records on every connect, not just at purchase time. */
    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::handle)
            }
        }
    }

    private fun handle(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val tier = purchase.products.firstNotNullOfOrNull(SubscriptionProducts::tierFor) ?: return
        scope.launch {
            settings.update { it.copy(tier = tier) }
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                client.acknowledgePurchase(ackParams) { }
            }
        }
    }
}
