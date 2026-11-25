package com.nicolasguillen.taletime.feature.shelf

import com.nicolasguillen.taletime.domain.UnavailablePurchases
import com.nicolasguillen.taletime.fakes.FakeProgress
import com.nicolasguillen.taletime.fakes.FakePurchases
import com.nicolasguillen.taletime.fakes.tinyLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfViewModelTest {
    private val main = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun withNoBooksTheShelfOpensOnTheStoreWhereAFreeBookIsFree() {
        val vm = ShelfViewModel(tinyLibrary(downloaded = emptyList()), UnavailablePurchases(), FakeProgress())
        val state = vm.state.value
        assertEquals(ShelfTab.STORE, state.tab)
        assertEquals(Price.Free, state.price(state.shown.single()))
    }

    @Test
    fun aPaidBookShowsTheStoresPriceUntilItsBought() {
        val purchases = FakePurchases(prices = mapOf("story.tiny" to "CHF 3.00"))
        val vm = ShelfViewModel(tinyLibrary(access = "paid", downloaded = emptyList()), purchases, FakeProgress())
        val forSale = vm.state.value
        assertEquals(Price.Paid("CHF 3.00"), forSale.price(forSale.store.single()))

        purchases.owned.value = setOf("story.tiny")
        val bought = vm.state.value
        assertEquals(null, bought.price(bought.store.single()))
    }

    @Test
    fun onceTheLastBookIsDeletedTheShelfGoesBackToTheStore() =
        runTest {
            val library = tinyLibrary()
            val vm = ShelfViewModel(library, UnavailablePurchases(), FakeProgress())
            assertEquals(ShelfTab.MY_BOOKS, vm.state.value.tab)
            vm.onEvent(ShelfEvent.Pick(ShelfTab.MY_BOOKS))

            library.deleteAll()
            assertEquals(ShelfTab.STORE, vm.state.value.tab)

            // A reader can still look at the empty My Books.
            vm.onEvent(ShelfEvent.Pick(ShelfTab.MY_BOOKS))
            assertEquals(ShelfTab.MY_BOOKS, vm.state.value.tab)
        }
}
