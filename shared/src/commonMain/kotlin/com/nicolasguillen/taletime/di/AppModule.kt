package com.nicolasguillen.taletime.di

import com.nicolasguillen.taletime.data.ContentSource
import com.nicolasguillen.taletime.data.HostedStoryLibrary
import com.nicolasguillen.taletime.data.local.DataStoreSettingsRepository
import com.nicolasguillen.taletime.data.local.RoomProgressRepository
import com.nicolasguillen.taletime.data.local.TaleTimeDatabase
import com.nicolasguillen.taletime.domain.ProgressRepository
import com.nicolasguillen.taletime.domain.Purchases
import com.nicolasguillen.taletime.domain.SettingsRepository
import com.nicolasguillen.taletime.domain.StoryLibrary
import com.nicolasguillen.taletime.domain.UnavailablePurchases
import com.nicolasguillen.taletime.feature.cover.CoverViewModel
import com.nicolasguillen.taletime.feature.parents.ParentsViewModel
import com.nicolasguillen.taletime.feature.reader.ReaderViewModel
import com.nicolasguillen.taletime.feature.shelf.ShelfViewModel
import com.nicolasguillen.taletime.platform.BuildInfo
import com.nicolasguillen.taletime.platform.LibraryDirectory
import com.nicolasguillen.taletime.platform.platformModule
import com.nicolasguillen.taletime.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dataModule =
    module {
        // Lives as long as the app: downloads and updates outlive the screen that started them.
        single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single<StoryLibrary> {
            HostedStoryLibrary(
                http = get(),
                fileSystem = FileSystem.SYSTEM,
                directory = get<LibraryDirectory>().path,
                // Debug builds read every story as it is (`story publish draft`); release builds, the finished ones.
                source = if (get<BuildInfo>().debug) ContentSource.Draft else ContentSource.Production,
                readBundledShelf = { Res.readBytes("files/shelf.json") },
                scope = get(),
            )
        }
        single<ProgressRepository> { RoomProgressRepository(get<TaleTimeDatabase>().progress()) }
        single<SettingsRepository> { DataStoreSettingsRepository(get()) }
        // Paid stories stay locked until StoreKit and Google Play Billing are wired in (plan.md Phase 4).
        single<Purchases> { UnavailablePurchases() }
    }

val featureModule =
    module {
        viewModelOf(::ShelfViewModel)
        viewModel { params ->
            CoverViewModel(storyId = params.get(), library = get(), purchases = get(), progress = get(), player = get())
        }
        viewModel { params ->
            ReaderViewModel(
                storyId = params.get(),
                restart = params.get(),
                library = get(),
                progress = get(),
                settings = get(),
                player = get(),
            )
        }
        viewModelOf(::ParentsViewModel)
    }

/** All Koin modules, in the order they're loaded. */
val appModules = listOf(platformModule, dataModule, featureModule)
