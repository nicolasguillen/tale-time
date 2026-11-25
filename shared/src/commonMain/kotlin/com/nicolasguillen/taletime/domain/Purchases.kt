package com.nicolasguillen.taletime.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A store product, with the price the store shows in the family's own currency. */
data class Product(
    val id: String,
    val price: String,
)

sealed interface PurchaseResult {
    data object Bought : PurchaseResult

    data object Cancelled : PurchaseResult

    /**
     * The store can't sell right now (no store connection yet, offline, parental controls).
     *
     * @property reason what the store said, in the device's language, or null: the app then says the store isn't
     *   available, in its own language.
     */
    data class Unavailable(
        val reason: String? = null,
    ) : PurchaseResult
}

/**
 * The App Store and Google Play, for paid stories: one-time purchases, never subscriptions or consumables.
 * Every call starts behind the parental gate. Nothing about the family is sent anywhere by the app itself:
 * the stores handle the payment and remember what this account owns.
 */
interface Purchases {
    /** Product ids this account owns. */
    val owned: Flow<Set<String>>

    /** Prices for [ids]; products the store doesn't know are left out. */
    suspend fun products(ids: List<String>): List<Product>

    suspend fun buy(productId: String): PurchaseResult

    /** Asks the store again what this account owns (a new device, a reinstall). */
    suspend fun restore()
}

/**
 * Until StoreKit and Google Play Billing are wired in (plan.md §11, Phase 4): nothing is owned and nothing can be
 * bought, so paid stories stay locked. Free stories don't need the store.
 */
class UnavailablePurchases : Purchases {
    private val none = MutableStateFlow(emptySet<String>())
    override val owned: StateFlow<Set<String>> = none

    override suspend fun products(ids: List<String>): List<Product> = emptyList()

    override suspend fun buy(productId: String): PurchaseResult = PurchaseResult.Unavailable()

    override suspend fun restore() = Unit
}
