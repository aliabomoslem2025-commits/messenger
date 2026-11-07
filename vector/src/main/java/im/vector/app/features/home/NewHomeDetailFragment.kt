/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home

import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.airbnb.mvrx.activityViewModel
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.SpaceStateHandler
import im.vector.app.core.extensions.commitTransaction
import im.vector.app.core.platform.OnBackPressed
import im.vector.app.core.platform.VectorBaseActivity
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.platform.VectorMenuProvider
import im.vector.app.core.resources.ColorProvider
import im.vector.app.core.ui.views.CurrentCallsView
import im.vector.app.core.ui.views.CurrentCallsViewPresenter
import im.vector.app.databinding.FragmentNewHomeDetailBinding
import im.vector.app.features.call.SharedKnownCallsViewModel
import im.vector.app.features.call.VectorCallActivity
import im.vector.app.features.call.dialpad.PstnDialActivity
import im.vector.app.features.call.webrtc.WebRtcCallManager
import im.vector.app.features.contactsbook.ContactsBookFragment
import im.vector.app.features.crypto.verification.self.SelfVerificationBottomSheet
import im.vector.app.features.home.room.list.UnreadCounterBadgeView
import im.vector.app.features.home.room.list.actions.RoomListSharedAction
import im.vector.app.features.home.room.list.actions.RoomListSharedActionViewModel
import im.vector.app.features.home.room.list.home.HomeRoomListAction
import im.vector.app.features.home.room.list.home.HomeRoomListFragment
import im.vector.app.features.home.room.list.home.HomeRoomListViewModel
import im.vector.app.features.home.room.list.home.HomeRoomListViewState
import im.vector.app.features.home.room.list.home.header.HomeRoomFilter
import im.vector.app.features.home.room.list.home.NewChatBottomSheet
import im.vector.app.features.media.ImageContentRenderer
import im.vector.app.features.popup.DefaultVectorAlert
import im.vector.app.features.popup.PopupAlertManager
import im.vector.app.features.popup.VerificationVectorAlert
import im.vector.app.features.qrcode.QrCodeScannerActivity
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.settings.VectorSettingsActivity.Companion.EXTRA_DIRECT_ACCESS_SECURITY_PRIVACY_MANAGE_SESSIONS
import im.vector.app.features.spaces.SpaceListBottomSheet
import im.vector.app.features.workers.signout.BannerState
import im.vector.app.features.workers.signout.ServerBackupStatusAction
import im.vector.app.features.workers.signout.ServerBackupStatusViewModel
import im.vector.lib.strings.CommonStrings
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.session.crypto.model.DeviceInfo
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import javax.inject.Inject

@AndroidEntryPoint
class NewHomeDetailFragment :
        VectorBaseFragment<FragmentNewHomeDetailBinding>(),
        CurrentCallsView.Callback,
        OnBackPressed,
        VectorMenuProvider {

    @Inject lateinit var avatarRenderer: AvatarRenderer
    @Inject lateinit var colorProvider: ColorProvider
    @Inject lateinit var alertManager: PopupAlertManager
    @Inject lateinit var callManager: WebRtcCallManager
    @Inject lateinit var vectorPreferences: VectorPreferences
    @Inject lateinit var spaceStateHandler: SpaceStateHandler
    @Inject lateinit var chatFoldersStore: ChatFoldersStore
    @Inject lateinit var telegramStoriesRepository: TelegramStoriesRepository
    @Inject lateinit var imageContentRenderer: ImageContentRenderer

    private val viewModel: HomeDetailViewModel by fragmentViewModel()
    private val newHomeDetailViewModel: NewHomeDetailViewModel by fragmentViewModel()
    private val homeRoomListViewModel: HomeRoomListViewModel by activityViewModel()

    private var isSyncingTelegramHomeTabs = false
    private var activeHomeRoomFilter: HomeRoomFilter = HomeRoomFilter.ALL
    private var pendingStoryEventId: String? = null
    private var pendingStoryShouldCreate = false
    private var isVerifyDeviceAlertPosted = false
    private var isKeysBackupAlertPosted = false
    private var homeSearchTextWatcher: TextWatcher? = null
    private val unknownDeviceDetectorSharedViewModel: UnknownDeviceDetectorSharedViewModel by activityViewModel()
    private val serverBackupStatusViewModel: ServerBackupStatusViewModel by activityViewModel()

    private lateinit var sharedActionViewModel: HomeSharedActionViewModel
    private lateinit var sharedRoomListActionViewModel: RoomListSharedActionViewModel
    private lateinit var sharedCallActionViewModel: SharedKnownCallsViewModel

    private val newChatBottomSheet = NewChatBottomSheet()
    private val spaceListBottomSheet = SpaceListBottomSheet()

    private var spacesUnreadLabel: String? = null

    private var hasUnreadRooms = false
        set(value) {
            if (value != field) {
                field = value
                invalidateOptionsMenu()
            }
        }

    override fun getMenuRes() = R.menu.room_list

    override fun handleMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_home_mark_all_as_read -> {
                viewModel.handle(HomeDetailAction.MarkAllRoomsRead)
                true
            }
            R.id.menu_home_dialpad -> {
                startActivity(Intent(requireContext(), PstnDialActivity::class.java))
                true
            }
            R.id.menu_home_spaces -> {
                spaceListBottomSheet.takeIf { !it.isAdded }?.show(requireActivity().supportFragmentManager, SpaceListBottomSheet.TAG)
                true
            }
            R.id.menu_home_open_drawer -> {
                sharedActionViewModel.post(HomeActivitySharedAction.OpenDrawer)
                true
            }
            R.id.menu_home_search -> true // The persistent search input handles the query.
            else -> false
        }
    }

    override fun handlePrepareMenu(menu: Menu) {
        withState(viewModel) { state ->
            val isRoomList = state.currentTab is HomeTab.RoomList
            menu.findItem(R.id.menu_home_mark_all_as_read).isVisible = isRoomList && hasUnreadRooms
            menu.findItem(R.id.menu_home_dialpad).isVisible = state.showDialPadTab
            val spacesItem = menu.findItem(R.id.menu_home_spaces)
            spacesItem.isVisible = isRoomList
            val spacesTitle = getString(CommonStrings.a11y_open_spaces)
            spacesItem.title = spacesUnreadLabel?.let { "$spacesTitle ($it)" } ?: spacesTitle

            // Search is a persistent input below the toolbar, not an overflow action.
            menu.findItem(R.id.menu_home_search).isVisible = false
        }
    }

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentNewHomeDetailBinding {
        return FragmentNewHomeDetailBinding.inflate(inflater, container, false)
    }

    private val currentCallsViewPresenter = CurrentCallsViewPresenter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedActionViewModel = activityViewModelProvider.get(HomeSharedActionViewModel::class.java)
        sharedCallActionViewModel = activityViewModelProvider.get(SharedKnownCallsViewModel::class.java)
        setupToolbar()
        setupKeysBackupAlerts()
        setupActiveCallView()
        setupDebugButton()
        setupFabs()
        setupObservers()

        setupTelegramHomeSearch()
        setupTelegramBottomNavigation()
        setupTelegramStoriesPreview()

        childFragmentManager.commitTransaction {
            add(R.id.roomListContainer, HomeRoomListFragment::class.java, null, HOME_ROOM_LIST_FRAGMENT_TAG)
        }
        setupTelegramHomeTabs()

        viewModel.onEach(HomeDetailViewState::selectedSpace) { selectedSpace ->
            onSpaceChange(selectedSpace)
        }

        viewModel.observeViewEvents { viewEvent ->
            when (viewEvent) {
                HomeDetailViewEvents.CallStarted -> Unit
                is HomeDetailViewEvents.FailToCall -> Unit
                HomeDetailViewEvents.Loading -> showLoadingDialog()
            }
        }

        unknownDeviceDetectorSharedViewModel.onEach { state ->
            state.unknownSessions.invoke()?.let { unknownDevices ->
                val uid = PopupAlertManager.REVIEW_LOGIN_UID
                if (unknownDevices.firstOrNull()?.currentSessionTrust == true) {
                    alertManager.cancelAlert(uid)
                    val olderUnverified = unknownDevices.filter { !it.isNew }
                    val newest = unknownDevices.firstOrNull { it.isNew }?.deviceInfo
                    if (newest != null) {
                        promptForNewUnknownDevices(uid, state, newest)
                    } else if (olderUnverified.isNotEmpty()) {
                        // In this case we prompt to go to settings to review logins
                        promptToReviewChanges(uid, state, olderUnverified.map { it.deviceInfo })
                    }
                } else {
                    // cancel as there are not anymore untrusted devices
                    alertManager.cancelAlert(uid)
                }
            }
        }

        sharedCallActionViewModel
                .liveKnownCalls
                .observe(viewLifecycleOwner) {
                    currentCallsViewPresenter.updateCall(callManager.getCurrentCall(), callManager.getCalls())
                    invalidateOptionsMenu()
                }

        newHomeDetailViewModel.onEach { viewState ->
            spacesUnreadLabel = when (val badgeState = viewState.spacesNotificationCounterBadgeState) {
                is UnreadCounterBadgeView.State.Count -> badgeState.count
                        .takeIf { it > 0 }
                        ?.let { count -> if (count > 99) "99+" else count.toString() }
                is UnreadCounterBadgeView.State.Text -> badgeState.text.takeIf { it.isNotBlank() }
            }
            // Spaces is now in the toolbar menu; keep its unread signal there instead of
            // drawing a detached badge where the hidden floating action button used to be.
            invalidateOptionsMenu()
        }
    }

    private fun setupTelegramHomeSearch() {
        val input = views.telegramHomeSearchInput
        homeSearchTextWatcher?.let(input::removeTextChangedListener)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                homeRoomListViewModel.handle(HomeRoomListAction.SearchRooms(s?.toString().orEmpty()))
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        homeSearchTextWatcher = watcher
        input.addTextChangedListener(watcher)
    }

    /**
     * Keep the floating four-item navigation tied to real Element/Matrix destinations.
     * Contacts opens the device contacts book; profile opens the signed-in user's
     * Matrix profile. No placeholder tabs or fake screen content are introduced.
     */
    private fun setupTelegramBottomNavigation() {
        val tabs = listOf(
                views.telegramTabChats,
                views.telegramTabContacts,
                views.telegramTabSettings,
                views.telegramTabProfile
        )
        val homeChromeViews = listOf(
                views.telegramHomeSearchInput,
                views.telegramHomeTabsTrack,
                views.telegramStoriesStrip,
                views.telegramCameraButton,
                views.newLayoutCreateChatButton,
                views.newLayoutOpenSpacesButton
        )
        var savedHomeChromeVisibility: Map<View, Int>? = null

        fun selectTab(selected: View) {
            tabs.forEach { tab ->
                tab.isSelected = tab.id == selected.id
            }
        }

        fun setHomeChromeVisible(visible: Boolean) {
            if (visible) {
                savedHomeChromeVisibility?.forEach { (view, oldVisibility) ->
                    view.visibility = oldVisibility
                }
                savedHomeChromeVisibility = null
            } else {
                if (savedHomeChromeVisibility == null) {
                    savedHomeChromeVisibility = homeChromeViews.associateWith { it.visibility }
                }
                homeChromeViews.forEach { it.isVisible = false }
            }
        }

        fun showHomeTab() {
            setHomeChromeVisible(true)
            views.toolbar.title = getString(R.string.telegram_home_title)
            views.toolbar.navigationIcon = null
            views.toolbar.setNavigationOnClickListener(null)
            selectTab(views.telegramTabChats)
        }

        fun showSecondaryTab(titleRes: Int, selected: View) {
            setHomeChromeVisible(false)
            views.toolbar.title = when {
                selected.id == views.telegramTabSettings.id -> ""
                selected.id == views.telegramTabContacts.id -> getString(R.string.telegram_tab_contacts)
                else -> getString(titleRes)
            }
            if (selected.id == views.telegramTabContacts.id) {
                views.toolbar.setNavigationIcon(R.drawable.ic_back_24dp)
                views.toolbar.setNavigationContentDescription(R.string.telegram_folder_action_cancel)
                views.toolbar.setNavigationOnClickListener {
                    if (childFragmentManager.backStackEntryCount > 0) {
                        childFragmentManager.popBackStackImmediate()
                    } else {
                        showHomeTab()
                    }
                }
            } else {
                views.toolbar.navigationIcon = null
                views.toolbar.setNavigationOnClickListener(null)
            }
            selectTab(selected)
        }

        fun showStoriesTab() {
            setHomeChromeVisible(false)
            views.toolbar.title = getString(R.string.telegram_stories_title)
            views.toolbar.setNavigationIcon(R.drawable.ic_back_24dp)
            views.toolbar.setNavigationContentDescription(R.string.telegram_folder_action_cancel)
            views.toolbar.setNavigationOnClickListener {
                if (childFragmentManager.backStackEntryCount > 0) {
                    childFragmentManager.popBackStackImmediate()
                } else {
                    showHomeTab()
                }
            }
            selectTab(views.telegramTabChats)
        }

        fun openStoriesTab() {
            if (childFragmentManager.findFragmentById(R.id.roomListContainer) !is TelegramStoriesFragment) {
                if (childFragmentManager.backStackEntryCount > 0) {
                    childFragmentManager.popBackStackImmediate(
                            null,
                            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                }
                showStoriesTab()
                val initialStoryEventId = pendingStoryEventId
                val openStoryComposer = pendingStoryShouldCreate
                pendingStoryEventId = null
                pendingStoryShouldCreate = false
                childFragmentManager.commitTransaction {
                    replace(R.id.roomListContainer, TelegramStoriesFragment.newInstance(initialStoryEventId, openStoryComposer))
                    addToBackStack("telegram_stories")
                }
            } else {
                showStoriesTab()
            }
        }

        views.telegramStoriesStrip.setOnClickListener { openStoriesTab() }

        selectTab(views.telegramTabChats)
        withState(viewModel) { state ->
            state.myMatrixItem?.let { avatarRenderer.render(it, views.telegramTabProfileIcon) }
        }

        views.telegramTabChats.debouncedClicks {
            // Restore the Matrix room list if Settings or Contacts is currently displayed.
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStackImmediate(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
            }
            showHomeTab()
            views.telegramHomeSearchInput.text?.clear()
            viewModel.handle(HomeDetailAction.SwitchTab(HomeTab.RoomList(RoomListDisplayMode.PEOPLE)))
            homeRoomListViewModel.handle(HomeRoomListAction.ChangeRoomFilter(HomeRoomFilter.ALL))
        }

        views.telegramTabContacts.debouncedClicks {
            if (childFragmentManager.findFragmentById(R.id.roomListContainer) !is ContactsBookFragment) {
                if (childFragmentManager.backStackEntryCount > 0) {
                    childFragmentManager.popBackStackImmediate(
                            null,
                            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                }
                showSecondaryTab(R.string.telegram_tab_chat_folders, views.telegramTabContacts)
                childFragmentManager.commitTransaction {
                    replace(R.id.roomListContainer, ContactsBookFragment())
                    addToBackStack("telegram_chat_folders")
                }
            } else {
                showSecondaryTab(R.string.telegram_tab_chat_folders, views.telegramTabContacts)
            }
        }

        childFragmentManager.addOnBackStackChangedListener {
            if (!isAdded || view == null) return@addOnBackStackChangedListener
            when (childFragmentManager.findFragmentById(R.id.roomListContainer)) {
                is ContactsBookFragment -> {
                    showSecondaryTab(R.string.telegram_tab_chat_folders, views.telegramTabContacts)
                }
                is TelegramSettingsFragment -> {
                    showSecondaryTab(R.string.telegram_tab_settings, views.telegramTabSettings)
                }
                is TelegramStoriesFragment -> showStoriesTab()
                else -> {
                    showHomeTab()
                    setupTelegramStoriesPreview()
                }
            }
        }

        views.telegramTabSettings.debouncedClicks {
            if (childFragmentManager.findFragmentById(R.id.roomListContainer) !is TelegramSettingsFragment) {
                if (childFragmentManager.backStackEntryCount > 0) {
                    childFragmentManager.popBackStackImmediate(
                            null,
                            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                }
                showSecondaryTab(R.string.telegram_tab_settings, views.telegramTabSettings)
                childFragmentManager.commitTransaction {
                    replace(R.id.roomListContainer, TelegramSettingsFragment())
                    addToBackStack("telegram_settings")
                }
            } else {
                showSecondaryTab(R.string.telegram_tab_settings, views.telegramTabSettings)
            }
        }

        views.telegramTabProfile.debouncedClicks {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStackImmediate(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
            }
            setHomeChromeVisible(true)
            selectTab(views.telegramTabProfile)
            withState(viewModel) { state ->
                val ownUserId = state.myMatrixItem?.id
                if (ownUserId.isNullOrBlank()) {
                    navigator.openSettings(requireContext())
                } else {
                    navigator.openRoomMemberProfile(ownUserId, null, requireContext())
                }
            }
        }
    }

    private fun setupTelegramStoriesPreview() {
        val container = views.telegramHomeStoriesPeople
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt().coerceAtLeast(1)
        val itemSize = dp(70)
        val itemWidth = dp(80)

        fun addStoryCard(label: String, story: TelegramStoriesRepository.Story?, isAdd: Boolean) {
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(itemWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener {
                    pendingStoryEventId = story?.event?.eventId
                    pendingStoryShouldCreate = isAdd
                    views.telegramStoriesStrip.performClick()
                }
            }

            val ringColors = when {
                isAdd -> intArrayOf(0xFF229ED9.toInt(), 0xFF55BDEB.toInt())
                story?.isSeen == false -> intArrayOf(0xFFFFB44A.toInt(), 0xFF229ED9.toInt())
                else -> intArrayOf(0xFF9AA3AD.toInt(), 0xFFBCC3CB.toInt())
            }
            val frame = android.widget.FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(itemSize, itemSize)
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, ringColors).apply {
                    shape = GradientDrawable.OVAL
                }
                setPadding(dp(3), dp(3), dp(3), dp(3))
            }

            val image = ImageView(requireContext()).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF3C5367.toInt())
                }
            }

            if (isAdd) {
                withState(viewModel) { state ->
                    state.myMatrixItem?.let { avatarRenderer.render(it, image) }
                }
                if (image.drawable == null) {
                    image.setImageResource(R.drawable.ic_user_round)
                }
            } else if (story != null) {
                val data = telegramStoriesRepository.buildThumbnailData(story)
                if (data != null) {
                    imageContentRenderer.render(data, image, itemSize - dp(6))
                } else {
                    image.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
            frame.addView(image)

            if (isAdd) {
                val plusBadge = TextView(requireContext()).apply {
                    text = "+"
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(android.graphics.Color.WHITE)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0xFF229ED9.toInt())
                        setStroke(dp(2), android.graphics.Color.WHITE)
                    }
                    elevation = dp(2).toFloat()
                    contentDescription = getString(R.string.telegram_stories_add_photo)
                }
                frame.addView(plusBadge, android.widget.FrameLayout.LayoutParams(
                        dp(23),
                        dp(23),
                        Gravity.BOTTOM or Gravity.END
                ))
            }

            card.addView(frame)
            val title = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(5)
                }
                text = label
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 11.5f
                setTextColor(
                        if (isAdd) colorProvider.getColorFromAttribute(com.google.android.material.R.attr.colorPrimary)
                        else colorProvider.getColorFromAttribute(im.vector.lib.ui.styles.R.attr.vctr_content_primary)
                )
            }
            card.addView(title)
            container.addView(card)
        }

        container.removeAllViews()
        addStoryCard(getString(R.string.telegram_stories_yours), null, true)

        lifecycleScope.launch {
            // Only dedicated encrypted Matrix story rooms are joined; ordinary rooms and DMs
            // are never touched. Joining these invites is necessary to read the story feed.
            val recentStories = runCatching {
                telegramStoriesRepository.loadStories(joinInvitations = true)
            }.getOrDefault(emptyList())
            if (!isAdded || view == null) return@launch

            container.removeAllViews()
            addStoryCard(getString(R.string.telegram_stories_yours), null, true)
            var ownUserId: String? = null
            withState(viewModel) { ownUserId = it.myMatrixItem?.id }
            recentStories.groupBy { it.ownerUserId }
                    .mapNotNull { (_, ownerStories) -> ownerStories.maxByOrNull { it.createdAt } }
                    .filter { it.ownerUserId != ownUserId }
                    .sortedByDescending { it.createdAt }
                    .forEach { story ->
                        val ownerName = story.ownerUserId.substringBefore(':').removePrefix("@").take(10)
                                .ifBlank { getString(R.string.telegram_stories_title) }
                        addStoryCard(ownerName, story, false)
                    }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupTelegramHomeTabs() {
        val tabs = views.telegramHomeTabs

        fun renderTabs() {
            val folders = chatFoldersStore.visibleFolders()
            isSyncingTelegramHomeTabs = true
            try {
                tabs.removeAllTabs()
                tabs.isVisible = folders.isNotEmpty()
                folders.forEach { folder ->
                    tabs.addTab(
                            tabs.newTab()
                                    .setText(folder.name)
                                    .setTag(folder),
                            false
                    )
                }
                val selectedId = chatFoldersStore.selectedFolderId()
                val selectedIndex = folders.indexOfFirst {
                    it.id == selectedId && it.filter == activeHomeRoomFilter
                }.takeIf { it >= 0 }
                        ?: folders.indexOfFirst { it.filter == activeHomeRoomFilter }.takeIf { it >= 0 }
                        ?: folders.indexOfFirst { it.filter == HomeRoomFilter.ALL }
                if (selectedIndex >= 0) {
                    tabs.getTabAt(selectedIndex)?.select()
                }
            } finally {
                isSyncingTelegramHomeTabs = false
            }
        }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (isSyncingTelegramHomeTabs) return
                val folder = tab.tag as? ChatFolderDefinition ?: return
                chatFoldersStore.setSelectedFolder(folder.id)
                activeHomeRoomFilter = folder.filter
                homeRoomListViewModel.handle(HomeRoomListAction.ChangeRoomFilter(folder.filter))
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        chatFoldersStore.foldersFlow
                .onEach {
                    val visible = chatFoldersStore.visibleFolders()
                    val selected = visible.firstOrNull { it.id == chatFoldersStore.selectedFolderId() }
                            ?: visible.firstOrNull { it.filter == HomeRoomFilter.ALL }
                    if (selected != null && selected.filter != activeHomeRoomFilter) {
                        chatFoldersStore.setSelectedFolder(selected.id)
                        activeHomeRoomFilter = selected.filter
                        homeRoomListViewModel.handle(HomeRoomListAction.ChangeRoomFilter(selected.filter))
                    }
                    renderTabs()
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)

        chatFoldersStore.separateChatsFlow
                .onEach {
                    val visible = chatFoldersStore.visibleFolders()
                    if (visible.none { it.filter == activeHomeRoomFilter }) {
                        val fallback = visible.firstOrNull { it.filter == HomeRoomFilter.ALL }
                                ?: visible.firstOrNull()
                        fallback?.let {
                            chatFoldersStore.setSelectedFolder(it.id)
                            activeHomeRoomFilter = it.filter
                            homeRoomListViewModel.handle(HomeRoomListAction.ChangeRoomFilter(it.filter))
                        }
                    }
                    renderTabs()
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)

        homeRoomListViewModel.onEach(HomeRoomListViewState::headersData) { headers ->
            activeHomeRoomFilter = headers.currentFilter
            val folders = chatFoldersStore.visibleFolders()
            val selectedFolder = folders.firstOrNull {
                it.id == chatFoldersStore.selectedFolderId() && it.filter == activeHomeRoomFilter
            } ?: folders.firstOrNull { it.filter == activeHomeRoomFilter }
            if (selectedFolder != null && selectedFolder.id != chatFoldersStore.selectedFolderId()) {
                chatFoldersStore.setSelectedFolder(selectedFolder.id)
            } else if (selectedFolder == null) {
                val fallback = folders.firstOrNull { it.filter == HomeRoomFilter.ALL }
                        ?: folders.firstOrNull()
                fallback?.let {
                    chatFoldersStore.setSelectedFolder(it.id)
                    if (it.filter != activeHomeRoomFilter) {
                        homeRoomListViewModel.handle(HomeRoomListAction.ChangeRoomFilter(it.filter))
                    }
                }
            }
            renderTabs()
        }
    }

    private fun setupObservers() {
        sharedRoomListActionViewModel = activityViewModelProvider[RoomListSharedActionViewModel::class.java]

        sharedRoomListActionViewModel
                .stream()
                .onEach(::handleSharedAction)
                .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun handleSharedAction(action: RoomListSharedAction) {
        when (action) {
            RoomListSharedAction.CloseBottomSheet -> spaceListBottomSheet.dismiss()
        }
    }

    private fun setupFabs() {
        showFABs()

        views.newLayoutCreateChatButton.debouncedClicks {
            newChatBottomSheet.takeIf { !it.isAdded }?.show(requireActivity().supportFragmentManager, NewChatBottomSheet.TAG)
        }

        views.telegramCameraButton.debouncedClicks {
            // The chat-list camera opens the story camera, not the external system camera.
            pendingStoryShouldCreate = true
            views.telegramStoriesStrip.performClick()
        }

    }


    private fun showFABs() {
        // One primary compose action, as in Telegram. Spaces stays available in the toolbar menu.
        views.newLayoutCreateChatButton.show()
    }

    private fun setCurrentSpace(spaceId: String?) {
        spaceStateHandler.setCurrentSpace(spaceId, isForwardNavigation = false)
        sharedActionViewModel.post(HomeActivitySharedAction.OnCloseSpace)
    }

    override fun onDestroyView() {
        homeSearchTextWatcher?.let { watcher -> views.telegramHomeSearchInput.removeTextChangedListener(watcher) }
        homeSearchTextWatcher = null
        currentCallsViewPresenter.unBind()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        callManager.checkForProtocolsSupportIfNeeded()
        refreshSpaceState()
    }

    private fun refreshSpaceState() {
        spaceStateHandler.getCurrentSpace()?.let {
            onSpaceChange(it)
        }
    }

    private fun promptForNewUnknownDevices(uid: String, state: UnknownDevicesState, newest: DeviceInfo) {
        val user = state.myMatrixItem
        alertManager.postVectorAlert(
                VerificationVectorAlert(
                        uid = uid,
                        title = getString(CommonStrings.new_session),
                        description = getString(CommonStrings.verify_this_session, newest.displayName ?: newest.deviceId ?: ""),
                        iconId = R.drawable.ic_shield_warning
                ).apply {
                    viewBinder = VerificationVectorAlert.ViewBinder(user, avatarRenderer)
                    colorInt = colorProvider.getColorFromAttribute(com.google.android.material.R.attr.colorPrimary)
                    contentAction = Runnable {
                        (weakCurrentActivity?.get() as? VectorBaseActivity<*>)?.let { vectorBaseActivity ->
                            vectorBaseActivity.navigator
                                    .requestSessionVerification(vectorBaseActivity, newest.deviceId ?: "")
                        }
                        unknownDeviceDetectorSharedViewModel.handle(
                                UnknownDeviceDetectorSharedViewModel.Action.IgnoreNewLogin(newest.deviceId?.let { listOf(it) }.orEmpty())
                        )
                    }
                    dismissedAction = Runnable {
                        unknownDeviceDetectorSharedViewModel.handle(
                                UnknownDeviceDetectorSharedViewModel.Action.IgnoreNewLogin(newest.deviceId?.let { listOf(it) }.orEmpty())
                        )
                    }
                }
        )
    }

    private fun promptToReviewChanges(uid: String, state: UnknownDevicesState, oldUnverified: List<DeviceInfo>) {
        val user = state.myMatrixItem
        alertManager.postVectorAlert(
                VerificationVectorAlert(
                        uid = uid,
                        title = getString(CommonStrings.review_unverified_sessions_title),
                        description = getString(CommonStrings.review_unverified_sessions_description),
                        iconId = R.drawable.ic_shield_warning,
                        shouldBeDisplayedIn = { activity ->
                            // do not show when there is an ongoing verification flow
                            if (activity is VectorBaseActivity<*>) {
                                activity.supportFragmentManager.findFragmentByTag(SelfVerificationBottomSheet.TAG) == null &&
                                        activity !is QrCodeScannerActivity
                            } else true
                        }
                ).apply {
                    viewBinder = VerificationVectorAlert.ViewBinder(user, avatarRenderer)
                    colorInt = colorProvider.getColorFromAttribute(com.google.android.material.R.attr.colorPrimary)
                    contentAction = Runnable {
                        (weakCurrentActivity?.get() as? VectorBaseActivity<*>)?.let { activity ->
                            // mark as ignored to avoid showing it again
                            unknownDeviceDetectorSharedViewModel.handle(
                                    UnknownDeviceDetectorSharedViewModel.Action.IgnoreDevice(oldUnverified.mapNotNull { it.deviceId })
                            )
                            activity.navigator.openSettings(activity, EXTRA_DIRECT_ACCESS_SECURITY_PRIVACY_MANAGE_SESSIONS)
                        }
                    }
                    dismissedAction = Runnable {
                        unknownDeviceDetectorSharedViewModel.handle(
                                UnknownDeviceDetectorSharedViewModel.Action.IgnoreDevice(oldUnverified.mapNotNull { it.deviceId })
                        )
                    }
                }
        )
    }

    private fun onSpaceChange(spaceSummary: RoomSummary?) {
        val title = spaceSummary?.displayName ?: getString(R.string.telegram_home_title)
        views.toolbar.title = title
    }

    /**
     * Security and backup prompts use the same transient alert surface as "New Login",
     * rather than reserving space in the chat list for a permanent banner.
     */
    private fun setupKeysBackupAlerts() {
        serverBackupStatusViewModel.handle(ServerBackupStatusAction.OnBannerDisplayed)
        serverBackupStatusViewModel.onEach { viewState ->
            when (val bannerState = viewState.bannerState.invoke()) {
                is BannerState.Setup -> {
                    if (bannerState.numberOfKeys > 0 && !bannerState.doNotShowAgain) {
                        showKeysBackupAlert()
                    } else {
                        isKeysBackupAlertPosted = false
                        alertManager.cancelAlert(KEYS_BACKUP_ALERT_UID)
                    }
                }
                BannerState.BackingUp,
                BannerState.Hidden -> {
                    isKeysBackupAlertPosted = false
                    alertManager.cancelAlert(KEYS_BACKUP_ALERT_UID)
                }
                else -> Unit
            }
        }
    }

    private fun showKeysBackupAlert() {
        if (isKeysBackupAlertPosted) return
        isKeysBackupAlertPosted = true
        val alert = DefaultVectorAlert(
                uid = KEYS_BACKUP_ALERT_UID,
                title = getString(CommonStrings.secure_backup_banner_setup_line1),
                description = getString(CommonStrings.secure_backup_banner_setup_line2),
                iconId = R.drawable.ic_shield_warning,
        ).apply {
            colorInt = colorProvider.getColorFromAttribute(com.google.android.material.R.attr.colorPrimary)
            addButton(getString(CommonStrings.action_enable), Runnable {
                navigator.openKeysBackupSetup(requireActivity(), false)
            })
            dismissedAction = Runnable {
                serverBackupStatusViewModel.handle(ServerBackupStatusAction.OnBannerClosed)
            }
        }
        alertManager.postVectorAlert(alert)
    }

    private fun setupActiveCallView() {
        currentCallsViewPresenter.bind(views.currentCallsView, this)
    }

    private fun setupToolbar() {
        setupToolbar(views.toolbar)
        views.toolbar.title = getString(R.string.telegram_home_title)

        // Telegram's chat list uses a plain title and a trailing overflow menu.
        // Drawer access remains available as an item in that menu.
        views.toolbar.navigationIcon = null
        views.toolbar.setNavigationOnClickListener(null)
    }

    private fun openSpaceSettings() = withState(viewModel) { viewState ->
        viewState.selectedSpace?.let {
            sharedActionViewModel.post(HomeActivitySharedAction.ShowSpaceSettings(it.roomId))
        }
    }

    private fun setupDebugButton() {
        // Developer shortcuts must not leak into the user-facing Telegram-style header.
        views.debugButton.isGone = true
    }

    private fun updateVerifyDeviceAlert(showPrompt: Boolean, matrixItem: org.matrix.android.sdk.api.util.MatrixItem?) {
        if (!showPrompt) {
            isVerifyDeviceAlertPosted = false
            alertManager.cancelAlert(VERIFY_DEVICE_ALERT_UID)
            return
        }
        if (isVerifyDeviceAlertPosted || matrixItem == null) return

        isVerifyDeviceAlertPosted = true
        val alert = VerificationVectorAlert(
                uid = VERIFY_DEVICE_ALERT_UID,
                title = getString(CommonStrings.crosssigning_verify_this_session),
                description = getString(CommonStrings.verify_this_session_content),
                iconId = R.drawable.ic_shield_warning,
        ).apply {
            viewBinder = VerificationVectorAlert.ViewBinder(matrixItem, avatarRenderer)
            colorInt = colorProvider.getColorFromAttribute(com.google.android.material.R.attr.colorPrimary)
            addButton(getString(CommonStrings.verify_now), Runnable {
                (weakCurrentActivity?.get() as? VectorBaseActivity<*>)?.let { activity ->
                    activity.navigator.requestSelfSessionVerification(activity)
                }
            })
        }
        alertManager.postVectorAlert(alert)
    }

    override fun invalidate() = withState(viewModel) {
        views.syncStateView.render(
                it.syncState,
                it.incrementalSyncRequestState,
                it.pushCounter,
                vectorPreferences.developerShowDebugInfo()
        )
        updateVerifyDeviceAlert(it.showVerifyDeviceBanner, it.myMatrixItem)
        hasUnreadRooms = it.hasUnreadMessages
    }

    override fun onTapToReturnToCall() {
        callManager.getCurrentCall()?.let { call ->
            VectorCallActivity.newIntent(
                    context = requireContext(),
                    callId = call.callId,
                    signalingRoomId = call.signalingRoomId,
                    otherUserId = call.mxCall.opponentUserId,
                    isIncomingCall = !call.mxCall.isOutgoing,
                    isVideoCall = call.mxCall.isVideoCall,
                    mode = null
            ).let {
                startActivity(it)
            }
        }
    }

    override fun onBackPressed(toolbarButton: Boolean) = if (spaceStateHandler.isRoot()) {
        false
    } else {
        val lastSpace = spaceStateHandler.popSpaceBackstack()
        spaceStateHandler.setCurrentSpace(lastSpace, isForwardNavigation = false)
        true
    }

    private fun SpaceStateHandler.isRoot() = getSpaceBackstack().isEmpty()

    companion object {
        private const val HOME_ROOM_LIST_FRAGMENT_TAG = "TAG_HOME_ROOM_LIST"
        private const val VERIFY_DEVICE_ALERT_UID = "messenger_verify_device"
        private const val KEYS_BACKUP_ALERT_UID = "messenger_keys_backup"
    }
}
